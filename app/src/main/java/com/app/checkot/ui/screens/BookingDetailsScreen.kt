package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.checkot.ui.components.BackTopAppBar
import com.app.checkot.ui.components.ConfirmDialog
import com.app.checkot.ui.components.DetailRow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailsScreen(
    bookingId: String?,
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    bookingViewModel: BookingViewModel = viewModel()
) {
    val bookings by bookingViewModel.userBookings.collectAsState()
    val booking = bookings.find { it.bookingId == bookingId }
    val scope = rememberCoroutineScope()
    var isCancelling by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var queueInfo by remember { mutableStateOf(QueueInfo()) }

    // Direct Firestore listener for queue info
    DisposableEffect(booking?.bookingId, booking?.shopId, booking?.bookingDate) {
        if (booking == null) return@DisposableEffect onDispose {}
        val listener = Firebase.firestore.collection("bookings")
            .whereEqualTo("shopId", booking.shopId)
            .whereEqualTo("bookingDate", booking.bookingDate)
            .whereIn("status", listOf("PENDING", "CONFIRMED", "IN_PROGRESS"))
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val bookings = snapshot.documents.mapNotNull { it.toObject(Booking::class.java) }
                val sorted = bookings.sortedBy { it.createdAt }
                val index = sorted.indexOfFirst { it.bookingId == booking.bookingId }
                val position = if (index != -1) index + 1 else -1
                val ahead = if (index > 0) sorted.subList(0, index) else emptyList()
                val estimated = ahead.sumOf { b ->
                    BookingUtils.bookingDurationMinutes(b)
                }
                queueInfo = QueueInfo(position, estimated, sorted.size)
            }
        onDispose { listener.remove() }
    }

    // Load the shop name from Firestore
    var shopName by remember(booking) { mutableStateOf("") }
    LaunchedEffect(booking?.shopId) {
        val shopId = booking?.shopId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val doc = Firebase.firestore.collection("shop_services").document(shopId).get().await()
                val name = doc.getString("shopName")
                if (!name.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { shopName = name }
                } else {
                    withContext(Dispatchers.Main) { shopName = shopId.takeLast(6).uppercase() }
                }
            } catch (e: Exception) {
                println("❌ Failed to load shop name: ${e.message}")
                withContext(Dispatchers.Main) { shopName = shopId.takeLast(6).uppercase() }
            }
        }
    }

    if (booking == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Loading booking details...")
            }
        }
        return
    }
    if (showCancelDialog) {
        ConfirmDialog(
            title = "Cancel Booking",
            text = "Are you sure you want to cancel this booking? This action cannot be undone.",
            confirmLabel = "Yes, Cancel",
            dismissLabel = "No",
            onConfirm = {
                scope.launch {
                    isCancelling = true
                    bookingViewModel.cancelBooking(booking.bookingId)
                    isCancelling = false
                    showCancelDialog = false
                    navController.popBackStack()
                }
            },
            onDismiss = { showCancelDialog = false }
        )
    }
    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Booking Details",
                onBack = { if (navController.previousBackStackEntry != null) navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (booking.status) {
                            BookingStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                            BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer
                            BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer
                            BookingStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
                            BookingStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = booking.status.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = when (booking.status) {
                                    BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                                    BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.onPrimaryContainer
                                    BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onTertiaryContainer
                                    BookingStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    BookingStatus.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                        Icon(
                            imageVector = when (booking.status) {
                                BookingStatus.PENDING -> Icons.Default.HourglassEmpty
                                BookingStatus.CONFIRMED -> Icons.Default.CheckCircle
                                BookingStatus.IN_PROGRESS -> Icons.Default.Build
                                BookingStatus.COMPLETED -> Icons.Default.DoneAll
                                BookingStatus.CANCELLED -> Icons.Default.Cancel
                            },
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = when (booking.status) {
                                BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                                BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.onPrimaryContainer
                                BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onTertiaryContainer
                                BookingStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                                BookingStatus.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }
            // Countdown card
            item {
                val countdownEnd = remember(booking.bookingId) {
                    when (booking.status) {
                        BookingStatus.PENDING -> booking.createdAt + 2 * 60 * 60 * 1000L
                        BookingStatus.CONFIRMED -> {
                            try {
                                val parts = booking.timeSlot.split(" ")
                                val t = parts[0].split(":")
                                var h = t[0].toInt()
                                val m = t[1].toInt()
                                if (parts[1] == "PM" && h != 12) h += 12
                                if (parts[1] == "AM" && h == 12) h = 0
                                val cal = java.util.Calendar.getInstance().apply {
                                    timeInMillis = booking.bookingDate
                                    set(java.util.Calendar.HOUR_OF_DAY, h)
                                    set(java.util.Calendar.MINUTE, m)
                                    add(java.util.Calendar.MINUTE, 30)
                                }
                                cal.timeInMillis
                            } catch (e: Exception) { 0L }
                        }
                        else -> 0L
                    }
                }
                var countdownText by remember { mutableStateOf("") }
                LaunchedEffect(countdownEnd) {
                    while (countdownEnd > 0 && countdownEnd > System.currentTimeMillis()) {
                        val diff = countdownEnd - System.currentTimeMillis()
                        val totalMin = (diff / 60000).toInt()
                        countdownText = if (totalMin > 0) {
                            val h = totalMin / 60
                            val m = totalMin % 60
                            when (booking.status) {
                                BookingStatus.PENDING -> if (h > 0) "Auto-cancels in ${h}h ${m}m" else "Auto-cancels in ${m}m"
                                BookingStatus.CONFIRMED -> if (h > 0) "Arrive within ${h}h ${m}m" else "Arrive within ${m}m"
                                else -> ""
                            }
                        } else {
                            when (booking.status) {
                                BookingStatus.PENDING -> "Cancelling soon..."
                                BookingStatus.CONFIRMED -> "Almost expired!"
                                else -> ""
                            }
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }
                if (countdownText.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (booking.status) {
                                BookingStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                                BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = when (booking.status) {
                                    BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                                    BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = countdownText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = when (booking.status) {
                                    BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                                    BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
            item {
                ServiceProgressStepper(status = booking.status)
            }
            // Queue Position Card — only for active bookings
            if (booking.status == BookingStatus.PENDING || booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.IN_PROGRESS) {
                if (queueInfo.position > 0) {
                    item {
                        QueuePositionCard(queueInfo = queueInfo, status = booking.status)
                    }
                }
            }
            item {
                // Service Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Service Details",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Shop:", shopName.ifEmpty { booking.shopId.takeLast(6).uppercase() })
                        DetailRow("Services:", booking.displayServiceNames())
                        val totalMin = BookingUtils.bookingDurationMinutes(booking)
                        val durationText = when {
                            totalMin >= 60 && totalMin % 60 > 0 -> "${totalMin / 60}h ${totalMin % 60}m"
                            totalMin >= 60 -> "${totalMin / 60}h"
                            else -> "$totalMin mins"
                        }
                        DetailRow("Duration:", durationText)
                        DetailRow("Price:", "₱${booking.price}")
                        if (booking.notes.isNotBlank()) {
                             DetailRow("Special Requests:", booking.notes)
                        }
                    }
                }
            }
            item {
                // Car Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Car Details",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val carDetails = booking.carDetails.split(" - ")
                        if (carDetails.size == 2) {
                            DetailRow("Car:", carDetails[0])
                            DetailRow("Plate Number:", carDetails[1])
                        } else {
                            DetailRow("Car:", booking.carDetails)
                        }
                    }
                }
            }
            item {
                // Schedule Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Schedule",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Date:", DateUtils.formatDate(booking.bookingDate))
                        DetailRow("Time:", booking.timeSlot)
                    }
                }
            }
            item {
                // Timeline Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Timeline",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (booking.createdAt > 0) {
                            DetailRow("Created:", DateUtils.formatDateTime(booking.createdAt))
                        }
                        booking.confirmedAt?.let {
                            DetailRow("Confirmed:", DateUtils.formatDateTime(it))
                        }
                        booking.inProgressAt?.let {
                            DetailRow("In Progress:", DateUtils.formatDateTime(it))
                        }
                        booking.completedAt?.let {
                            DetailRow("Completed:", DateUtils.formatDateTime(it))
                        }
                        booking.cancelledAt?.let {
                            DetailRow("Cancelled:", DateUtils.formatDateTime(it))
                        }
                    }
                }
            }
            if (booking.status == BookingStatus.PENDING || booking.status == BookingStatus.CONFIRMED) {
                item {
                    // Cancel Button
                    Button(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        enabled = !isCancelling
                    ) {
                        if (isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel Booking")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceProgressStepper(status: BookingStatus) {
    if (status == BookingStatus.CANCELLED) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("This booking has been cancelled.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    val steps = listOf("Queue", "Accepted", "Washing", "Ready")
    val currentStepIndex = when (status) {
        BookingStatus.PENDING -> 0
        BookingStatus.CONFIRMED -> 1
        BookingStatus.IN_PROGRESS -> 2
        BookingStatus.COMPLETED -> 3
        BookingStatus.CANCELLED -> -1
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Service Progress Tracker",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            // Line with circles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                // Full background line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            MaterialTheme.shapes.small
                        )
                )
                // Completed portion of line
                if (currentStepIndex > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentStepIndex.toFloat() / (steps.size - 1).toFloat())
                            .height(4.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.shapes.small
                            )
                    )
                }
                // Circles
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    steps.forEachIndexed { index, label ->
                        val isCompleted = index < currentStepIndex
                        val isActive = index == currentStepIndex
                        val circleColor = when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isActive -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(circleColor, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = (index + 1).toString(),
                                    color = if (isActive) MaterialTheme.colorScheme.onTertiary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
            // Labels below circles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                steps.forEachIndexed { index, label ->
                    val isActive = index == currentStepIndex
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else null
                    )
                }
            }
        }
    }
}

@Composable
fun QueuePositionCard(queueInfo: QueueInfo, status: BookingStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Queue Position",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Large position number — explicit white: primary (teal) was
            // invisible against the teal primaryContainer card
            Text(
                text = "#${queueInfo.position}",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Cars ahead
            val carsAhead = queueInfo.position - 1
            Text(
                text = if (carsAhead == 0) "You're next!" else "$carsAhead car${if (carsAhead > 1) "s" else ""} ahead of you",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Estimated wait time
            if (carsAhead > 0 && queueInfo.estimatedWaitMinutes > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val waitText = if (queueInfo.estimatedWaitMinutes >= 60) {
                            val hours = queueInfo.estimatedWaitMinutes / 60
                            val mins = queueInfo.estimatedWaitMinutes % 60
                            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
                        } else {
                            "${queueInfo.estimatedWaitMinutes} min"
                        }
                        Text(
                            text = "Est. wait: ~$waitText",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status message
            Text(
                text = when (status) {
                    BookingStatus.PENDING -> "Waiting for shop to confirm your booking"
                    BookingStatus.CONFIRMED -> "Your booking is confirmed, hang tight!"
                    BookingStatus.IN_PROGRESS -> "Your car is being serviced right now"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
