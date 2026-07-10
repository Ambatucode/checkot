package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.app.checkot.ui.components.ConfirmDialog

// FIXED BOOKINGS TAB
@Composable
fun OwnerBookingsTab(
    navController: NavController,
    ownerViewModel: OwnerDashboardViewModel,
    paddingValues: PaddingValues
) {
    val allBookings by ownerViewModel.allBookings.collectAsState()
    val allBookingsLoaded by ownerViewModel.allBookingsLoaded.collectAsState()
    var filter by remember { mutableStateOf("all") }
    // FORCE REFRESH WHEN TAB OPENS
    LaunchedEffect(Unit) {
        ownerViewModel.forceRefresh()
    }
    val filteredBookings = when (filter) {
        "pending" -> allBookings.filter { it.status == BookingStatus.PENDING }.sortedBy { it.createdAt }
        "confirmed" -> allBookings.filter { it.status == BookingStatus.CONFIRMED }.sortedBy { it.createdAt }
        "in_progress" -> allBookings.filter { it.status == BookingStatus.IN_PROGRESS }
        "completed" -> allBookings.filter { it.status == BookingStatus.COMPLETED }
        else -> allBookings
    }
    // Customer name lookup + queue positions (must be outside LazyColumn)
    val users by ownerViewModel.allUsers.collectAsState()
    val customerNames = remember(users) {
        users.associate { it.userId to it.fullName }
    }
    val activeSorted = remember(allBookings) {
        allBookings
            .filter { it.status in listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED) }
            .sortedBy { it.createdAt }
    }
    val queuePositions = remember(activeSorted) {
        activeSorted.mapIndexed { i, b -> b.bookingId to (i + 1) }.toMap()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Stats summary card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val pendingCount = allBookings.count { it.status == BookingStatus.PENDING }
                val confirmedCount = allBookings.count { it.status == BookingStatus.CONFIRMED }
                val inProgressCount = allBookings.count { it.status == BookingStatus.IN_PROGRESS }
                val todayCompleted = allBookings.count {
                    it.status == BookingStatus.COMPLETED &&
                    it.completedAt?.let { completed ->
                        val cal = java.util.Calendar.getInstance()
                        completed > cal.timeInMillis - 86400000
                    } ?: false
                }
                StatsBadge(label = "Pending", count = pendingCount, color = MaterialTheme.colorScheme.secondary)
                StatsBadge(label = "Active", count = confirmedCount + inProgressCount, color = MaterialTheme.colorScheme.primary)
                StatsBadge(label = "Done Today", count = todayCompleted, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        // Filter Chips
        PrimaryScrollableTabRow(
            selectedTabIndex = when (filter) {
                "all" -> 0
                "pending" -> 1
                "confirmed" -> 2
                "in_progress" -> 3
                "completed" -> 4
                else -> 0
            },
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 16.dp,
            divider = {}
        ) {
            val filterLabels = listOf("All", "Pending", "Confirmed", "In Progress", "Completed")
            val filterIcons = listOf(
                Icons.Default.AllInclusive,
                Icons.Default.HourglassEmpty,
                Icons.Default.CheckCircle,
                Icons.Default.Build,
                Icons.Default.DoneAll
            )
            filterLabels.forEachIndexed { index, label ->
                val isSelected = when (filter) {
                    "all" -> index == 0
                    "pending" -> index == 1
                    "confirmed" -> index == 2
                    "in_progress" -> index == 3
                    "completed" -> index == 4
                    else -> false
                }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        filter = when (index) {
                            0 -> "all"
                            1 -> "pending"
                            2 -> "confirmed"
                            3 -> "in_progress"
                            4 -> "completed"
                            else -> "all"
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = filterIcons[index],
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        // Bookings List
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!allBookingsLoaded) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (filteredBookings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No bookings found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Bookings in this category will appear here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredBookings,
                    key = { it.bookingId }
                ) { booking ->
                    OwnerBookingCard(
                        booking = booking,
                        customerName = customerNames[booking.userId] ?: "",
                        queuePosition = queuePositions[booking.bookingId] ?: 0,
                        onNoShow = { ownerViewModel.markNoShow(booking.bookingId) },
                        onApprove = {
                            ownerViewModel.updateBookingStatus(booking.bookingId, BookingStatus.CONFIRMED)
                        },
                        onReject = {
                            ownerViewModel.updateBookingStatus(booking.bookingId, BookingStatus.CANCELLED)
                        },
                        onStart = {
                            ownerViewModel.updateBookingStatus(booking.bookingId, BookingStatus.IN_PROGRESS)
                        },
                        onComplete = {
                            ownerViewModel.updateBookingStatus(booking.bookingId, BookingStatus.COMPLETED)
                        }
                    )
                }
            }
        }
    }
}

// Owner Booking Card
@Composable
fun OwnerBookingCard(
    booking: Booking,
    customerName: String = "",
    queuePosition: Int = 0,
    onNoShow: () -> Unit = {},
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onStart: () -> Unit,
    onComplete: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    var showApproveDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    var showNoShowDialog by remember { mutableStateOf(false) }
    var showCancelConfirmedDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runAction(action: () -> Unit) {
        scope.launch {
            isProcessing = true
            action()
            kotlinx.coroutines.delay(2000)
            isProcessing = false
        }
    }

    if (showApproveDialog) {
        ConfirmDialog(
            title = "Approve Booking",
            text = "Are you sure you want to approve this booking? The customer will be notified.",
            confirmLabel = "Yes, Approve",
            onConfirm = { showApproveDialog = false; runAction(onApprove) },
            onDismiss = { showApproveDialog = false }
        )
    }

    if (showRejectDialog) {
        ConfirmDialog(
            title = "Reject Booking",
            text = "Are you sure you want to reject this booking? This action cannot be undone.",
            confirmLabel = "Yes, Reject",
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = { showRejectDialog = false; runAction(onReject) },
            onDismiss = { showRejectDialog = false }
        )
    }

    if (showStartDialog) {
        ConfirmDialog(
            title = "Start Service",
            text = "Are you sure you want to start this service? The customer will be notified that their car is now being worked on.",
            confirmLabel = "Yes, Start",
            onConfirm = { showStartDialog = false; runAction(onStart) },
            onDismiss = { showStartDialog = false }
        )
    }

    if (showCompleteDialog) {
        ConfirmDialog(
            title = "Complete Booking",
            text = "Are you sure you want to mark this booking as completed? Please ensure the service is fully done.",
            confirmLabel = "Yes, Complete",
            onConfirm = { showCompleteDialog = false; runAction(onComplete) },
            onDismiss = { showCompleteDialog = false }
        )
    }

    if (showNoShowDialog) {
        ConfirmDialog(
            title = "Mark as No Show",
            text = "The customer didn't arrive for their ${booking.timeSlot} slot. This will cancel the booking and notify the customer.",
            confirmLabel = "Mark No Show",
            confirmColor = MaterialTheme.colorScheme.error,
            dismissLabel = "Go Back",
            onConfirm = { showNoShowDialog = false; runAction(onNoShow) },
            onDismiss = { showNoShowDialog = false }
        )
    }

    if (showCancelConfirmedDialog) {
        ConfirmDialog(
            title = "Cancel Booking",
            text = "Cancel this confirmed booking? The customer will be notified.",
            confirmLabel = "Cancel Booking",
            confirmColor = MaterialTheme.colorScheme.error,
            dismissLabel = "Go Back",
            onConfirm = { showCancelConfirmedDialog = false; runAction(onReject) },
            onDismiss = { showCancelConfirmedDialog = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (booking.status) {
                BookingStatus.PENDING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                BookingStatus.CANCELLED -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Booking #${booking.bookingId.takeLast(6).uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = booking.displayServiceNames(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Surface(
                    color = when (booking.status) {
                        BookingStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                        BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer
                        BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer
                        BookingStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = when (booking.status) {
                        BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                        BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.onPrimaryContainer
                        BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onTertiaryContainer
                        BookingStatus.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = booking.status.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Customer name + queue position
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = customerName.ifEmpty { "Unknown Customer" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (queuePosition > 0 && booking.status != BookingStatus.CANCELLED && booking.status != BookingStatus.COMPLETED) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "#$queuePosition",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Booked at ${DateUtils.formatDateTime(booking.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            // Countdown for PENDING / No-show time for CONFIRMED
            if (booking.status == BookingStatus.PENDING) {
                val countdownText = remember { mutableStateOf("") }
                val countdownEnd = booking.createdAt + 2 * 60 * 60 * 1000L
                LaunchedEffect(countdownEnd) {
                    while (countdownEnd > System.currentTimeMillis()) {
                        val diff = countdownEnd - System.currentTimeMillis()
                        val totalMin = (diff / 60000).toInt()
                        countdownText.value = if (totalMin > 0) {
                            val h = totalMin / 60
                            val m = totalMin % 60
                            "Expires in ${h}h ${m}m"
                        } else "Expiring soon..."
                        kotlinx.coroutines.delay(1000)
                    }
                    countdownText.value = "Expired"
                }
                if (countdownText.value.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = countdownText.value,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else if (booking.status == BookingStatus.CONFIRMED) {
                // Show the no-show available time (slot + 30 min) as a static time
                val noShowTime = remember(booking) {
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
                        DateUtils.formatTime(cal.timeInMillis)
                    } catch (e: Exception) { "" }
                }
                if (noShowTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No-show available at $noShowTime",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = booking.carDetails,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${DateUtils.formatDate(booking.bookingDate)} at ${booking.timeSlot}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "Total Price",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "₱${booking.price}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                    )
                }
            }
            if (booking.status == BookingStatus.PENDING) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showApproveDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve")
                        }
                    }
                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.error)
                        } else {
                            Icon(Icons.Default.Close, contentDescription = "Reject", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reject")
                        }
                    }
                }
            }
            if (booking.status == BookingStatus.CONFIRMED) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showStartDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSecondary)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Service", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Service")
                        }
                    }
                    OutlinedButton(
                        onClick = { showCancelConfirmedDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel")
                    }
                    // Only show "No Show" if the time slot has passed (+ 30 min grace period)
                    val slotPast = remember(booking) {
                        try {
                            val parts = booking.timeSlot.split(" ")
                            val timeParts = parts[0].split(":")
                            var h = timeParts[0].toInt()
                            val m = timeParts[1].toInt()
                            if (parts[1] == "PM" && h != 12) h += 12
                            if (parts[1] == "AM" && h == 12) h = 0
                            val cal = java.util.Calendar.getInstance().apply {
                                timeInMillis = booking.bookingDate
                                set(java.util.Calendar.HOUR_OF_DAY, h)
                                set(java.util.Calendar.MINUTE, m)
                                set(java.util.Calendar.SECOND, 0)
                                add(java.util.Calendar.MINUTE, 30) // 30 min grace period
                            }
                            cal.timeInMillis < System.currentTimeMillis()
                        } catch (e: Exception) { false }
                    }
                    if (slotPast) {
                        OutlinedButton(
                            onClick = { showNoShowDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.PersonOff, contentDescription = "No Show", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("No Show")
                        }
                    }
                }
            }
            if (booking.status == BookingStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCompleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.DoneAll, contentDescription = "Complete", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark as Completed")
                    }
                }
            }
        }
    }
}

@Composable
fun StatsBadge(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
