package com.app.checkot.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import com.app.checkot.BuildConfig
import com.app.checkot.model.*
import com.app.checkot.service.NotificationHelper
import com.app.checkot.service.FCMSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

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

// Drives the phone-verification screen (signup gate + change-number flow).
sealed class PhoneVerifyState {
    object Idle : PhoneVerifyState()        // nothing requested yet
    object Sending : PhoneVerifyState()     // requesting an SMS code
    object CodeSent : PhoneVerifyState()    // code delivered; awaiting entry
    object Verifying : PhoneVerifyState()   // confirming the code / linking
    object Success : PhoneVerifyState()     // number verified + saved
    data class Error(val message: String) : PhoneVerifyState()
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
        // Demo mode (this branch): if demo credentials are configured in
        // local.properties, silently sign in as the configured demo role on
        // every launch — no login/signup UI. Switching roles = comment/uncomment
        // the credentials in local.properties + rebuild; the configured role
        // always wins over any cached session. If NO demo credentials are set,
        // the app behaves exactly like the normal login flow.
        val demoEmail = demoRoleEmail()
        if (demoEmail != null) {
            demoSignIn(demoEmail)
        } else {
            val existingUser = auth.currentUser
            if (existingUser != null) {
                _authState.value = AuthState.Authenticated
                loadUserData()
                // Upload FCM token directly — ensures token is always saved
                // even if loadUserData hasn't completed yet
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        firestore.collection("users").document(existingUser.uid)
                            .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener { Log.d(TAG, "✅ AuthVM: FCM token saved on init") }
                            .addOnFailureListener { e -> Log.e(TAG, "❌ AuthVM: Failed to save token: ${e.message}") }
                    }
            }
        }
    }

    /** Demo role configured in local.properties, or null when demo mode is off. */
    private fun demoRoleEmail(): String? = when {
        BuildConfig.DEMO_OWNER_EMAIL.isNotBlank() && BuildConfig.DEMO_OWNER_PASSWORD.isNotBlank() ->
            BuildConfig.DEMO_OWNER_EMAIL
        BuildConfig.DEMO_EMAIL.isNotBlank() && BuildConfig.DEMO_PASSWORD.isNotBlank() ->
            BuildConfig.DEMO_EMAIL
        else -> null
    }

    private fun demoRolePassword(email: String): String = when (email) {
        BuildConfig.DEMO_OWNER_EMAIL -> BuildConfig.DEMO_OWNER_PASSWORD
        else -> BuildConfig.DEMO_PASSWORD
    }

    private fun demoSignIn(email: String) {
        // Keep the RBAC gate on Loading so the Login screen never flashes
        // while the demo sign-in is in flight.
        _roleLoadState.value = RoleLoadState.Loading
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, demoRolePassword(email)).await()
                val user = result.user ?: throw Exception("Demo sign-in failed")
                val docRef = firestore.collection("users").document(user.uid)
                if (!docRef.get().await().exists()) {
                    if (email == BuildConfig.DEMO_OWNER_EMAIL) {
                        seedDemoOwner(user.uid, email)
                    } else {
                        seedDemoCustomer(user.uid, email)
                    }
                }
                loadUserData()
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                Log.e(TAG, "Demo auto sign-in failed: ${e.message}")
                // Fall back to the normal login flow.
                _roleLoadState.value = RoleLoadState.Ready
            }
        }
    }

    /** First run: create the demo customer profile (role=customer, verified phone). */
    private suspend fun seedDemoCustomer(userId: String, email: String) {
        val userData = CarWashUser(
            userId = userId,
            fullName = "Demo User",
            email = email,
            phoneNumber = "+10000000000",
            phoneVerified = true,
            createdAt = System.currentTimeMillis(),
            role = "customer",
            savedCars = emptyList()
        )
        firestore.collection("users").document(userId).set(userData).await()
    }

    /**
     * First run: create the demo owner profile plus a pending shop. The shop
     * starts 'pending' (the rules require it) — the owner dashboard works
     * immediately; an admin must approve it before clients can book it.
     */
    private suspend fun seedDemoOwner(userId: String, email: String) {
        val shopId = firestore.collection("shop_services").document().id
        val userData = CarWashUser(
            userId = userId,
            fullName = "Demo Owner",
            email = email,
            phoneNumber = "+10000000001",
            phoneVerified = true,
            createdAt = System.currentTimeMillis(),
            role = "owner",
            ownedShopId = shopId,
            savedCars = emptyList()
        )
        // User doc FIRST — the shop_services create rule requires the owner's
        // users doc to already exist with ownedShopId set.
        firestore.collection("users").document(userId).set(userData).await()
        val shopCustomization = ShopCustomization(
            shopName = "Demo Car Wash",
            shopAddress = "",
            status = "pending",
            ownerId = userId,
            ownerName = "Demo Owner",
            ownerEmail = email,
            services = emptyList()
        )
        firestore.collection("shop_services").document(shopId).set(shopCustomization).await()
        Log.d(TAG, "✅ Demo owner seeded: shop_services/$shopId (pending)")
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

    /**
     * Sign in with a Google ID token (obtained via Credential Manager in the UI).
     * A brand-new Google user has no users/{uid} doc, so we create a "customer"
     * profile immediately — otherwise loadUserData()'s missing-profile guard would
     * lock them out with RoleLoadState.Error. phoneVerified starts false so the
     * phone-verification gate runs before they reach the app.
     */
    fun signInWithGoogle(idToken: String, isOwnerMode: Boolean = false) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user ?: throw Exception("Google sign-in failed")
                val docRef = firestore.collection("users").document(user.uid)
                val snapshot = docRef.get().await()
                if (!snapshot.exists()) {
                    if (isOwnerMode) {
                        // Owner mode: write user and shop documents immediately.
                        val shopId = firestore.collection("shop_services").document().id
                        val userData = CarWashUser(
                            userId = user.uid,
                            fullName = user.displayName ?: "",
                            email = user.email ?: "",
                            phoneNumber = "",
                            phoneVerified = false,
                            createdAt = System.currentTimeMillis(),
                            role = "owner",
                            ownedShopId = shopId,
                            isApproved = false
                        )
                        docRef.set(userData).await()

                        val shopCustomization = ShopCustomization(
                            shopName = "${user.displayName ?: "New"}'s Shop",
                            shopAddress = "",
                            status = "pending",
                            ownerId = user.uid,
                            ownerName = user.displayName ?: "",
                            ownerEmail = user.email ?: "",
                            services = emptyList(),
                            ownerFcmToken = ""
                        )
                        firestore.collection("shop_services").document(shopId).set(shopCustomization).await()

                        _currentUserData.value = userData
                        uploadFcmToken(user.uid)
                        _roleLoadState.value = RoleLoadState.Ready
                    } else {
                        // Customer mode: write record immediately, skip phone verification.
                        val userData = CarWashUser(
                            userId = user.uid,
                            fullName = user.displayName ?: "",
                            email = user.email ?: "",
                            phoneNumber = "",
                            phoneVerified = false,
                            createdAt = System.currentTimeMillis(),
                            role = "customer",
                            savedCars = emptyList()
                        )
                        docRef.set(userData).await()
                        _currentUserData.value = userData
                        uploadFcmToken(user.uid)
                        _roleLoadState.value = RoleLoadState.Ready
                    }
                } else {
                    loadUserData()
                }
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Google sign-in failed")
            }
        }
    }

    // ---- Phone number verification (signup gate + change-number) -------------

    private val _phoneVerifyState = MutableStateFlow<PhoneVerifyState>(PhoneVerifyState.Idle)
    val phoneVerifyState: StateFlow<PhoneVerifyState> = _phoneVerifyState

    private var storedVerificationId: String? = null
    private var pendingPhoneE164: String = ""
    // "signup" -> link the number to the account; "change" -> replace the number.
    private var pendingMode: String = "signup"

    fun resetPhoneVerify() {
        _phoneVerifyState.value = PhoneVerifyState.Idle
        storedVerificationId = null
    }

    /** True once a code has been sent — keeps the UI on the code step after a bad-code error. */
    fun hasPendingCode(): Boolean = storedVerificationId != null

    /** Send an SMS code to a full E.164 number (e.g. +639123456789). */
    fun startPhoneVerification(activity: Activity, phoneE164: String, mode: String) {
        pendingMode = mode
        pendingPhoneE164 = phoneE164
        storedVerificationId = null
        _phoneVerifyState.value = PhoneVerifyState.Sending
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Instant/auto-retrieval on the same device — no code entry needed.
                applyPhoneCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _phoneVerifyState.value = PhoneVerifyState.Error(mapPhoneError(e))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                _phoneVerifyState.value = PhoneVerifyState.CodeSent
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneE164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** Confirm the 6-digit code the user typed. */
    fun confirmPhoneCode(code: String) {
        val vid = storedVerificationId
        if (vid == null) {
            _phoneVerifyState.value = PhoneVerifyState.Error("Please request a code first.")
            return
        }
        applyPhoneCredential(PhoneAuthProvider.getCredential(vid, code))
    }

    // Signs in, links (signup), or updates (change) the phone credential.
    // If auth.currentUser is null, this acts as a pure SMS Sign In.
    private fun applyPhoneCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _phoneVerifyState.value = PhoneVerifyState.Verifying
            try {
                val user = auth.currentUser
                val uid: String
                
                if (user == null) {
                    // 1. Pure Phone Sign-In
                    val result = auth.signInWithCredential(credential).await()
                    val signedInUser = result.user ?: throw Exception("Phone sign-in failed")
                    uid = signedInUser.uid
                    
                    // Check if Firestore record exists
                    val docRef = firestore.collection("users").document(uid)
                    val snapshot = docRef.get().await()
                    if (!snapshot.exists()) {
                        // Create basic customer profile
                        val userData = CarWashUser(
                            userId = uid,
                            fullName = "New User",
                            email = "",
                            phoneNumber = pendingPhoneE164,
                            phoneVerified = true,
                            createdAt = System.currentTimeMillis(),
                            role = "customer",
                            savedCars = emptyList()
                        )
                        docRef.set(userData).await()
                        _currentUserData.value = userData
                    } else {
                        // Ensure phone is marked verified if they already existed
                        docRef.set(mapOf("phoneNumber" to pendingPhoneE164, "phoneVerified" to true), SetOptions.merge()).await()
                        loadUserData()
                    }
                    _authState.value = AuthState.Authenticated
                } else {
                    // 2. Linking / Updating an existing session
                    uid = user.uid
                    if (pendingMode == "change") {
                        user.updatePhoneNumber(credential).await()
                    } else {
                        user.linkWithCredential(credential).await()
                    }
                    firestore.collection("users").document(uid)
                        .set(
                            mapOf("phoneNumber" to pendingPhoneE164, "phoneVerified" to true),
                            SetOptions.merge()
                        ).await()
                    _currentUserData.value = _currentUserData.value
                        ?.copy(phoneNumber = pendingPhoneE164, phoneVerified = true)
                }
                
                _phoneVerifyState.value = PhoneVerifyState.Success
            } catch (e: Exception) {
                _phoneVerifyState.value = PhoneVerifyState.Error(mapPhoneError(e))
            }
        }
    }

    private fun mapPhoneError(e: Exception): String = when {
        e is FirebaseAuthUserCollisionException ->
            "That number is already linked to another account. Use a different number."
        e.message?.contains("already in use", ignoreCase = true) == true ->
            "That number is already linked to another account. Use a different number."
        e.message?.contains("invalid", ignoreCase = true) == true &&
            e.message?.contains("code", ignoreCase = true) == true ->
            "That code isn't right. Check it and try again."
        e.message?.contains("expired", ignoreCase = true) == true ->
            "That code expired. Request a new one."
        else -> e.message ?: "Phone verification failed. Try again."
    }



    fun deleteClientAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                onError("No authenticated user found")
                return@launch
            }
            val userId = user.uid

            try {
                // 1. Check for any active customer bookings
                val activeBookings = firestore.collection("bookings")
                    .whereEqualTo("userId", userId)
                    .whereIn("status", listOf("PENDING", "CONFIRMED", "IN_PROGRESS"))
                    .get().await()

                if (!activeBookings.isEmpty) {
                    onError("Cannot delete account while you have active or pending bookings. Please cancel them first.")
                    return@launch
                }

                // 2. Delete user record from users collection (savedCars embedded inside)
                firestore.collection("users").document(userId).delete().await()

                // 3. Delete Firebase Auth user
                user.delete().await()

                Log.d(TAG, "✅ Client account deleted successfully.")
                signOut()
                onSuccess()
            } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                Log.e(TAG, "❌ Firebase Auth delete requires recent login: ${e.message}")
                onError("For security, please log out, log back in, and try again.")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to delete client account: ${e.message}")
                onError(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState.Unauthenticated
        _currentUserData.value = null
        _roleLoadState.value = RoleLoadState.Ready
        resetPhoneVerify()
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

    fun completeProfile(
        fullName: String,
        email: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "fullName" to fullName,
                    "email" to email
                )
                firestore.collection("users").document(uid)
                    .set(updates, SetOptions.merge())
                    .await()
                
                // Refresh local user data
                loadUserData()
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to update profile.")
            }
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
