package com.app.checkot.viewmodel

import android.app.Application
import com.app.checkot.model.*
import com.app.checkot.service.NotificationHelper
import com.app.checkot.service.FCMSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
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

sealed class AuthState {
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val appContext = application.applicationContext

    // Track previous booking statuses to detect changes
    private var previousBookingStatuses = mutableMapOf<String, BookingStatus>()

    // Upload the FCM token to Firestore for shop owners
    private fun uploadFcmToken(userId: String) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                firestore.collection("users").document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        println("✅ FCM token saved to Firestore")
                    }
                    .addOnFailureListener { e ->
                        println("Failed to upload FCM token: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                println("Failed to get FCM token: ${e.message}")
            }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState

    private val _currentUserData = MutableStateFlow<CarWashUser?>(null)
    val currentUserData: StateFlow<CarWashUser?> = _currentUserData

    private val _userBookings = MutableStateFlow<List<Booking>>(emptyList())
    val userBookings: StateFlow<List<Booking>> = _userBookings

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _availableTimeSlots = MutableStateFlow<List<TimeSlot>>(emptyList())
    val availableTimeSlots: StateFlow<List<TimeSlot>> = _availableTimeSlots

    private var bookingsListenerRegistration: ListenerRegistration? = null

    init {
        if (auth.currentUser != null) {
            _authState.value = AuthState.Authenticated
            loadUserData()
            setupRealTimeBookingsListener()
        }
    }

    fun signUp(email: String, password: String, fullName: String, phoneNumber: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    val userData = CarWashUser(
                        userId = user.uid,
                        fullName = fullName,
                        email = email,
                        phoneNumber = phoneNumber,
                        createdAt = System.currentTimeMillis(),
                        role = "customer", // Default role
                        savedCars = emptyList()
                    )
                    firestore.collection("users").document(user.uid).set(userData).await()
                    _currentUserData.value = userData
                    _authState.value = AuthState.Authenticated
                    setupRealTimeBookingsListener()
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign up failed")
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                loadUserData()
                setupRealTimeBookingsListener()
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign in failed")
            }
        }
    }

    fun signOut() {
        bookingsListenerRegistration?.remove()
        auth.signOut()
        _authState.value = AuthState.Unauthenticated
        _currentUserData.value = null
        _userBookings.value = emptyList()
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                println("Password reset email sent to $email")
            } catch (e: Exception) {
                println("Failed to send password reset email: ${e.message}")
            }
        }
    }

    private fun loadUserData() {
        viewModelScope.launch {
            val user = auth.currentUser ?: return@launch
            try {
                val snapshot = firestore.collection("users").document(user.uid).get().await()
                val userData = snapshot.toObject(CarWashUser::class.java)
                _currentUserData.value = userData
                // Upload FCM token for ALL users (customers AND owners)
                // so the Cloud Function can send push notifications to anyone
                if (userData != null) {
                    uploadFcmToken(userData.userId)
                }
            } catch (e: Exception) {
                println("Failed to load user data: ${e.message}")
            }
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

    fun addCar(car: Car) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = auth.currentUser ?: return@launch
            try {
                val currentData = _currentUserData.value
                val currentCars = currentData?.savedCars?.toMutableList() ?: mutableListOf()
                
                val carId = firestore.collection("users").document().id
                val newCar = car.copy(carId = carId)
                currentCars.add(newCar)
                
                firestore.collection("users").document(user.uid).update("savedCars", currentCars).await()
                _currentUserData.value = currentData?.copy(savedCars = currentCars)
            } catch (e: Exception) {
                println("Failed to add car: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteCar(carId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = auth.currentUser ?: return@launch
            try {
                val currentData = _currentUserData.value
                val currentCars = currentData?.savedCars?.toMutableList() ?: return@launch
                currentCars.removeAll { it.carId == carId }
                
                firestore.collection("users").document(user.uid).update("savedCars", currentCars).await()
                _currentUserData.value = currentData.copy(savedCars = currentCars)
            } catch (e: Exception) {
                println("Failed to delete car: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUserProfile(updates: Map<String, Any>) {
        viewModelScope.launch {
            val user = auth.currentUser ?: return@launch
            try {
                // SECURITY: Strip out privileged fields so a user cannot
                // promote themselves to 'owner' or overwrite their userId.
                val safeUpdates = updates.filterKeys { key ->
                    key !in setOf("role", "userId", "ownedShopId")
                }
                if (safeUpdates.isEmpty()) return@launch
                firestore.collection("users").document(user.uid).update(safeUpdates).await()
                loadUserData()
            } catch (e: Exception) {
                println("Failed to update profile: ${e.message}")
            }
        }
    }

    fun createBooking(booking: Booking) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = auth.currentUser ?: return@launch
            try {
                val bookingDoc = firestore.collection("bookings").document()

                // SECURITY: Recalculate price from the trusted ServiceType enum.
                // Never trust the price value sent from the frontend — a manipulated
                // client could send price = 0. We look up the real price here instead.
                val verifiedPrice = booking.services.sumOf { serviceType ->
                    ServiceType.values()
                        .find { it.name == serviceType.name }
                        ?.price ?: 0.0
                }

                val newBooking = booking.copy(
                    bookingId = bookingDoc.id,
                    userId = user.uid,                 // Always use the authenticated UID
                    price = verifiedPrice,              // Use server-verified price
                    createdAt = System.currentTimeMillis(),
                    status = BookingStatus.PENDING      // Always start as PENDING
                )
                bookingDoc.set(newBooking).await()

                // Track the new booking's status
                previousBookingStatuses[bookingDoc.id] = BookingStatus.PENDING

                // Show confirmation notification
                val serviceSummary = newBooking.services.joinToString(", ") { it.displayName }
                NotificationHelper.showBookingCreatedNotification(appContext, serviceSummary)

                // Notify the owner via FCM so it works when the app is swiped away
                val ownersSnapshot = firestore.collection("users")
                    .whereEqualTo("ownedShopId", newBooking.shopId)
                    .get().await()
                
                for (doc in ownersSnapshot.documents) {
                    FCMSender.sendToUser(
                        context = appContext,
                        userId = doc.id,
                        title = "New Booking Received! 📋",
                        body = "New booking: $serviceSummary — ${newBooking.carDetails}",
                        bookingId = newBooking.bookingId
                    )
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

                firestore.collection("bookings").document(bookingId).update("status", BookingStatus.CANCELLED).await()
                sendBookingNotification(bookingId, "Booking cancelled")

                // Notify the owner via FCM
                if (booking != null) {
                    val serviceSummary = booking.services.joinToString(", ") { it.displayName }
                    val ownersSnapshot = firestore.collection("users")
                        .whereEqualTo("ownedShopId", booking.shopId)
                        .get().await()
                    
                    for (doc in ownersSnapshot.documents) {
                        FCMSender.sendToUser(
                            context = appContext,
                            userId = doc.id,
                            title = "Booking Cancelled ❌",
                            body = "Booking for $serviceSummary has been cancelled.",
                            bookingId = bookingId
                        )
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

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun getQueuePositionRealTime(booking: Booking): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.callbackFlow {
        val listener = firestore.collection("bookings")
            .whereEqualTo("shopId", booking.shopId)
            .whereEqualTo("bookingDate", booking.bookingDate)
            .whereIn("status", listOf("PENDING", "CONFIRMED", "IN_PROGRESS"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val bookings = snapshot.documents.mapNotNull { it.toObject(Booking::class.java) }
                    val sorted = bookings.sortedBy { it.createdAt }
                    val index = sorted.indexOfFirst { it.bookingId == booking.bookingId }
                    val position = if (index != -1) index + 1 else -1
                    trySend(position)
                }
            }
        awaitClose {
            listener.remove()
        }
    }

    override fun onCleared() {
        super.onCleared()
        bookingsListenerRegistration?.remove()
    }
}
