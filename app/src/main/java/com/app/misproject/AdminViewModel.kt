package com.app.misproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminViewModel : ViewModel() {
    private val database: DatabaseReference = FirebaseDatabase
        .getInstance("https://misproject-df034-default-rtdb.asia-southeast1.firebasedatabase.app")
        .reference

    private val _allBookings = MutableStateFlow<List<Booking>>(emptyList())
    val allBookings: StateFlow<List<Booking>> = _allBookings

    private val _allUsers = MutableStateFlow<List<CarWashUser>>(emptyList())
    val allUsers: StateFlow<List<CarWashUser>> = _allUsers

    init {
        println("🔥 AdminViewModel initialized")
        loadBookings()
        loadUsers()
    }

    fun loadBookings() {
        viewModelScope.launch {
            try {
                println("🔥 Attempting to load bookings...")
                val snapshot = database.child("bookings").get().await()
                val bookingsList = mutableListOf<Booking>()
                for (childSnapshot in snapshot.children) {
                    val booking = childSnapshot.getValue(Booking::class.java)
                    if (booking != null) bookingsList.add(booking)
                }
                _allBookings.value = bookingsList
                println("🔥 Total bookings loaded: ${bookingsList.size}")
            } catch (e: Exception) {
                println("🔥 ERROR loading bookings: ${e.message}")
            }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            try {
                println("🔥 Attempting to load users...")
                val snapshot = database.child("users").get().await()
                val usersList = mutableListOf<CarWashUser>()
                for (childSnapshot in snapshot.children) {
                    val user = childSnapshot.getValue(CarWashUser::class.java)
                    if (user != null) usersList.add(user)
                }
                _allUsers.value = usersList
                println("🔥 Total users loaded: ${usersList.size}")
            } catch (e: Exception) {
                println("🔥 ERROR loading users: ${e.message}")
            }
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            loadBookings()
            loadUsers()
        }
    }

    fun updateBookingStatus(bookingId: String, status: BookingStatus) {
        viewModelScope.launch {
            try {
                database.child("bookings").child(bookingId).child("status").setValue(status).await()
                println("✅ Booking $bookingId updated to $status")
                loadBookings()
            } catch (e: Exception) {
                println("❌ Failed to update booking: ${e.message}")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                Firebase.auth.signOut()
                println("✅ User logged out")
                onComplete()
            } catch (e: Exception) {
                println("❌ Logout failed: ${e.message}")
            }
        }
    }
}