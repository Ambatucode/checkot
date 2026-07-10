package com.app.checkot.viewmodel

import android.app.Application
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
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val appContext = application.applicationContext
    
    private val _allBookings = MutableStateFlow<List<Booking>>(emptyList())
    val allBookings: StateFlow<List<Booking>> = _allBookings
    
    private val _allUsers = MutableStateFlow<List<CarWashUser>>(emptyList())
    val allUsers: StateFlow<List<CarWashUser>> = _allUsers
    
    private val _currentOwnerShopId = MutableStateFlow<String?>(null)

    private val _shopCustomization = MutableStateFlow(ShopCustomization())
    val shopCustomization: StateFlow<ShopCustomization> = _shopCustomization

    // Track known booking IDs so we only notify on truly new ones
    private var knownBookingIds = mutableSetOf<String>()
    private var isInitialLoad = true

    private var bookingsListenerRegistration: ListenerRegistration? = null
    private var authStateListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null

    init {
        println("🔥 AdminViewModel initialized")
        // Auth listener handles both initial load and re-login to a different user
        authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                // Clear old state first
                _allBookings.value = emptyList()
                _allUsers.value = emptyList()
                bookingsListenerRegistration?.remove()
                bookingsListenerRegistration = null
                servicesListenerRegistration?.remove()
                servicesListenerRegistration = null
                loadOwnerContext()
            } else {
                // User logged out — clear state
                _allBookings.value = emptyList()
                _allUsers.value = emptyList()
                _currentOwnerShopId.value = null
                _shopCustomization.value = ShopCustomization()
                bookingsListenerRegistration?.remove()
                bookingsListenerRegistration = null
                servicesListenerRegistration?.remove()
                servicesListenerRegistration = null
            }
        }
        Firebase.auth.addAuthStateListener(authStateListener!!)
    }
    
    private fun loadOwnerContext() {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser ?: return@launch
            try {
                val snapshot = firestore.collection("users").document(user.uid).get().await()
                val userData = snapshot.toObject(CarWashUser::class.java)
                if (userData?.role == "owner") {
                    val shopId = userData.ownedShopId
                    _currentOwnerShopId.value = shopId
                    // Upload FCM token to both users and shop_services so clients can send notifications
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token ->
                            // Save to users/{uid} (for other uses)
                            firestore.collection("users").document(user.uid)
                                .update("fcmToken", token)
                            // Save to shop_services/{shopId} (for client-to-owner notifications)
                            if (shopId != null) {
                                firestore.collection("shop_services").document(shopId)
                                    .set(mapOf("ownerFcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                                    .addOnSuccessListener { println("✅ FCM token saved to shop_services/$shopId") }
                                    .addOnFailureListener { e -> println("❌ Failed to save FCM token to shop_services: ${e.message}") }
                            }
                        }
                    if (shopId != null) {
                        // Explicit direct read FIRST, then set up listener for updates
                        try {
                            val doc = firestore.collection("shop_services").document(shopId)
                                .get(com.google.firebase.firestore.Source.SERVER).await()
                            val customization = doc.toObject(ShopCustomization::class.java)
                            _shopCustomization.value = customization ?: ShopCustomization()
                            println("📋 Initial shop services loaded: ${_shopCustomization.value.services.size} services")
                        } catch (e: Exception) {
                            println("❌ Failed initial services load: ${e.message}")
                            _shopCustomization.value = ShopCustomization()
                        }
                        setupRealTimeServicesListener(shopId)
                    } else {
                        _shopCustomization.value = ShopCustomization()
                    }
                    setupRealTimeBookingsListener()
                } else {
                    println("❌ Current user is not an owner.")
                }
            } catch (e: Exception) {
                println("❌ Failed to load owner context: ${e.message}")
            }
        }
    }

    private var servicesListenerRegistration: ListenerRegistration? = null

    private fun setupRealTimeServicesListener(shopId: String) {
        servicesListenerRegistration?.remove()
        servicesListenerRegistration = firestore.collection("shop_services").document(shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("🔥 ERROR on services listener: ${error.message}")
                    return@addSnapshotListener
                }
                val customization = snapshot?.toObject(ShopCustomization::class.java)
                _shopCustomization.value = customization ?: ShopCustomization()
                println("📋 Shop services updated in real-time: ${_shopCustomization.value.services.size} services")
            }
    }

    private fun setupRealTimeBookingsListener() {
        val shopId = _currentOwnerShopId.value ?: return
        bookingsListenerRegistration?.remove()

        bookingsListenerRegistration = firestore.collection("bookings")
            .whereEqualTo("shopId", shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("🔥 ERROR on bookings listener: ${error.message}")
                    return@addSnapshotListener
                }

                val bookingsList = snapshot?.documents?.mapNotNull { it.toObject(Booking::class.java) }
                    ?: emptyList()
                
                _allBookings.value = bookingsList
                println("🔥 Bookings updated in real-time: ${bookingsList.size}")

                // Update known IDs
                if (!isInitialLoad) {
                    // We removed the local NotificationHelper call here because
                    // FCMSender now handles sending push notifications for new bookings.
                    // This prevents duplicate notifications.
                }

                // Update known IDs
                knownBookingIds = bookingsList.map { it.bookingId }.toMutableSet()
                isInitialLoad = false

                // Load users who have bookings
                viewModelScope.launch {
                    loadUsers(bookingsList.map { it.userId }.distinct())
                }
            }
    }

    fun loadBookings() {
        setupRealTimeBookingsListener()
        // Also auto-cancel stale pending bookings
        autoCancelStaleBookings()
    }

    /** Cancel PENDING bookings older than 2 hours */
    private fun autoCancelStaleBookings() {
        viewModelScope.launch {
            try {
                val shopId = _currentOwnerShopId.value ?: return@launch
                val cutoff = System.currentTimeMillis() - 2 * 60 * 60 * 1000L // 2 hours
                val snapshot = firestore.collection("bookings")
                    .whereEqualTo("shopId", shopId)
                    .whereEqualTo("status", "PENDING")
                    .get().await()
                for (doc in snapshot.documents) {
                    val createdAt = doc.getLong("createdAt") ?: continue
                    if (createdAt < cutoff) {
                        val bookingId = doc.id
                        firestore.collection("bookings").document(bookingId)
                            .update("status", "CANCELLED", "cancelledAt", System.currentTimeMillis())
                            .await()
                        val bookingDate = doc.getLong("bookingDate")
                        if (bookingDate != null) {
                            BookingLedgerService.release(firestore, shopId, bookingDate, bookingId)
                        }
                        // Notify customer
                        val userId = doc.getString("userId") ?: ""
                        FCMSender.sendToUser(
                            context = appContext,
                            userId = userId,
                            title = "Booking Cancelled",
                            body = "Your booking was cancelled because it wasn't approved in time.",
                            bookingId = bookingId
                        )
                        println("📬 Auto-cancelled stale booking $bookingId")
                    }
                }
                // Also auto-cancel CONFIRMED bookings past their slot + 2 hours
                val confirmedSnapshot = firestore.collection("bookings")
                    .whereEqualTo("shopId", shopId)
                    .whereEqualTo("status", "CONFIRMED")
                    .get().await()
                for (doc in confirmedSnapshot.documents) {
                    val timeSlot = doc.getString("timeSlot") ?: continue
                    val bookingDate = doc.getLong("bookingDate") ?: continue
                    val confirmedAt = doc.getLong("confirmedAt") ?: 0L
                    // Skip if confirmed less than 30 min ago (prevents immediate cancel after approval)
                    if (confirmedAt > 0 && System.currentTimeMillis() - confirmedAt < 30 * 60 * 1000L) continue
                    try {
                        val (h, m) = BookingUtils.parseTimeSlotToHourMinute(timeSlot)
                        val cal = java.util.Calendar.getInstance().apply {
                            timeInMillis = bookingDate
                            set(java.util.Calendar.HOUR_OF_DAY, h)
                            set(java.util.Calendar.MINUTE, m)
                            add(java.util.Calendar.MINUTE, 30) // grace period
                            add(java.util.Calendar.HOUR_OF_DAY, 2) // 2 extra hours
                        }
                        if (cal.timeInMillis < System.currentTimeMillis()) {
                            val bookingId = doc.id
                            firestore.collection("bookings").document(bookingId)
                                .update("status", "CANCELLED", "cancelledAt", System.currentTimeMillis())
                                .await()
                            BookingLedgerService.release(firestore, shopId, bookingDate, bookingId)
                            val userId = doc.getString("userId") ?: ""
                            FCMSender.sendToUser(
                                context = appContext,
                                userId = userId,
                                title = "Booking Cancelled",
                                body = "Your confirmed booking was cancelled because the service wasn't started in time.",
                                bookingId = bookingId
                            )
                            println("📬 Auto-cancelled stale confirmed booking $bookingId")
                        }
                    } catch (e: Exception) {
                        println("⚠️ Failed to parse slot for $timeSlot: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("❌ Auto-cancel error: ${e.message}")
            }
        }
    }

    /** Mark a confirmed booking as no-show (past their time slot) */
    fun markNoShow(bookingId: String) {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("bookings").document(bookingId).get().await()
                val booking = doc.toObject(Booking::class.java)
                if (booking == null || booking.shopId != _currentOwnerShopId.value) return@launch
                if (booking.status != BookingStatus.CONFIRMED) return@launch

                firestore.collection("bookings").document(bookingId)
                    .update("status", "CANCELLED", "cancelledAt", System.currentTimeMillis())
                    .await()
                BookingLedgerService.release(firestore, booking.shopId, booking.bookingDate, bookingId)

                val services = booking.services.joinToString(", ") { it.displayName }
                FCMSender.sendToUser(
                    context = appContext,
                    userId = booking.userId,
                    title = "Booking Cancelled — No Show",
                    body = "Your booking for $services was marked as no-show. Please book again when you're ready.",
                    bookingId = bookingId
                )
                println("✅ Marked booking $bookingId as no-show")
                loadBookings()
            } catch (e: Exception) {
                println("❌ No-show error: ${e.message}")
            }
        }
    }

    private suspend fun loadUsers(userIds: List<String>) {
        if (userIds.isEmpty()) {
            _allUsers.value = emptyList()
            return
        }
        try {
            println("🔥 Attempting to load users...")
            val snapshot = firestore.collection("users").get().await()
            
            val usersList = snapshot.documents.mapNotNull { it.toObject(CarWashUser::class.java) }
                .filter { userIds.contains(it.userId) }
                
            _allUsers.value = usersList
            println("🔥 Total users loaded: ${usersList.size}")
        } catch (e: Exception) {
            println("🔥 ERROR loading users: ${e.message}")
        }
    }

    fun forceRefresh() {
        loadBookings()
    }

    fun updateBookingStatus(bookingId: String, status: BookingStatus) {
        viewModelScope.launch {
            try {
                // 1. Get booking details before updating
                val bookingDoc = firestore.collection("bookings").document(bookingId).get().await()
                val booking = bookingDoc.toObject(Booking::class.java)

                // SECURITY: Verify the booking belongs to the owner's own shop.
                // This prevents a rogue owner from modifying another shop's bookings.
                val ownerShopId = _currentOwnerShopId.value
                if (booking == null || booking.shopId != ownerShopId) {
                    println("❌ Security: Attempted to update a booking not belonging to this shop. Blocked.")
                    return@launch
                }

                // SECURITY: Only allow status to move forward, never back to PENDING.
                // This prevents an owner from resetting a COMPLETED booking to PENDING.
                val allowedTransitions = mapOf(
                    BookingStatus.PENDING    to setOf(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
                    BookingStatus.CONFIRMED  to setOf(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED),
                    BookingStatus.IN_PROGRESS to setOf(BookingStatus.COMPLETED)
                )
                val currentStatus = booking.status
                if (status !in (allowedTransitions[currentStatus] ?: emptySet())) {
                    println("❌ Security: Invalid status transition $currentStatus → $status. Blocked.")
                    return@launch
                }

                // 2. Update status and timestamp
                val updates = mutableMapOf<String, Any>("status" to status.name)
                when (status) {
                    BookingStatus.CONFIRMED -> updates["confirmedAt"] = System.currentTimeMillis()
                    BookingStatus.IN_PROGRESS -> updates["inProgressAt"] = System.currentTimeMillis()
                    BookingStatus.COMPLETED -> updates["completedAt"] = System.currentTimeMillis()
                    BookingStatus.CANCELLED -> updates["cancelledAt"] = System.currentTimeMillis()
                    else -> {}
                }

                firestore.collection("bookings").document(bookingId)
                    .update(updates).await()
                println("✅ Booking $bookingId updated to $status")

                if (status == BookingStatus.CANCELLED) {
                    BookingLedgerService.release(firestore, booking.shopId, booking.bookingDate, bookingId)
                }

                // 3. Send FCM push notification to the CUSTOMER
                val services = booking.services.joinToString(", ") { it.displayName }
                val (title, body) = when (status) {
                    BookingStatus.CONFIRMED -> Pair(
                        "Booking Confirmed!",
                        "Your booking for $services has been confirmed."
                    )
                    BookingStatus.IN_PROGRESS -> Pair(
                        "Service In Progress",
                        "Your $services is now being worked on!"
                    )
                    BookingStatus.COMPLETED -> Pair(
                        "Service Completed!",
                        "Your $services is done. Your car is ready!"
                    )
                    BookingStatus.CANCELLED -> Pair(
                        "Booking Cancelled",
                        "Your booking for $services has been cancelled."
                    )
                    else -> Pair("Booking Update", "Status: $status")
                }
                FCMSender.sendToUser(
                    context = appContext,
                    userId = booking.userId,
                    title = title,
                    body = body,
                    bookingId = bookingId
                )

                loadBookings()
            } catch (e: Exception) {
                println("❌ Failed to update booking: ${e.message}")
            }
        }
    }

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult

    fun saveShopCustomization(customization: ShopCustomization) {
        viewModelScope.launch {
            _saveResult.value = null
            try {
                val shopId = _currentOwnerShopId.value
                if (shopId == null) {
                    _saveResult.value = "Error: No shop ID found"
                    return@launch
                }
                // Get the owner's current FCM token and attach it to the customization
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                val customizationWithToken = customization.copy(ownerFcmToken = token)
                println("💾 Saving ${customizationWithToken.services.size} services to shop_services/$shopId (token: ${token.take(8)}...)")
                // Save to shop_services collection
                firestore.collection("shop_services").document(shopId)
                    .set(customizationWithToken).await()
                // Verify by reading back immediately
                val verify = firestore.collection("shop_services").document(shopId)
                    .get(Source.SERVER).await()
                val saved = verify.toObject(ShopCustomization::class.java)
                val savedCount = saved?.services?.size ?: 0
                _shopCustomization.value = customization
                _saveResult.value = "✅ Saved: $savedCount services"
                println("✅ Save verified: $savedCount services in shop_services/$shopId")
            } catch (e: Exception) {
                println("❌ Failed to save customization: ${e.message}")
                _saveResult.value = "❌ Error: ${e.message}"
            }
        }
    }

    fun saveLogoBase64(base64: String, mimeType: String) {
        val updated = _shopCustomization.value.copy(logoBase64 = base64, logoMimeType = mimeType)
        saveShopCustomization(updated)
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                bookingsListenerRegistration?.remove()
                Firebase.auth.signOut()
                println("✅ User logged out")
                onComplete()
            } catch (e: Exception) {
                println("❌ Logout failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bookingsListenerRegistration?.remove()
        servicesListenerRegistration?.remove()
        authStateListener?.let { Firebase.auth.removeAuthStateListener(it) }
    }
}
