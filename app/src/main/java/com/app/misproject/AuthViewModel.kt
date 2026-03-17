package com.app.misproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val database: DatabaseReference = FirebaseDatabase
        .getInstance("https://misproject-df034-default-rtdb.asia-southeast1.firebasedatabase.app")
        .reference

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState

    private val _currentUserData = MutableStateFlow<CarWashUser?>(null)
    val currentUserData: StateFlow<CarWashUser?> = _currentUserData

    private val _userBookings = MutableStateFlow<List<Booking>>(emptyList())
    val userBookings: StateFlow<List<Booking>> = _userBookings

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _ownerAccessCode = MutableStateFlow("")
    val ownerAccessCode: StateFlow<String> = _ownerAccessCode

    fun fetchOwnerAccessCode() {
        viewModelScope.launch {
            try {
                val snapshot = database.child("owner_access").child("code").get().await()
                val code = snapshot.getValue(String::class.java) ?: ""
                _ownerAccessCode.value = code
                println("✅ Owner access code fetched: $code")
            } catch (e: Exception) {
                println("❌ Failed to fetch owner access code: ${e.message}")
            }
        }
    }

    suspend fun verifyOwnerAccessCode(enteredCode: String): Boolean {
        return try {
            val snapshot = database.child("owner_access").child("code").get().await()
            val value = snapshot.value // This could be String or Long

            val correctCode = when (value) {
                is String -> value
                is Long -> value.toString()
                is Int -> value.toString()
                else -> ""
            }

            println("🔍 Correct code from Firebase: $correctCode")
            enteredCode == correctCode
        } catch (e: Exception) {
            println("❌ Failed to verify access code: ${e.message}")
            false
        }
    }
    private var bookingsListener: ValueEventListener? = null

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
                        savedCars = emptyList()
                    )

                    database.child("users").child(user.uid).setValue(userData).await()
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
        bookingsListener?.let {
            database.child("bookings").removeEventListener(it)
        }
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
                val snapshot = database.child("users").child(user.uid).get().await()
                _currentUserData.value = snapshot.getValue(CarWashUser::class.java)
            } catch (e: Exception) {
                println("Failed to load user data: ${e.message}")
            }
        }
    }

    fun setupRealTimeBookingsListener() {
        val user = auth.currentUser ?: return

        bookingsListener?.let {
            database.child("bookings").removeEventListener(it)
        }

        bookingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val bookings = snapshot.children
                    .mapNotNull { it.getValue(Booking::class.java) }
                    .filter { it.userId == user.uid }
                    .sortedByDescending { it.createdAt }

                _userBookings.value = bookings
                println("Bookings updated in real-time: ${bookings.size} bookings")
            }

            override fun onCancelled(error: DatabaseError) {
                println("Real-time listener cancelled: ${error.message}")
            }
        }

        database.child("bookings")
            .orderByChild("userId")
            .equalTo(user.uid)
            .addValueEventListener(bookingsListener!!)
    }

    fun addCar(car: Car) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = auth.currentUser ?: return@launch
            try {
                val currentData = _currentUserData.value
                val currentCars = currentData?.savedCars?.toMutableList() ?: mutableListOf()

                val carId = database.child("users").child(user.uid).child("cars").push().key ?: return@launch
                val newCar = car.copy(carId = carId)

                currentCars.add(newCar)

                val updates = mapOf(
                    "savedCars" to currentCars
                )
                database.child("users").child(user.uid).updateChildren(updates).await()

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

                val updates = mapOf(
                    "savedCars" to currentCars
                )
                database.child("users").child(user.uid).updateChildren(updates).await()

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
                database.child("users").child(user.uid).updateChildren(updates).await()
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
                val bookingId = database.child("bookings").push().key ?: return@launch
                val newBooking = booking.copy(
                    bookingId = bookingId,
                    userId = user.uid,
                    createdAt = System.currentTimeMillis(),
                    status = BookingStatus.PENDING
                )

                database.child("bookings").child(bookingId).setValue(newBooking).await()
                sendBookingNotification(bookingId, "New booking created")
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
                database.child("bookings").child(bookingId).child("status")
                    .setValue(BookingStatus.CANCELLED).await()
                sendBookingNotification(bookingId, "Booking cancelled")
            } catch (e: Exception) {
                println("Failed to cancel booking: ${e.message}")
            }
        }
    }

    fun sendBookingNotification(bookingId: String, message: String) {
        // This will be implemented when we add the owner side
        println("Notification for booking $bookingId: $message")
    }

    fun getAvailableTimeSlots(date: Long): List<TimeSlot> {
        return listOf(
            TimeSlot("09:00 AM", true),
            TimeSlot("10:00 AM", true),
            TimeSlot("11:00 AM", true),
            TimeSlot("01:00 PM", true),
            TimeSlot("02:00 PM", true),
            TimeSlot("03:00 PM", true),
            TimeSlot("04:00 PM", true),
            TimeSlot("05:00 PM", true)
        )
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun getQueuePosition(): Int {
        val pending = _userBookings.value.filter { it.status == BookingStatus.PENDING }.size
        val confirmed = _userBookings.value.filter { it.status == BookingStatus.CONFIRMED }.size
        val yourBookings = _userBookings.value.filter {
            it.status == BookingStatus.PENDING || it.status == BookingStatus.CONFIRMED
        }.size
        return yourBookings
    }

    override fun onCleared() {
        super.onCleared()
        bookingsListener?.let {
            database.child("bookings").removeEventListener(it)
        }
    }
}