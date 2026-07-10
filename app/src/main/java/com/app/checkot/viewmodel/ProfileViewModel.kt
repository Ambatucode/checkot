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

    fun updateUserProfile(updates: Map<String, Any>) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = auth.currentUser ?: return@launch
            try {
                // SECURITY: Strip out privileged fields so a user cannot
                // promote themselves to 'owner' or overwrite their userId.
                val safeUpdates = updates.filterKeys { key ->
                    key !in setOf("role", "userId", "ownedShopId")
                }
                if (safeUpdates.isEmpty()) return@launch
                firestore.collection("users").document(user.uid).update(safeUpdates).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update profile: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
