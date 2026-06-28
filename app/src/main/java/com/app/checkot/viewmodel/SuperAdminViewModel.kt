package com.app.checkot.viewmodel

import android.app.Application
import com.app.checkot.model.*
import com.app.checkot.service.FCMSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ShopWithOwner(
    val shopId: String = "",
    val shopName: String = "",
    val shopAddress: String = "",
    val status: String = "pending",
    val ownerId: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val createdAt: Long = 0
)

class SuperAdminViewModel(application: Application) : AndroidViewModel(application) {
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _pendingShops = MutableStateFlow<List<ShopWithOwner>>(emptyList())
    val pendingShops: StateFlow<List<ShopWithOwner>> = _pendingShops

    private val _activeShops = MutableStateFlow<List<ShopWithOwner>>(emptyList())
    val activeShops: StateFlow<List<ShopWithOwner>> = _activeShops

    private val _rejectedShops = MutableStateFlow<List<ShopWithOwner>>(emptyList())
    val rejectedShops: StateFlow<List<ShopWithOwner>> = _rejectedShops

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadShops() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = firestore.collection("shop_services").get().await()
                val shops = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("shopName") ?: return@mapNotNull null
                    ShopWithOwner(
                        shopId = doc.id,
                        shopName = name,
                        shopAddress = doc.getString("shopAddress") ?: "",
                        status = doc.getString("status") ?: "active",
                        ownerId = doc.getString("ownerId") ?: "",
                        ownerName = doc.getString("ownerName") ?: "Unknown",
                        ownerEmail = doc.getString("ownerEmail") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0
                    )
                }

                _pendingShops.value = shops.filter { it.status == "pending" }
                _activeShops.value = shops.filter { it.status == "active" }
                _rejectedShops.value = shops.filter { it.status == "rejected" }
            } catch (e: Exception) {
                println("❌ SuperAdmin: Failed to load shops: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Verify the admin's password before allowing sensitive actions */
    fun verifyPassword(password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val user = Firebase.auth.currentUser
                val email = user?.email
                if (email == null) {
                    onResult(false, "Not logged in")
                    return@launch
                }
                val credential = EmailAuthProvider.getCredential(email, password)
                user!!.reauthenticate(credential).await()
                onResult(true, "")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Wrong password")
            }
        }
    }

    /** Approve a shop — calls onResult(true) on success, onResult(false) on failure */
    fun approveShop(shopId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                firestore.collection("shop_services").document(shopId)
                    .update("status", "active").await()
                println("✅ SuperAdmin: Shop $shopId approved")
                // Notify the owner
                notifyOwner(shopId, "approved")
                loadShops()
                onResult(true)
            } catch (e: Exception) {
                println("❌ SuperAdmin: Failed to approve shop $shopId: ${e.message}")
                onResult(false)
            }
        }
    }

    /** Reject a shop — calls onResult(true) on success, onResult(false) on failure */
    fun rejectShop(shopId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                firestore.collection("shop_services").document(shopId)
                    .update("status", "rejected").await()
                println("✅ SuperAdmin: Shop $shopId rejected")
                // Notify the owner
                notifyOwner(shopId, "rejected")
                loadShops()
                onResult(true)
            } catch (e: Exception) {
                println("❌ SuperAdmin: Failed to reject shop $shopId: ${e.message}")
                onResult(false)
            }
        }
    }

    /** Send a push notification to the shop owner about approval/rejection */
    private suspend fun notifyOwner(shopId: String, action: String) {
        try {
            val doc = firestore.collection("shop_services").document(shopId).get().await()
            val token = doc.getString("ownerFcmToken")
            val shopName = doc.getString("shopName") ?: "Your shop"
            if (!token.isNullOrEmpty()) {
                val (title, body) = if (action == "approved") {
                    Pair("Shop Approved! 🎉", "$shopName is now live. Customers can see you!")
                } else {
                    Pair("Shop Application Reviewed", "$shopName was not approved. Contact support for details.")
                }
                FCMSender.sendToUser(
                    context = getApplication(),
                    userId = "",
                    title = title,
                    body = body,
                    bookingId = "",
                    fcmToken = token
                )
                println("📬 SuperAdmin: Notified owner of shop $shopId about $action")
            } else {
                println("⚠️ SuperAdmin: No FCM token for shop $shopId owner")
            }
        } catch (e: Exception) {
            println("❌ SuperAdmin: Failed to notify owner: ${e.message}")
        }
    }
}
