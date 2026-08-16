package com.app.checkot.viewmodel

import android.app.Application
import android.util.Log
import com.app.checkot.model.*
import com.app.checkot.service.NotificationHelper
import com.app.checkot.service.FCMSender
import com.app.checkot.service.BookingLedgerService
import com.app.checkot.utils.BookingUtils
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
    private val TAG = "BookingViewModel"
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

    /** True once the first userBookings snapshot (success or error) has arrived. */
    private val _userBookingsLoaded = MutableStateFlow(false)
    val userBookingsLoaded: StateFlow<Boolean> = _userBookingsLoaded

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
                _userBookingsLoaded.value = false
                setupRealTimeBookingsListener()
            } else {
                _userBookings.value = emptyList()
                _userBookingsLoaded.value = true
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
                    Log.d(TAG, "Real-time listener cancelled: ${error.message}")
                    _userBookingsLoaded.value = true
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
                _userBookingsLoaded.value = true
                Log.d(TAG, "Bookings updated in real-time: ${bookings.size} bookings")
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
                    Log.e(TAG, "❌ Cannot create booking — user has an active booking already")
                    return@launch
                }

                // Re-validate availability at creation time — closes the race
                // where the owner removes a service or closes the shop after
                // the client loaded the screen. The shop must still exist and
                // be active, not closed on the booking date, every selected
                // service must still be offered and available on that date, and
                // the slot must fit the day's effective hours (per-day override
                // wins — e.g. the owner closed early today).
                val shopSnap = firestore.collection("shop_services").document(booking.shopId)
                    .get(com.google.firebase.firestore.Source.SERVER).await()
                val shop = shopSnap.toObject(ShopCustomization::class.java)
                val bookingDay = BookingUtils.startOfDay(booking.bookingDate)
                val unavailableService = booking.services.any { svc ->
                    if (svc == ServiceType.CUSTOM) {
                        booking.customServiceNames.any { name ->
                            val config = shop?.services?.find { it.isCustom && it.customName == name }
                            config == null || config.unavailableDates.contains(bookingDay)
                        }
                    } else {
                        val config = shop?.services?.find { it.serviceName == svc.name }
                        config == null || config.unavailableDates.contains(bookingDay)
                    }
                }
                val (effOpen, effClose) = BookingUtils.effectiveHours(
                    shop?.openMinutes ?: 540,
                    shop?.closeMinutes ?: 960,
                    shop?.dayOverrides.orEmpty(),
                    booking.bookingDate
                )
                val (h, m) = BookingUtils.parseTimeSlotToHourMinute(booking.timeSlot)
                val slotStart = h * 60 + m
                val slotOutsideHours = slotStart < effOpen || slotStart > effClose
                if (shop == null || shop.status != "active" ||
                    shop.closedDates.contains(bookingDay) || unavailableService || slotOutsideHours
                ) {
                    _isLoading.value = false
                    _error.value = "One or more services you selected are no longer available for this date, or the shop's hours changed. Please review your selection and try again."
                    Log.e(TAG, "❌ Cannot create booking — availability re-check failed")
                    return@launch
                }

                // Server-side slot availability check + creation, atomically —
                // avoids the race where two concurrent bookings both pass a
                // separate check before either writes (see BookingLedgerService).
                val normalizedDate = normalizeToStartOfDay(booking.bookingDate)
                val bookingDoc = firestore.collection("bookings").document()
                val newBooking = booking.copy(
                    bookingId = bookingDoc.id,
                    userId = user.uid,
                    bookingDate = normalizedDate,
                    createdAt = System.currentTimeMillis(),
                    status = BookingStatus.PENDING
                )
                val startMin = BookingUtils.parseTimeSlotToMinutesSince9AM(booking.timeSlot)
                val endMin = startMin + BookingUtils.bookingDurationMinutes(newBooking)

                try {
                    BookingLedgerService.reserveAndCreateBooking(firestore, bookingDoc, newBooking, startMin, endMin)
                } catch (e: BookingLedgerService.NoFreeBayException) {
                    _isLoading.value = false
                    _error.value = "This time slot is no longer available. All bays are occupied. Please select another time."
                    Log.e(TAG, "❌ Cannot create booking — no free bay for ${booking.timeSlot}")
                    return@launch
                } catch (e: Exception) {
                    _isLoading.value = false
                    _error.value = "Could not create booking. Please check your connection and try again."
                    Log.e(TAG, "❌ Booking reservation failed: ${e.message}")
                    return@launch
                }
                Log.d(TAG, "✅ Booking created: ${newBooking.bookingId}")

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
                            Log.d(TAG, "📬 Notifying owner for shop ${newBooking.shopId}...")
                            FCMSender.sendToUser(
                                context = appContext,
                                userId = "",
                                title = "New Booking Received!",
                                body = "New booking: $serviceSummary — ${newBooking.carDetails}",
                                bookingId = newBooking.bookingId,
                                fcmToken = ownerToken
                            )
                        } else {
                            Log.w(TAG, "⚠️ No owner FCM token in shop_services/${newBooking.shopId}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Owner notification failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create booking: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Client adds a paid add-on to their OWN Confirmed/In-Progress booking:
     * appends [addOnLabel] to addOns and bumps price by [addOnPrice]. Price only —
     * the reserved bay window/duration is never changed, so the day_slots ledger
     * stays valid and the extra can't collide with the next booking. The live
     * bookings listener refreshes the UI automatically.
     */
    fun addBookingAddOn(bookingId: String, addOnLabel: String, addOnPrice: Double) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("bookings").document(bookingId).get().await()
                val booking = snapshot.toObject(Booking::class.java)
                val uid = auth.currentUser?.uid
                if (booking == null || booking.userId != uid) {
                    Log.e(TAG, "❌ Add-on on a booking that isn't yours. Blocked.")
                    return@launch
                }
                if (booking.status != BookingStatus.CONFIRMED && booking.status != BookingStatus.IN_PROGRESS) {
                    Log.e(TAG, "❌ Add-ons can only be added while Confirmed or In Progress. Blocked.")
                    return@launch
                }
                if (addOnPrice <= 0.0 || addOnLabel.isBlank()) return@launch
                firestore.collection("bookings").document(bookingId).update(
                    mapOf(
                        "price" to (booking.price + addOnPrice),
                        "addOns" to (booking.addOns + addOnLabel)
                    )
                ).await()
                Log.d(TAG, "✅ Add-on '$addOnLabel' (+₱$addOnPrice) added to $bookingId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to add add-on: ${e.message}")
            }
        }
    }


    /**
     * Cancels a booking. When [skipCooldown] is true (e.g. the shop closed the
     * date / changed hours, so the booking can't be fulfilled), the user is
     * NOT stamped with lastCancelledAt — a free cancel, no rebooking cooldown.
     */
    fun cancelBooking(bookingId: String, skipCooldown: Boolean = false) {
        viewModelScope.launch {
            try {
                val bookingSnapshot = firestore.collection("bookings").document(bookingId).get().await()
                val booking = bookingSnapshot.toObject(Booking::class.java)

                firestore.collection("bookings").document(bookingId).update(
                    "status", BookingStatus.CANCELLED,
                    "cancelledAt", System.currentTimeMillis()
                ).await()
                if (booking != null) {
                    BookingLedgerService.release(firestore, booking.shopId, booking.bookingDate, bookingId)
                }
                // Store cancellation timestamp in Firestore (survives app restart) —
                // skipped for a free (impacted) cancellation.
                val uid = auth.currentUser?.uid ?: ""
                if (uid.isNotEmpty() && !skipCooldown) {
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
                            Log.d(TAG, "📬 Sending cancellation notification to owner (token: ${ownerToken.take(8)}...)")
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
                        Log.e(TAG, "❌ Failed to notify owner of cancellation: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel booking: ${e.message}")
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

    fun fetchAvailableTimeSlots(
        date: Long,
        shopId: String,
        durationMinutes: Int = 60,
        openMinutes: Int = 540,  // 9:00 AM — shop's opening time
        closeMinutes: Int = 960  // 4:00 PM — shop's last bookable slot
    ) {
        // Build raw slots from the shop's configured working hours.
        val rawSlots = BookingUtils.generateSlotLabels(openMinutes, closeMinutes)
            .map { TimeSlot(it, true) }

        // Convert "09:00 AM" → minutes since 9:00
        fun slotToMinutes(slot: String): Int = BookingUtils.parseTimeSlotToMinutesSince9AM(slot)

        // Filter out slots too close to current time (30 min min advance)
        val cal = java.util.Calendar.getInstance()
        val curTotalMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val selCal = java.util.Calendar.getInstance().apply { timeInMillis = date }
        val isToday = cal.get(java.util.Calendar.YEAR) == selCal.get(java.util.Calendar.YEAR) &&
                      cal.get(java.util.Calendar.DAY_OF_YEAR) == selCal.get(java.util.Calendar.DAY_OF_YEAR)
        val MIN_ADVANCE = 30 // minutes
        val initialSlots = rawSlots.map { slot ->
            if (isToday) {
                val sm = slotToMinutes(slot.slot)
                val slotHour = (sm / 60) + 9
                val slotMin = sm % 60
                val slotTotalMin = slotHour * 60 + slotMin
                if (slotTotalMin - curTotalMin < MIN_ADVANCE) slot.copy(available = false) else slot
            } else slot
        }
        _availableTimeSlots.value = initialSlots

        viewModelScope.launch {
            try {
                if (shopId.isEmpty()) return@launch
                Log.d(TAG, "📅 fetchAvailableTimeSlots: shop=$shopId duration=${durationMinutes}min")

                // Load bay count from shop settings
                val shopDoc = firestore.collection("shop_services").document(shopId).get().await()
                val bayCount = (shopDoc.getLong("bayCount")?.toInt() ?: 1).coerceAtLeast(1)
                Log.d(TAG, "📅 Bay count: $bayCount")

                // Use raw slots (past-time filter already applied in initialSlots)
                val allSlots = rawSlots.toMutableList()

                // Get existing active bookings
                val snapshot = firestore.collection("bookings")
                    .whereEqualTo("bookingDate", date)
                    .whereEqualTo("shopId", shopId)
                    .get().await()

                val existing = snapshot.documents.mapNotNull { it.toObject(Booking::class.java) }
                    .filter { it.status != BookingStatus.CANCELLED && it.status != BookingStatus.COMPLETED }
                Log.d(TAG, "📅 Existing bookings: ${existing.size}")

                // Build busy ranges per bay
                val busyRanges = BookingUtils.computeBusyRanges(existing, bayCount)

                // Check each slot (curH/curM/isToday/slotToMinutes from outer scope)
                val updated = allSlots.map { slot ->
                    var avail = true
                    val sm = slotToMinutes(slot.slot)

                    if (isToday) {
                        val slotHour = (sm / 60) + 9
                        val slotMin = sm % 60
                        val slotTotalMin = slotHour * 60 + slotMin
                        if (slotTotalMin - curTotalMin < 30) avail = false
                    }

                    if (avail) {
                        val em = sm + durationMinutes
                        if (!BookingUtils.hasFreeBay(busyRanges, sm, em)) avail = false
                    }

                    slot.copy(available = avail)
                }

                val availCount = updated.count { it.available }
                Log.d(TAG, "📅 Slots updated: ${updated.size} total, $availCount available")
                _availableTimeSlots.value = updated
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to fetch time slots: ${e.message}")
                // Keep default slots on error
            }
        }
    }

    /** Normalize a timestamp to the start of the day (midnight) so same-day bookings are grouped together */
    private fun normalizeToStartOfDay(timestamp: Long): Long = BookingUtils.startOfDay(timestamp)

    override fun onCleared() {
        super.onCleared()
        bookingsListenerRegistration?.remove()
        authStateListener?.let { auth.removeAuthStateListener(it) }
    }
}
