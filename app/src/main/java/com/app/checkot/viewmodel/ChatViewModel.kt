package com.app.checkot.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.checkot.model.ChatMessage
import com.app.checkot.model.ChatThread
import com.google.firebase.functions.ktx.functions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore: FirebaseFirestore = Firebase.firestore
    private val TAG = "ChatViewModel"

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _chatThread = MutableStateFlow<ChatThread?>(null)
    val chatThread: StateFlow<ChatThread?> = _chatThread

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var messagesListener: ListenerRegistration? = null
    private var threadListener: ListenerRegistration? = null

    /**
     * Connects to a real-time chat thread and message stream.
     */
    fun startChatListener(
        chatId: String,
        bookingId: String = "",
        shopId: String = "",
        userId: String = "",
        customerName: String = "",
        shopName: String = ""
    ) {
        if (chatId.isBlank()) return

        _isLoading.value = true

        // 1. Thread Metadata Listener
        threadListener?.remove()
        val threadRef = firestore.collection("chats").document(chatId)
        threadListener = threadRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "❌ Failed to listen to chat thread: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val existing = snapshot.toObject(ChatThread::class.java)
                _chatThread.value = existing

                // Self-healing: Update any missing fields if parameters are provided
                val updates = mutableMapOf<String, Any>()
                if (userId.isNotBlank() && existing?.userId.isNullOrBlank()) updates["userId"] = userId
                if (shopId.isNotBlank() && existing?.shopId.isNullOrBlank()) updates["shopId"] = shopId
                if (bookingId.isNotBlank() && existing?.bookingId.isNullOrBlank()) updates["bookingId"] = bookingId
                if (customerName.isNotBlank() && existing?.customerName.isNullOrBlank()) updates["customerName"] = customerName
                if (shopName.isNotBlank() && existing?.shopName.isNullOrBlank()) updates["shopName"] = shopName

                if (updates.isNotEmpty()) {
                    threadRef.set(updates, SetOptions.merge())
                }
            } else {
                // Initialize default thread if document doesn't exist yet
                val newThread = ChatThread(
                    chatId = chatId,
                    bookingId = bookingId,
                    shopId = shopId,
                    userId = userId,
                    customerName = customerName,
                    shopName = shopName,
                    lastMessage = "Chat started",
                    lastMessageTimestamp = System.currentTimeMillis()
                )
                _chatThread.value = newThread
                threadRef.set(newThread, SetOptions.merge())
            }
        }

        // 2. Real-time Messages Listener
        messagesListener?.remove()
        messagesListener = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e(TAG, "❌ Failed to listen to messages: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messageList = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                    _messages.value = messageList
                }
            }
    }

    /**
     * Sends a new chat message, updates thread metadata, and dispatches FCM notification.
     */
    fun sendMessage(
        chatId: String,
        senderId: String,
        senderRole: String,
        text: String,
        recipientToken: String = ""
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || chatId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messageRef = firestore.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document()

                val now = System.currentTimeMillis()
                val message = ChatMessage(
                    messageId = messageRef.id,
                    chatId = chatId,
                    senderId = senderId,
                    senderRole = senderRole,
                    text = trimmed,
                    timestamp = now,
                    isRead = false
                )

                // 1. Write message doc
                messageRef.set(message).await()

                // 2. Update thread lastMessage & unread count
                val threadRef = firestore.collection("chats").document(chatId)
                val isCustomer = senderRole.lowercase() == "customer"
                
                firestore.runTransaction { tx ->
                    val snap = tx.get(threadRef)
                    val currentOwnerUnread = (snap.getLong("unreadCountOwner") ?: 0).toInt()
                    val currentCustomerUnread = (snap.getLong("unreadCountCustomer") ?: 0).toInt()

                    tx.set(
                        threadRef,
                        mapOf(
                            "lastMessage" to trimmed,
                            "lastMessageTimestamp" to now,
                            "unreadCountOwner" to if (isCustomer) currentOwnerUnread + 1 else 0,
                            "unreadCountCustomer" to if (!isCustomer) currentCustomerUnread + 1 else 0
                        ),
                        SetOptions.merge()
                    )
                }.await()

                // 3. Send Push Notification to recipient via FCM if token available
                if (recipientToken.isNotBlank()) {
                    val senderTitle = if (isCustomer) "New message from Customer" else "New message from Shop"
                    try {
                        Firebase.functions("asia-southeast1")
                            .getHttpsCallable("sendPushNotification")
                            .call(
                                mapOf(
                                    "targetToken" to recipientToken,
                                    "title" to senderTitle,
                                    "body" to trimmed
                                )
                            )
                            .await()
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Failed to send FCM push for chat: ${e.message}")
                    }
                }

                Log.d(TAG, "✅ Message sent successfully to chat $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send message: ${e.message}")
            }
        }
    }

    /**
     * Clears unread badge counts for the active user role.
     */
    fun markAsRead(chatId: String, isCustomerRole: Boolean) {
        if (chatId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fieldToClear = if (isCustomerRole) "unreadCountCustomer" else "unreadCountOwner"
                firestore.collection("chats").document(chatId)
                    .set(mapOf(fieldToClear to 0), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to mark as read: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesListener?.remove()
        threadListener?.remove()
    }
}
