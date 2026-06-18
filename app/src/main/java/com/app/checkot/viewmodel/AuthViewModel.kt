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
import com.google.firebase.firestore.ktx.firestore
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

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val appContext = application.applicationContext

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        if (auth.currentUser != null) {
            _authState.value = AuthState.Authenticated
            loadUserData()
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
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign in failed")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState.Unauthenticated
        _currentUserData.value = null
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

    fun loadUserData() {
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

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
}
