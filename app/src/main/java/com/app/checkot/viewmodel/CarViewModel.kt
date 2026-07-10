package com.app.checkot.viewmodel

import android.app.Application
import android.util.Log
import com.app.checkot.model.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CarViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "CarViewModel"
    private val auth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _savedCars = MutableStateFlow<List<Car>>(emptyList())
    val savedCars: StateFlow<List<Car>> = _savedCars

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                loadSavedCars(user.uid)
            } else {
                listenerRegistration?.remove()
                _savedCars.value = emptyList()
            }
        }
    }

    private fun loadSavedCars(uid: String) {
        listenerRegistration?.remove()
        listenerRegistration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to listen for saved cars: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val userData = snapshot.toObject(CarWashUser::class.java)
                    _savedCars.value = userData?.savedCars ?: emptyList()
                }
            }
    }

    fun addCar(car: Car) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = auth.currentUser ?: return@launch
            try {
                val currentCars = _savedCars.value.toMutableList()
                if (currentCars.size >= 5) {
                    Log.d(TAG, "Car limit reached. Cannot add more than 5 cars.")
                    return@launch
                }

                val carId = firestore.collection("users").document().id
                val newCar = car.copy(carId = carId)
                currentCars.add(newCar)

                firestore.collection("users").document(user.uid).update("savedCars", currentCars).await()
                _savedCars.value = currentCars
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add car: ${e.message}")
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
                val currentCars = _savedCars.value.toMutableList()
                currentCars.removeAll { it.carId == carId }

                firestore.collection("users").document(user.uid).update("savedCars", currentCars).await()
                _savedCars.value = currentCars
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete car: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
