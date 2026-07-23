package com.app.checkot.viewmodel

import android.app.Application
import android.util.Log
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

sealed class AuthState {
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Error(val message: String) : AuthState()
}

// Gates the UI at startup: no screen may render until the signed-in user's
// role is confirmed from Firestore, otherwise a fast tap can reach screens
// outside the user's role (RBAC violation).
sealed class RoleLoadState {
    object Loading : RoleLoadState()
    object Ready : RoleLoadState()
    data class Error(val message: String) : RoleLoadState()
}

private const val ROLE_FETCH_TIMEOUT_MS = 10_000L

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AuthViewModel"
    private val auth: FirebaseAuth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val appContext = application.applicationContext

    // Upload the FCM token to Firestore for every signed-in user.
    // If the user is an owner, ALSO refresh shop_services/{shopId}.ownerFcmToken
    // here (not only when the owner opens their dashboard) so client→owner and
    // admin→owner pushes always target a fresh token.
    private fun uploadFcmToken(userId: String, ownedShopId: String? = null) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                // merge (not update) so the write survives a missing doc and
                // isn't rejected by field-shape rules.
                firestore.collection("users").document(userId)
                    .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ FCM token saved to Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to upload FCM token: ${e.message}")
                    }
                if (!ownedShopId.isNullOrEmpty()) {
                    firestore.collection("shop_services").document(ownedShopId)
                        .set(mapOf("ownerFcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener { Log.d(TAG, "✅ Owner FCM token refreshed on shop_services/$ownedShopId") }
                        .addOnFailureListener { e -> Log.e(TAG, "Failed to refresh owner token: ${e.message}") }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get FCM token: ${e.message}")
            }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState

    private val _currentUserData = MutableStateFlow<CarWashUser?>(null)
    val currentUserData: StateFlow<CarWashUser?> = _currentUserData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Starts as Loading when a Firebase session is cached, because the role
    // must be re-fetched before any role-gated screen is composed.
    private val _roleLoadState = MutableStateFlow<RoleLoadState>(
        if (auth.currentUser != null) RoleLoadState.Loading else RoleLoadState.Ready
    )
    val roleLoadState: StateFlow<RoleLoadState> = _roleLoadState

    init {
        if (auth.currentUser != null) {
            _authState.value = AuthState.Authenticated
            loadUserData()
            // Upload FCM token directly — ensures token is always saved
            // even if loadUserData hasn't completed yet
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    firestore.collection("users").document(auth.currentUser!!.uid)
                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener { Log.d(TAG, "✅ AuthVM: FCM token saved on init") }
                        .addOnFailureListener { e -> Log.e(TAG, "❌ AuthVM: Failed to save token: ${e.message}") }
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
        shopAddress: String,
        latitude: Double,
        longitude: Double
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
                    Log.w(TAG, "⚠️ Could not get FCM token: ${e.message}")
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
                    latitude = latitude,
                    longitude = longitude,
                    ownerFcmToken = fcmToken
                )
                firestore.collection("shop_services").document(shopId)
                    .set(shopCustomization)
                    .await()
                Log.d(TAG, "✅ Owner signup complete: shop_services/$shopId created — clean slate")

                // Notify admins about the new shop (background, don't block signup)
                viewModelScope.launch {
                    try {
                        val adminSnapshot = firestore.collection("users")
                            .whereEqualTo("role", "admin").get().await()
                        for (adminDoc in adminSnapshot.documents) {
                            val adminToken = adminDoc.getString("fcmToken")
                            if (!adminToken.isNullOrEmpty()) {
                                FCMSender.sendToUser(
                                    context = appContext,
                                    userId = "",
                                    title = "New Shop Pending Approval",
                                    body = "$fullName registered \"$shopName\" — review it in Admin Dashboard",
                                    bookingId = "",
                                    fcmToken = adminToken
                                )
                            }
                        }
                        Log.d(TAG, "📬 Notified ${adminSnapshot.documents.size} admin(s) about new shop")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to notify admins: ${e.message}")
                    }
                }

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
        _roleLoadState.value = RoleLoadState.Ready
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                Log.d(TAG, "Password reset email sent to $email")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send password reset email: ${e.message}")
            }
        }
    }

    fun loadUserData() {
        viewModelScope.launch {
            val user = auth.currentUser ?: run {
                _roleLoadState.value = RoleLoadState.Ready
                return@launch
            }
            _roleLoadState.value = RoleLoadState.Loading
            try {
                val snapshot = withTimeout(ROLE_FETCH_TIMEOUT_MS) {
                    firestore.collection("users").document(user.uid).get().await()
                }
                val userData = snapshot.toObject(CarWashUser::class.java)
                _currentUserData.value = userData
                // Upload FCM token for ALL users (customers AND owners)
                // so the Cloud Function can send push notifications to anyone
                if (userData != null) {
                    // Pass ownedShopId so owners also refresh shop_services.ownerFcmToken.
                    uploadFcmToken(userData.userId, userData.ownedShopId)
                    _roleLoadState.value = RoleLoadState.Ready
                } else {
                    // Missing profile means unknown role — must not fall through
                    // to the default (customer) UI.
                    _roleLoadState.value = RoleLoadState.Error("Your account profile could not be found.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Role fetch timed out after ${ROLE_FETCH_TIMEOUT_MS}ms")
                _roleLoadState.value = RoleLoadState.Error("Loading your account timed out. Check your connection and try again.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load user data: ${e.message}")
                _roleLoadState.value = RoleLoadState.Error("Couldn't load your account. Check your connection and try again.")
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
