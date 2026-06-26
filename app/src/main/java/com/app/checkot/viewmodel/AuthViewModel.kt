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
            // Upload FCM token directly — ensures token is always saved
            // even if loadUserData hasn't completed yet
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    firestore.collection("users").document(auth.currentUser!!.uid)
                        .update("fcmToken", token)
                        .addOnSuccessListener { println("✅ AuthVM: FCM token saved on init") }
                        .addOnFailureListener { e -> println("❌ AuthVM: Failed to save token: ${e.message}") }
                }
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
                    uploadFcmToken(user.uid)
                    _authState.value = AuthState.Authenticated
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign up failed")
            }
        }
    }

    fun signUpOwner(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String,
        shopName: String,
        shopAddress: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // 1. Create Firebase Auth user
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("Failed to create user")

                // 2. Generate shop ID
                val shopId = firestore.collection("shop_services").document().id

                // 3. Create the user document with role="owner"
                val userData = CarWashUser(
                    userId = user.uid,
                    fullName = fullName,
                    email = email,
                    phoneNumber = phoneNumber,
                    createdAt = System.currentTimeMillis(),
                    role = "owner",
                    ownedShopId = shopId,
                    savedCars = emptyList()
                )
                firestore.collection("users").document(user.uid).set(userData).await()

                // 4. Get FCM token
                val fcmToken = try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                } catch (e: Exception) {
                    println("⚠️ Could not get FCM token: ${e.message}")
                    ""
                }

                // 5. Create shop_services document with shop info — clean slate
                val shopCustomization = ShopCustomization(
                    shopName = shopName,
                    shopAddress = shopAddress,
                    status = "pending",
                    ownerId = user.uid,
                    ownerName = fullName,
                    ownerEmail = email,
                    services = emptyList(), // owner adds services from dashboard
                    ownerFcmToken = fcmToken
                )
                firestore.collection("shop_services").document(shopId)
                    .set(shopCustomization)
                    .await()
                println("✅ Owner signup complete: shop_services/$shopId created — clean slate")

                _currentUserData.value = userData
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Owner sign up failed")
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

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
