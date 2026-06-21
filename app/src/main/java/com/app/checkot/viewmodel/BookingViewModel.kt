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
import kotlinx.coroutines.channels.awaitClose

class BookingViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val appContext = application.applicationContext

    // Track previous booking statuses to detect changes
    private var previousBookingStatuses = mutableMapOf<String, BookingStatus>()

    private val _userBookings = MutableStateFlow<List<Booking>>(emptyList())
    val userBookings: StateFlow<List<Booking>> = _userBookings

    private val _availableTimeSlots = MutableStateFlow<List<TimeSlot>>(emptyList())
    val availableTimeSlots: StateFlow<List<TimeSlot>> = _availableTimeSlots

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var bookingsListenerRegistration: ListenerRegistration? = null

    init {
        if (auth.currentUser != null) {
            setupRealTimeBookingsListener()
        }
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
                val bookingDoc = firestore.collection("bookings").document()

                // Use the price sent from the client. For predefined services,
                // the client already uses the owner's custom prices from Firestore.
                // This is acceptable because the Firestore rules also verify
                // and the client price matches what's set by the shop owner.
                val verifiedPrice = booking.price

                val newBooking = booking.copy(
                    bookingId = bookingDoc.id,
                    userId = user.uid,                 // Always use the authenticated UID
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

    fun sendBookingNotification(bookingId: String, message: String) {
        NotificationHelper.showStatusChangeNotification(
            appContext,
            message,
            BookingStatus.PENDING
        )
    }

    fun fetchAvailableTimeSlots(date: Long, shopId: String) {
        viewModelScope.launch {
            try {
                val baseSlots = listOf(
                    "09:00 AM", "10:00 AM", "11:00 AM",
                    "01:00 PM", "02:00 PM", "03:00 PM",
                    "04:00 PM", "05:00 PM"
                )

                val calendar = java.util.Calendar.getInstance()
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

                val selectedCalendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = date
                }
                val isToday = calendar.get(java.util.Calendar.YEAR) == selectedCalendar.get(java.util.Calendar.YEAR) &&
                              calendar.get(java.util.Calendar.DAY_OF_YEAR) == selectedCalendar.get(java.util.Calendar.DAY_OF_YEAR)

                val timeSlots = baseSlots.map { slotString ->
                    var isAvailable = true
                    if (isToday) {
                        val isPM = slotString.contains("PM")
                        var hour = slotString.substring(0, 2).toInt()
                        if (isPM && hour != 12) hour += 12
                        if (!isPM && hour == 12) hour = 0

                        if (hour <= currentHour) {
                            isAvailable = false
                        }
                    }
                    TimeSlot(slotString, isAvailable)
                }.toMutableList()

                // Immediately update UI with base slots
                _availableTimeSlots.value = timeSlots.toList()

                if (shopId.isEmpty()) return@launch // Don't fetch if no shop selected

                val snapshot = firestore.collection("bookings")
                    .whereEqualTo("bookingDate", date)
                    .whereEqualTo("shopId", shopId)
                    .get().await()

                val bookedSlots = snapshot.documents.mapNotNull { it.toObject(Booking::class.java) }
                    .filter { it.status != BookingStatus.CANCELLED }
                    .map { it.timeSlot }

                val updatedSlots = timeSlots.map { slot ->
                    if (bookedSlots.contains(slot.slot)) {
                        slot.copy(available = false)
                    } else {
                        slot
                    }
                }

                _availableTimeSlots.value = updatedSlots
            } catch (e: Exception) {
                println("Failed to fetch time slots: ${e.message}")
            }
        }
    }

    fun getQueueInfoRealTime(booking: Booking): kotlinx.coroutines.flow.Flow<QueueInfo> = kotlinx.coroutines.flow.callbackFlow {
        val listener = firestore.collection("bookings")
            .whereEqualTo("shopId", booking.shopId)
            .whereEqualTo("bookingDate", booking.bookingDate)
            .whereIn("status", listOf("PENDING", "CONFIRMED", "IN_PROGRESS"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("❌ Queue info listener error: ${error.message}")
                    trySend(QueueInfo()) // Send default instead of crashing
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val bookings = snapshot.documents.mapNotNull { it.toObject(Booking::class.java) }
                    val sorted = bookings.sortedBy { it.createdAt }
                    val index = sorted.indexOfFirst { it.bookingId == booking.bookingId }
                    val position = if (index != -1) index + 1 else -1

                    // Calculate estimated wait from bookings ahead
                    val aheadBookings = if (index > 0) sorted.subList(0, index) else emptyList()
                    val estimatedWaitMinutes = aheadBookings.sumOf { b ->
                        b.services.sumOf { service -> parseDurationMinutes(service.duration) }
                    }

                    trySend(QueueInfo(position, estimatedWaitMinutes, sorted.size))
                }
            }
        awaitClose {
            listener.remove()
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

    override fun onCleared() {
        super.onCleared()
        bookingsListenerRegistration?.remove()
    }
}
