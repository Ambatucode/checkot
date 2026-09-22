package com.app.checkot.viewmodel

import android.app.Application
import android.util.Log
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

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ProfileViewModel"
    private val auth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun updateUserProfile(updates: Map<String, Any>, onResult: (success: Boolean, error: String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val user = auth.currentUser
            if (user == null) {
                _isLoading.value = false
                val err = "You're not signed in."
                _errorMessage.value = err
                onResult(false, err)
                return@launch
            }
            try {
                // SECURITY: Strip out privileged fields so a user cannot
                // promote themselves to 'owner' or overwrite their userId.
                val safeUpdates = updates.filterKeys { key ->
                    key !in setOf("role", "userId", "ownedShopId")
                }
                if (safeUpdates.isEmpty()) {
                    onResult(true, null)
                    return@launch
                }
                // Fetch user document to check role and ownedShopId
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                val role = userDoc.getString("role")
                val ownedShopId = userDoc.getString("ownedShopId")
                
                firestore.collection("users").document(user.uid).update(safeUpdates).await()
                
                val newName = safeUpdates["fullName"] as? String
                val newPhone = safeUpdates["phoneNumber"] as? String
                if (role == "owner" && !ownedShopId.isNullOrEmpty()) {
                    val shopUpdates = mutableMapOf<String, Any>()
                    if (newName != null) shopUpdates["ownerName"] = newName
                    if (newPhone != null) {
                        shopUpdates["ownerPhone"] = newPhone
                        shopUpdates["ownerPhoneVerified"] = true
                    }
                    if (shopUpdates.isNotEmpty()) {
                        firestore.collection("shop_services").document(ownedShopId)
                            .update(shopUpdates)
                            .await()
                    }
                }
                
                onResult(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update profile: ${e.message}")
                val errStr = "Couldn't save your profile. Check your connection and try again."
                _errorMessage.value = errStr
                onResult(false, errStr)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
