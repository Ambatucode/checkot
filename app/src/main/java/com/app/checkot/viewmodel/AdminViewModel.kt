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

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val appContext = application.applicationContext
    
    private val _allBookings = MutableStateFlow<List<Booking>>(emptyList())
    val allBookings: StateFlow<List<Booking>> = _allBookings
    
    private val _allUsers = MutableStateFlow<List<CarWashUser>>(emptyList())
    val allUsers: StateFlow<List<CarWashUser>> = _allUsers
    
    private val _currentOwnerShopId = MutableStateFlow<String?>(null)

    // Track known booking IDs so we only notify on truly new ones
    private var knownBookingIds = mutableSetOf<String>()
    private var isInitialLoad = true

    private var bookingsListenerRegistration: ListenerRegistration? = null

    init {
        println("🔥 AdminViewModel initialized")
        loadOwnerContext()
    }
    
    private fun loadOwnerContext() {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser ?: return@launch
            try {
                val snapshot = firestore.collection("users").document(user.uid).get().await()
                val userData = snapshot.toObject(CarWashUser::class.java)
                if (userData?.role == "owner") {
                    _currentOwnerShopId.value = userData.ownedShopId
                    setupRealTimeBookingsListener()
                } else {
                    println("❌ Current user is not an owner.")
                }
            } catch (e: Exception) {
                println("❌ Failed to load owner context: ${e.message}")
            }
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
        // Force refresh by re-setting up the listener
        setupRealTimeBookingsListener()
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
    }
}
