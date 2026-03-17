package com.app.misproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
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
    }

    fun loadBookings() {
        viewModelScope.launch {
            try {
                println("🔥 Attempting to load bookings...")
                val snapshot = database.child("bookings").get().await()
                println("🔥 Snapshot exists: ${snapshot.exists()}")
                println("🔥 Children count: ${snapshot.childrenCount}")

                val bookingsList = mutableListOf<Booking>()

                for (childSnapshot in snapshot.children) {
                    println("🔥 Processing child: ${childSnapshot.key}")
                    val booking = childSnapshot.getValue(Booking::class.java)
                    if (booking != null) {
                        bookingsList.add(booking)
                        println("🔥 Added booking: ${booking.bookingId}")
                    } else {
                        println("🔥 Failed to parse booking from: ${childSnapshot.value}")
                    }
                }

                _allBookings.value = bookingsList
                println("🔥 Total bookings loaded: ${bookingsList.size}")

            } catch (e: Exception) {
                println("🔥 ERROR: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // ADD THIS FUNCTION
    fun forceRefresh() {
        viewModelScope.launch {
            loadBookings()
        }
    }

    fun updateBookingStatus(bookingId: String, status: BookingStatus) {
        viewModelScope.launch {
            try {
                database.child("bookings").child(bookingId).child("status").setValue(status).await()
                println("✅ Booking $bookingId updated to $status")
                loadBookings() // Refresh after update
            } catch (e: Exception) {
                println("❌ Failed to update booking: ${e.message}")
            }
        }
    }
}