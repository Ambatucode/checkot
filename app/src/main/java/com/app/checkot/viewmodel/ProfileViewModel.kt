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

    fun updateUserProfile(updates: Map<String, Any>, onResult: (success: Boolean, error: String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = auth.currentUser
            if (user == null) {
                _isLoading.value = false
                onResult(false, "You're not signed in.")
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
                firestore.collection("users").document(user.uid).update(safeUpdates).await()
                onResult(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update profile: ${e.message}")
                onResult(false, "Couldn't save your profile. Check your connection and try again.")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
