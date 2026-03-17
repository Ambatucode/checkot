package com.app.misproject

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val bookings by authViewModel.userBookings.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    val pendingBookings = bookings.filter { it.status == BookingStatus.PENDING }
    val confirmedBookings = bookings.filter { it.status == BookingStatus.CONFIRMED }
    val completedBookings = bookings.filter { it.status == BookingStatus.COMPLETED }
    val cancelledBookings = bookings.filter { it.status == BookingStatus.CANCELLED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bookings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Queue Status Card (for pending/confirmed bookings)
            if (pendingBookings.isNotEmpty() || confirmedBookings.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Your Queue",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (pendingBookings.isNotEmpty()) {
                            QueueItem(
                                status = "Pending Approval",
                                count = pendingBookings.size,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (confirmedBookings.isNotEmpty()) {
                            QueueItem(
                                status = "Confirmed - Waiting for service",
                                count = confirmedBookings.size,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = 0.5f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Estimated wait time: 30-45 minutes",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("All", "Pending", "Confirmed", "Completed", "Cancelled").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val displayBookings = when (selectedTab) {
                0 -> bookings
                1 -> pendingBookings
                2 -> confirmedBookings
                3 -> completedBookings
                4 -> cancelledBookings
                else -> bookings
            }

            if (displayBookings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "No bookings in this category",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayBookings) { booking ->
                        BookingCard(
                            booking = booking,
                            onClick = {
                                navController.navigate("booking_details/${booking.bookingId}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueItem(
    status: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(8.dp),
                color = color,
                shape = CircleShape
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = status)
        }
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun QueuePositionCard(
    bookings: List<Booking>
) {
    val pendingCount = bookings.count { it.status == BookingStatus.PENDING }
    val confirmedCount = bookings.count { it.status == BookingStatus.CONFIRMED }
    val yourPosition = pendingCount + confirmedCount

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Your Queue Position",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Position circle
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (yourPosition > 0) "1" else "0",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Stats
                Column {
                    Text(
                        text = "Pending: $pendingCount",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Ahead: ${confirmedCount + pendingCount}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Est. time: ${(confirmedCount + pendingCount) * 30} min",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = if (yourPosition > 0) 0.3f else 0f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}