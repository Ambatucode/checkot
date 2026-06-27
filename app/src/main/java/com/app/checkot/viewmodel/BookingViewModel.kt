package com.app.checkot.viewmodel

import android.app.Application
import com.app.checkot.model.*
import com.app.checkot.service.NotificationHelper
import com.app.checkot.service.FCMSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


class BookingViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val appContext = application.applicationContext

    // Track previous booking statuses to detect changes
    private var previousBookingStatuses = mutableMapOf<String, BookingStatus>()

    // Cooldown: 5-minute wait after cancel stored in Firestore (survives app restart)
    private val COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes

    private val _userBookings = MutableStateFlow<List<Booking>>(emptyList())
    val userBookings: StateFlow<List<Booking>> = _userBookings

    private val _availableTimeSlots = MutableStateFlow<List<TimeSlot>>(emptyList())
    val availableTimeSlots: StateFlow<List<TimeSlot>> = _availableTimeSlots

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var bookingsListenerRegistration: ListenerRegistration? = null
    private var authStateListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null

    init {
        authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            bookingsListenerRegistration?.remove()
            bookingsListenerRegistration = null
            previousBookingStatuses.clear()
            if (user != null) {
                _userBookings.value = emptyList()
                setupRealTimeBookingsListener()
            } else {
                _userBookings.value = emptyList()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    fun setupRealTimeBookingsListener() {
        val user = auth.currentUser ?: return
        bookingsListenerRegistration?.remove()

        bookingsListenerRegistration = firestore.collection("bookings")
            .whereEqualTo("userId", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("Real-time listener cancelled: ${error.message}")
                    return@addSnapshotListener
                }

                val bookings = snapshot?.documents?.mapNotNull { it.toObject(Booking::class.java) }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()

                // Detect status changes and update previous statuses
                for (booking in bookings) {
                    val previousStatus = previousBookingStatuses[booking.bookingId]
                    // We removed the local NotificationHelper call here because
                    // FCMSender now handles sending push notifications for status changes.
                    // This prevents duplicate notifications.
                    previousBookingStatuses[booking.bookingId] = booking.status
                }

                _userBookings.value = bookings
                println("Bookings updated in real-time: ${bookings.size} bookings")
            }
    }

    fun createBooking(booking: Booking) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = auth.currentUser ?: return@launch
            try {
                // Check cooldown (rapid booking after cancel) — stored in Firestore for persistence
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                val lastCancelled = userDoc.getLong("lastCancelledAt") ?: 0L
                if (lastCancelled > 0) {
                    val elapsed = System.currentTimeMillis() - lastCancelled
                    if (elapsed < COOLDOWN_MS) {
                        val endTime = lastCancelled + COOLDOWN_MS
                        _error.value = "cooldown:$endTime"
                        _isLoading.value = false
                        return@launch
                    }
                }

                // Check if user already has an active booking
                val activeSnapshot = firestore.collection("bookings")
                    .whereEqualTo("userId", user.uid)
                    .whereIn("status", listOf("PENDING", "CONFIRMED", "IN_PROGRESS"))
                    .get().await()
                if (activeSnapshot.documents.isNotEmpty()) {
                    _isLoading.value = false
                    _error.value = "You already have an active booking. Please cancel or wait for it to complete before booking again."
                    println("❌ Cannot create booking — user has an active booking already")
                    return@launch
                }

                val bookingDoc = firestore.collection("bookings").document()

                // Use the price sent from the client. For predefined services,
                // the client already uses the owner's custom prices from Firestore.
                // This is acceptable because the Firestore rules also verify
                // and the client price matches what's set by the shop owner.
                val verifiedPrice = booking.price

                val normalizedDate = normalizeToStartOfDay(booking.bookingDate)
                val newBooking = booking.copy(
                    bookingId = bookingDoc.id,
                    userId = user.uid,                 // Always use the authenticated UID
                    bookingDate = normalizedDate,       // Normalize to start of day so all same-day bookings match
                    price = verifiedPrice,              // Use server-verified price
                    createdAt = System.currentTimeMillis(),
                    status = BookingStatus.PENDING      // Always start as PENDING
                )
                bookingDoc.set(newBooking).await()
                println("✅ Booking created: ${newBooking.bookingId}")

                // Track the new booking's status
                previousBookingStatuses[bookingDoc.id] = BookingStatus.PENDING

                // Show confirmation notification
                val serviceSummary = newBooking.services.joinToString(", ") { it.displayName }
                NotificationHelper.showBookingCreatedNotification(appContext, serviceSummary)

                // Reset loading early so the UI updates immediately
                _isLoading.value = false

                // Notify the owner in the background (after UI updates)
                viewModelScope.launch {
                    try {
                        val shopDoc = firestore.collection("shop_services")
                            .document(newBooking.shopId)
                            .get().await()
                        val ownerToken = shopDoc.getString("ownerFcmToken")
                        if (!ownerToken.isNullOrEmpty()) {
                            println("📬 Notifying owner for shop ${newBooking.shopId}...")
                            FCMSender.sendToUser(
                                context = appContext,
                                userId = "",
                                title = "New Booking Received!",
                                body = "New booking: $serviceSummary — ${newBooking.carDetails}",
                                bookingId = newBooking.bookingId,
                                fcmToken = ownerToken
                            )
                        } else {
                            println("⚠️ No owner FCM token in shop_services/${newBooking.shopId}")
                        }
                    } catch (e: Exception) {
                        println("❌ Owner notification failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("Failed to create booking: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            try {
                val bookingSnapshot = firestore.collection("bookings").document(bookingId).get().await()
                val booking = bookingSnapshot.toObject(Booking::class.java)

                firestore.collection("bookings").document(bookingId).update(
                    "status", BookingStatus.CANCELLED,
                    "cancelledAt", System.currentTimeMillis()
                ).await()
                // Store cancellation timestamp in Firestore (survives app restart)
                val uid = auth.currentUser?.uid ?: ""
                if (uid.isNotEmpty()) {
                    firestore.collection("users").document(uid)
                        .update("lastCancelledAt", System.currentTimeMillis())
                        .await()
                }
                sendBookingNotification(bookingId, "Booking cancelled")

                // Notify the owner via FCM
                if (booking != null) {
                    val serviceSummary = booking.services.joinToString(", ") { it.displayName }
                    try {
                        val shopDoc = firestore.collection("shop_services")
                            .document(booking.shopId)
                            .get().await()
                        val ownerToken = shopDoc.getString("ownerFcmToken")
                        if (!ownerToken.isNullOrEmpty()) {
                            println("📬 Sending cancellation notification to owner (token: ${ownerToken.take(8)}...)")
                            FCMSender.sendToUser(
                                context = appContext,
                                userId = "",
                                title = "Booking Cancelled",
                                body = "Booking for $serviceSummary has been cancelled.",
                                bookingId = bookingId,
                                fcmToken = ownerToken
                            )
                        }
                    } catch (e: Exception) {
                        println("❌ Failed to notify owner of cancellation: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("Failed to cancel booking: ${e.message}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun sendBookingNotification(bookingId: String, message: String) {
        NotificationHelper.showStatusChangeNotification(
            appContext,
            message,
            BookingStatus.PENDING
        )
    }

    fun fetchAvailableTimeSlots(date: Long, shopId: String, durationMinutes: Int = 60) {
        // Build raw slots
        val rawSlots = listOf(
            TimeSlot("09:00 AM", true), TimeSlot("09:30 AM", true),
            TimeSlot("10:00 AM", true), TimeSlot("10:30 AM", true),
            TimeSlot("11:00 AM", true), TimeSlot("11:30 AM", true),
            TimeSlot("12:00 PM", true), TimeSlot("12:30 PM", true),
            TimeSlot("01:00 PM", true), TimeSlot("01:30 PM", true),
            TimeSlot("02:00 PM", true), TimeSlot("02:30 PM", true),
            TimeSlot("03:00 PM", true), TimeSlot("03:30 PM", true),
            TimeSlot("04:00 PM", true)
        )

        // Convert "09:00 AM" → minutes since 9:00
        fun slotToMinutes(slot: String): Int {
            val parts = slot.split(" ")
            val timeParts = parts[0].split(":")
            var h = timeParts[0].toInt()
            val m = timeParts[1].toInt()
            val isPM = parts[1] == "PM"
            if (isPM && h != 12) h += 12
            if (!isPM && h == 12) h = 0
            return (h - 9) * 60 + m
        }

        // Filter out past slots immediately (before any async work)
        val cal = java.util.Calendar.getInstance()
        val curH = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val curM = cal.get(java.util.Calendar.MINUTE)
        val selCal = java.util.Calendar.getInstance().apply { timeInMillis = date }
        val isToday = cal.get(java.util.Calendar.YEAR) == selCal.get(java.util.Calendar.YEAR) &&
                      cal.get(java.util.Calendar.DAY_OF_YEAR) == selCal.get(java.util.Calendar.DAY_OF_YEAR)
        val initialSlots = rawSlots.map { slot ->
            if (isToday) {
                val sm = slotToMinutes(slot.slot)
                val sh = (sm / 60) + 9
                val smin = sm % 60
                if (sh < curH || (sh == curH && smin < curM)) slot.copy(available = false) else slot
            } else slot
        }
        _availableTimeSlots.value = initialSlots

        viewModelScope.launch {
            try {
                if (shopId.isEmpty()) return@launch
                println("📅 fetchAvailableTimeSlots: shop=$shopId duration=${durationMinutes}min")

                // Load bay count from shop settings
                val shopDoc = firestore.collection("shop_services").document(shopId).get().await()
                val bayCount = (shopDoc.getLong("bayCount")?.toInt() ?: 1).coerceAtLeast(1)
                println("📅 Bay count: $bayCount")

                // Use raw slots (past-time filter already applied in initialSlots)
                val allSlots = rawSlots.toMutableList()

                // Get existing active bookings
                val snapshot = firestore.collection("bookings")
                    .whereEqualTo("bookingDate", date)
                    .whereEqualTo("shopId", shopId)
                    .get().await()

                val existing = snapshot.documents.mapNotNull { it.toObject(Booking::class.java) }
                    .filter { it.status != BookingStatus.CANCELLED }
                println("📅 Existing bookings: ${existing.size}")

                // Build busy ranges per bay
                val busyRanges = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
                for (i in 0 until bayCount) busyRanges[i] = mutableListOf()

                for (b in existing) {
                    val startMin = slotToMinutes(b.timeSlot)
                    val endMin = startMin + b.services.sumOf { s -> parseDurationMinutes(s.duration) }
                    for (bay in 0 until bayCount) {
                        val ranges = busyRanges[bay]!!
                        if (ranges.none { (s, e) -> startMin < e && endMin > s }) {
                            ranges.add(startMin to endMin)
                            break
                        }
                    }
                }

                // Check each slot (curH/curM/isToday/slotToMinutes from outer scope)
                val updated = allSlots.map { slot ->
                    var avail = true
                    val sm = slotToMinutes(slot.slot)

                    if (isToday) {
                        val sh = (sm / 60) + 9
                        val smin = sm % 60
                        if (sh < curH || (sh == curH && smin < curM)) avail = false
                    }

                    if (avail) {
                        val em = sm + durationMinutes
                        val fits = (0 until bayCount).any { bay ->
                            !busyRanges[bay]!!.any { (s, e) -> sm < e && em > s }
                        }
                        if (!fits) avail = false
                    }

                    slot.copy(available = avail)
                }

                val availCount = updated.count { it.available }
                println("📅 Slots updated: ${updated.size} total, $availCount available")
                _availableTimeSlots.value = updated
            } catch (e: Exception) {
                println("❌ Failed to fetch time slots: ${e.message}")
                // Keep default slots on error
            }
        }
    }

    private fun parseDurationMinutes(duration: String): Int {
        return when {
            duration.contains("hour") -> {
                val hours = duration.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0
                (hours * 60).toInt()
            }
            duration.contains("min") -> {
                duration.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 30
            }
            else -> 30
        }
    }

    /** Normalize a timestamp to the start of the day (midnight) so same-day bookings are grouped together */
    private fun normalizeToStartOfDay(timestamp: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    override fun onCleared() {
        super.onCleared()
        bookingsListenerRegistration?.remove()
        authStateListener?.let { auth.removeAuthStateListener(it) }
    }
}
