package com.app.checkot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.app.checkot.model.ChatMessage
import com.app.checkot.ui.theme.CheckotBadgeTeal
import com.app.checkot.ui.theme.CheckotCardSurface
import com.app.checkot.viewmodel.AuthViewModel
import com.app.checkot.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    chatId: String,
    bookingId: String = "",
    shopId: String = "",
    recipientName: String = "Chat",
    carDetails: String = "",
    recipientToken: String = "",
    authViewModel: AuthViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel()
) {
    val currentUserData by authViewModel.currentUserData.collectAsState()
    val currentUserId = currentUserData?.userId ?: ""
    val currentUserRole = currentUserData?.role ?: "customer"
    val isCustomer = currentUserRole.lowercase() == "customer"

    val messages by chatViewModel.messages.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Start listener and clear unread count on open
    LaunchedEffect(chatId) {
        if (chatId.isNotBlank()) {
            chatViewModel.startChatListener(
                chatId = chatId,
                bookingId = bookingId,
                shopId = shopId,
                userId = currentUserId,
                customerName = currentUserData?.fullName ?: "Customer"
            )
            chatViewModel.markAsRead(chatId, isCustomer)
        }
    }

    // Scroll to latest message when message list updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val presetMessages = if (isCustomer) {
        listOf("I've arrived at the shop!", "Running 5 mins late", "Ready for wash", "Where can I park?")
    } else {
        listOf("Car is now in the bay", "Finishing up interior wash", "Ready for pickup!", "Please approach the counter")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = recipientName.ifBlank { "In-App Chat" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (carDetails.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = CheckotBadgeTeal
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = carDetails,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CheckotCardSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F172A))
        ) {
            // Message List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && messages.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        color = CheckotBadgeTeal
                    )
                } else if (messages.isEmpty()) {
                    Text(
                        text = "No messages yet. Send a message to start the conversation!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.messageId.ifBlank { it.timestamp.toString() } }) { msg ->
                            val isFromMe = msg.senderId == currentUserId || (isCustomer && msg.senderRole == "customer")
                            MessageBubble(message = msg, isFromMe = isFromMe)
                        }
                    }
                }
            }

            // Quick Preset Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presetMessages) { preset ->
                    SuggestionChip(
                        onClick = {
                            chatViewModel.sendMessage(
                                chatId = chatId,
                                senderId = currentUserId,
                                senderRole = currentUserRole,
                                text = preset,
                                recipientToken = recipientToken
                            )
                        },
                        label = {
                            Text(
                                preset,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        border = null,
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            // Input Bar
            Surface(
                color = CheckotCardSurface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Type a message...", color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CheckotBadgeTeal,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                chatViewModel.sendMessage(
                                    chatId = chatId,
                                    senderId = currentUserId,
                                    senderRole = currentUserRole,
                                    text = inputText,
                                    recipientToken = recipientToken
                                )
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(CheckotBadgeTeal, shape = CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isFromMe: Boolean) {
    val bubbleColor = if (isFromMe) CheckotBadgeTeal else CheckotCardSurface
    val textColor = if (isFromMe) Color.Black else Color.White
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) {
        if (message.timestamp > 0) timeFormat.format(Date(message.timestamp)) else ""
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromMe) 16.dp else 4.dp,
                bottomEnd = if (isFromMe) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    lineHeight = 20.sp
                )
                if (formattedTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
