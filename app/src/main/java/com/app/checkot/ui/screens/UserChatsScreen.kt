package com.app.checkot.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.app.checkot.model.ChatThread
import com.app.checkot.ui.components.BackTopAppBar
import com.app.checkot.viewmodel.AuthViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserChatsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val currentUser by authViewModel.currentUserData.collectAsState()
    var chatThreads by remember { mutableStateOf<List<ChatThread>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val authUid = com.google.firebase.ktx.Firebase.auth.currentUser?.uid ?: ""
    val effectiveUid = (currentUser?.userId ?: "").ifBlank { authUid }
    val isOwner = currentUser?.role == "owner"
    val ownedShopId = currentUser?.ownedShopId ?: ""

    // Real-time listener on Firestore chats collection
    DisposableEffect(effectiveUid, ownedShopId, isOwner) {
        if (effectiveUid.isBlank() && ownedShopId.isBlank()) {
            isLoading = false
            return@DisposableEffect onDispose {}
        }

        val db = Firebase.firestore
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        val threadsMap = mutableMapOf<String, ChatThread>()

        fun updateThreads() {
            isLoading = false
            val grouped = threadsMap.values.groupBy { thread ->
                val sId = thread.shopId
                val uId = thread.userId
                if (sId.isNotBlank() && uId.isNotBlank()) "${sId}_${uId}" else (thread.chatId.ifBlank { thread.bookingId })
            }
            val deduplicated = grouped.map { (_, threads) ->
                val latest = threads.maxByOrNull { it.lastMessageTimestamp } ?: threads.first()
                val totalUnreadOwner = threads.sumOf { it.unreadCountOwner }
                val totalUnreadCustomer = threads.sumOf { it.unreadCountCustomer }
                val unifiedId = if (latest.shopId.isNotBlank() && latest.userId.isNotBlank()) "${latest.shopId}_${latest.userId}" else latest.chatId.ifBlank { latest.bookingId }
                latest.copy(
                    chatId = unifiedId,
                    unreadCountOwner = totalUnreadOwner,
                    unreadCountCustomer = totalUnreadCustomer
                )
            }
            chatThreads = deduplicated.sortedByDescending { it.lastMessageTimestamp }
        }

        if (effectiveUid.isNotBlank()) {
            val userQuery = db.collection("chats").whereEqualTo("userId", effectiveUid)
            listeners.add(userQuery.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                for (doc in snapshot.documents) {
                    val thread = doc.toObject(ChatThread::class.java)
                    if (thread != null) {
                        threadsMap[doc.id] = thread
                    }
                }
                updateThreads()
            })
        }

        if (isOwner && ownedShopId.isNotBlank()) {
            val shopQuery = db.collection("chats").whereEqualTo("shopId", ownedShopId)
            listeners.add(shopQuery.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                for (doc in snapshot.documents) {
                    val thread = doc.toObject(ChatThread::class.java)
                    if (thread != null) {
                        threadsMap[doc.id] = thread
                    }
                }
                updateThreads()
            })
        }

        onDispose {
            listeners.forEach { it.remove() }
        }
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Messages",
                onBack = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                chatThreads.isEmpty() -> {
                    EmptyChatsState(
                        isOwner = isOwner,
                        onExploreShops = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = chatThreads,
                            key = { index, thread ->
                                val id = if (thread.shopId.isNotBlank() && thread.userId.isNotBlank()) "${thread.shopId}_${thread.userId}" else thread.chatId.ifBlank { thread.bookingId }
                                if (id.isNotBlank()) id else "thread_${index}_${thread.lastMessageTimestamp}"
                            }
                        ) { _, thread ->
                            ChatThreadRow(
                                thread = thread,
                                isOwner = isOwner,
                                onClick = {
                                    val effectiveChatId = if (thread.shopId.isNotBlank() && thread.userId.isNotBlank()) {
                                        "${thread.shopId}_${thread.userId}"
                                    } else {
                                        thread.chatId.ifBlank { thread.bookingId }
                                    }
                                    if (effectiveChatId.isNotBlank()) {
                                        val recipientName = if (isOwner) {
                                            thread.customerName.ifBlank { "Customer" }
                                        } else {
                                            thread.shopName.ifBlank { "Car Wash Shop" }
                                        }
                                        val encodedName = try { Uri.encode(recipientName) } catch (_: Exception) { "Chat" }
                                        val route = "chat/$effectiveChatId?bookingId=${thread.bookingId}&shopId=${thread.shopId}&customerId=${thread.userId}&recipientName=${encodedName}"
                                        navController.navigate(route)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatThreadRow(
    thread: ChatThread,
    isOwner: Boolean,
    onClick: () -> Unit
) {
    val displayName = if (isOwner) {
        thread.customerName.ifBlank { "Customer" }
    } else {
        thread.shopName.ifBlank { "Car Wash Shop" }
    }

    val unreadCount = if (isOwner) thread.unreadCountOwner else thread.unreadCountCustomer
    val hasUnread = unreadCount > 0

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOwner) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOwner) Icons.Default.Person else Icons.Default.Storefront,
                    contentDescription = null,
                    tint = if (isOwner) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Thread info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = formatChatTime(thread.lastMessageTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = thread.lastMessage.ifBlank { "Tap to open chat" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasUnread) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (hasUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text("$unreadCount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyChatsState(
    isOwner: Boolean,
    onExploreShops: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No Messages Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isOwner) {
                "When customers send messages regarding their bookings, your conversations will appear here."
            } else {
                "When you book a service or contact a car wash shop, your conversations will appear here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 14.sp
        )

        if (!isOwner) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onExploreShops,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Explore Car Wash Shops")
            }
        }
    }
}

private fun formatChatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestamp }

    return if (now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR)
    ) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    } else if (now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - time.get(Calendar.DAY_OF_YEAR) == 1
    ) {
        "Yesterday"
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
