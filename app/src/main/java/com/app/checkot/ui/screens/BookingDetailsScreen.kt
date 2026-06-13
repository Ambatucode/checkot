package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import com.app.checkot.ui.screens.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailsScreen(
    bookingId: String?,
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val bookings by authViewModel.userBookings.collectAsState()
    val booking = bookings.find { it.bookingId == bookingId }
    val scope = rememberCoroutineScope()
    var isCancelling by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val queuePosition by remember(booking) {
        if (booking != null) {
            authViewModel.getQueuePositionRealTime(booking)
        } else {
            kotlinx.coroutines.flow.flowOf(-1)
        }
    }.collectAsState(initial = -1)

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
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Booking") },
            text = { Text("Are you sure you want to cancel this booking? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isCancelling = true
                            authViewModel.cancelBooking(booking.bookingId)
                            isCancelling = false
                            showCancelDialog = false
                            navController.popBackStack()
                        }
                    }
                ) {
                    Text("Yes, Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("No")
                }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Booking Details") },
                navigationIcon = {
                    IconButton(onClick = { if(navController.previousBackStackEntry != null) navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
                                text = booking.status.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (booking.status) {
                                    BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                                    BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.onPrimaryContainer
                                    BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onTertiaryContainer
                                    BookingStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    BookingStatus.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            if (queuePosition > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Queue Position: #$queuePosition (${queuePosition - 1} ahead)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = when (booking.status) {
                                        BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                                        BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.onPrimaryContainer
                                        BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
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
            item {
                ServiceProgressStepper(status = booking.status)
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
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        val shopName = partnerShops.find { it.shopId == booking.shopId }?.name ?: "Unknown Shop"
                        DetailRow("Shop", shopName)
                        DetailRow("Services", booking.services.joinToString(", ") { it.displayName })
                        DetailRow("Duration", booking.services.map { it.duration }.distinct().joinToString(" + "))
                        DetailRow("Price", "₱${booking.price}")
                        if (booking.notes.isNotBlank()) {
                             DetailRow("Special Requests", booking.notes)
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
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        val carDetails = booking.carDetails.split(" - ")
                        if (carDetails.size == 2) {
                            DetailRow("Car", carDetails[0])
                            DetailRow("Plate Number", carDetails[1])
                        } else {
                            DetailRow("Car", booking.carDetails)
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
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Date", DateUtils.formatDate(booking.bookingDate))
                        DetailRow("Time", booking.timeSlot)
                        DetailRow("Booked on", DateUtils.formatDate(booking.createdAt))
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
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                steps.forEachIndexed { index, label ->
                    val isCompleted = index < currentStepIndex
                    val isActive = index == currentStepIndex
                    val color = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, shape = CircleShape),
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
                                    color = if (isActive) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else null
                        )
                    }

                    if (index < steps.lastIndex) {
                        val lineColor = if (index < currentStepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        Divider(
                            modifier = Modifier
                                .weight(0.5f)
                                .padding(bottom = 16.dp),
                            color = lineColor,
                            thickness = 2.dp
                        )
                    }
                }
            }
        }
    }
}
