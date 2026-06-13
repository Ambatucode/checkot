package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import com.app.checkot.ui.screens.*
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboard(
    navController: NavController,
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            val userData by authViewModel.currentUserData.collectAsState()
            TopAppBar(
                title = { 
                    Column {
                        Text("Owner Dashboard", style = MaterialTheme.typography.titleMedium)
                        val shopName = partnerShops.find { it.shopId == userData?.ownedShopId }?.name
                        if (shopName != null) {
                            Text(shopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLogoutDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = "Bookings") },
                    label = { Text("Bookings") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.People, contentDescription = "Customers") },
                    label = { Text("Customers") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AttachMoney, contentDescription = "Revenue") },
                    label = { Text("Revenue") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Services") },
                    label = { Text("Services") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> OwnerBookingsTab(navController, adminViewModel, paddingValues)
            1 -> OwnerCustomersTab(adminViewModel, paddingValues)
            2 -> OwnerRevenueTab(adminViewModel, paddingValues)
            3 -> Text("Services Tab - Coming Soon", modifier = Modifier.padding(paddingValues))
        }
    }
    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.signOut()
                        adminViewModel.logout {
                            showLogoutDialog = false
                            navController.navigate("login") {
                                popUpTo(0) // Clears all screens from back stack
                            }
                        }
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("No")
                }
            }
        )
    }
}
// FIXED BOOKINGS TAB
@Composable
fun OwnerBookingsTab(
    navController: NavController,
    adminViewModel: AdminViewModel,
    paddingValues: PaddingValues
) {
    val allBookings by adminViewModel.allBookings.collectAsState()
    var filter by remember { mutableStateOf("all") }
    // FORCE REFRESH WHEN TAB OPENS
    LaunchedEffect(Unit) {
        adminViewModel.forceRefresh()
    }
    val filteredBookings = when (filter) {
        "pending" -> allBookings.filter { it.status == BookingStatus.PENDING }
        "confirmed" -> allBookings.filter { it.status == BookingStatus.CONFIRMED }
        "completed" -> allBookings.filter { it.status == BookingStatus.COMPLETED }
        else -> allBookings
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Filter Chips
        ScrollableTabRow(
            selectedTabIndex = when (filter) {
                "all" -> 0
                "pending" -> 1
                "confirmed" -> 2
                "completed" -> 3
                else -> 0
            },
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 16.dp
        ) {
            listOf("All", "Pending", "Confirmed", "Completed").forEachIndexed { index, label ->
                FilterChip(
                    selected = when (filter) {
                        "all" -> index == 0
                        "pending" -> index == 1
                        "confirmed" -> index == 2
                        "completed" -> index == 3
                        else -> false
                    },
                    onClick = {
                        filter = when (index) {
                            0 -> "all"
                            1 -> "pending"
                            2 -> "confirmed"
                            3 -> "completed"
                            else -> "all"
                        }
                    },
                    label = { Text(label) },
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
            if (filteredBookings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No bookings found")
                    }
                }
            } else {
                items(filteredBookings) { booking ->
                    OwnerBookingCard(
                        booking = booking,
                        onApprove = {
                            adminViewModel.updateBookingStatus(booking.bookingId, BookingStatus.CONFIRMED)
                        },
                        onReject = {
                            adminViewModel.updateBookingStatus(booking.bookingId, BookingStatus.CANCELLED)
                        },
                        onComplete = {
                            adminViewModel.updateBookingStatus(booking.bookingId, BookingStatus.COMPLETED)
                        }
                    )
                }
            }
        }
    }
}
// Customers Tab
@Composable
fun OwnerCustomersTab(
    adminViewModel: AdminViewModel,
    paddingValues: PaddingValues
) {
    val allUsers by adminViewModel.allUsers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = if (searchQuery.isEmpty()) {
        allUsers
    } else {
        allUsers.filter {
            it.fullName.contains(searchQuery, ignoreCase = true) ||
                    it.email.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery, ignoreCase = true)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search customers...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            singleLine = true
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = allUsers.size.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("Total Customers")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = allUsers.count { it.createdAt > System.currentTimeMillis() - 7 * 86400000 }.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("This Week")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredUsers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No customers found")
                    }
                }
            } else {
                items(filteredUsers) { user ->
                    CustomerCard(user = user)
                }
            }
        }
    }
}
@Composable
fun CustomerCard(user: CarWashUser) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = user.fullName.first().uppercase(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.fullName, style = MaterialTheme.typography.titleMedium)
                Text(text = user.email, style = MaterialTheme.typography.bodyMedium)
                Text(text = user.phoneNumber, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text(text = "${user.savedCars.size} cars", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
// Revenue Tab
@Composable
fun OwnerRevenueTab(adminViewModel: AdminViewModel, paddingValues: PaddingValues) {
    val allBookings by adminViewModel.allBookings.collectAsState()
    var selectedPeriod by remember { mutableStateOf("today") }
    val now = System.currentTimeMillis()
    val oneDay = 86400000
    val oneWeek = oneDay * 7
    val oneMonth = oneDay * 30
    val filteredBookings = when (selectedPeriod) {
        "today" -> allBookings.filter { it.createdAt > now - oneDay && it.status == BookingStatus.COMPLETED }
        "week" -> allBookings.filter { it.createdAt > now - oneWeek && it.status == BookingStatus.COMPLETED }
        "month" -> allBookings.filter { it.createdAt > now - oneMonth && it.status == BookingStatus.COMPLETED }
        else -> allBookings.filter { it.status == BookingStatus.COMPLETED }
    }
    val totalRevenue = filteredBookings.sumOf { it.price }
    val bookingCount = filteredBookings.size
    val averagePerBooking = if (bookingCount > 0) totalRevenue / bookingCount else 0.0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf("Today", "Week", "Month").forEachIndexed { index, period ->
                FilterChip(
                    selected = when (selectedPeriod) {
                        "today" -> index == 0
                        "week" -> index == 1
                        "month" -> index == 2
                        else -> false
                    },
                    onClick = { selectedPeriod = when (index) { 0 -> "today"; 1 -> "week"; 2 -> "month"; else -> "today" } },
                    label = { Text(period) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Total Revenue", style = MaterialTheme.typography.titleLarge)
                Text(text = "₱${String.format("%,.2f", totalRevenue)}", style = MaterialTheme.typography.displaySmall)
                Text(text = "${bookingCount} completed bookings", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShowChart, contentDescription = null)
                    Text(text = "₱${String.format("%,.0f", averagePerBooking)}", style = MaterialTheme.typography.headlineSmall)
                    Text(text = "Avg/Booking", style = MaterialTheme.typography.bodySmall)
                }
            }
            val pendingRevenue = allBookings.filter { it.status == BookingStatus.PENDING || it.status == BookingStatus.CONFIRMED }.sumOf { it.price }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null)
                    Text(text = "₱${String.format("%,.0f", pendingRevenue)}", style = MaterialTheme.typography.headlineSmall)
                    Text(text = "Pending", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Recent Transactions", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filteredBookings.take(10)) { booking ->
                TransactionItem(booking = booking)
            }
            if (filteredBookings.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No completed bookings for this period")
                    }
                }
            }
        }
    }
}
@Composable
fun TransactionItem(booking: Booking) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = booking.services.joinToString(", ") { it.displayName }, style = MaterialTheme.typography.titleMedium)
                Text(text = DateUtils.formatDate(booking.createdAt), style = MaterialTheme.typography.bodySmall)
            }
            Text(text = "₱${booking.price}", style = MaterialTheme.typography.titleLarge)
        }
    }
}
// Owner Booking Card
@Composable
fun OwnerBookingCard(
    booking: Booking,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onComplete: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (booking.status) {
                BookingStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer
                BookingStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
                BookingStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(text = "Booking #${booking.bookingId.takeLast(6)}", style = MaterialTheme.typography.labelSmall)
                    Text(text = booking.services.joinToString(", ") { it.displayName }, style = MaterialTheme.typography.titleMedium)
                }
                Surface(
                    color = when (booking.status) {
                        BookingStatus.PENDING -> MaterialTheme.colorScheme.secondary
                        BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
                        BookingStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                        BookingStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(text = booking.status.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(booking.carDetails, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${DateUtils.formatDate(booking.bookingDate)} at ${booking.timeSlot}")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(text = "₱${booking.price}", style = MaterialTheme.typography.titleLarge)
            }
            if (booking.status == BookingStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            scope.launch {
                                isProcessing = true
                                onApprove()
                                kotlinx.coroutines.delay(2000)
                                isProcessing = false
                            }
                        }, 
                        modifier = Modifier.weight(1f), 
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve")
                        }
                    }
                    Button(
                        onClick = { 
                            scope.launch {
                                isProcessing = true
                                onReject()
                                kotlinx.coroutines.delay(2000)
                                isProcessing = false
                            }
                        }, 
                        modifier = Modifier.weight(1f), 
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError)
                        } else {
                            Icon(Icons.Default.Close, contentDescription = "Reject", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject")
                        }
                    }
                }
            }
            if (booking.status == BookingStatus.CONFIRMED) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { 
                        scope.launch {
                            isProcessing = true
                            onComplete()
                            kotlinx.coroutines.delay(2000)
                            isProcessing = false
                        }
                    }, 
                    modifier = Modifier.fillMaxWidth(), 
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onTertiary)
                    } else {
                        Icon(Icons.Default.Done, contentDescription = "Complete", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark as Completed")
                    }
                }
            }
        }
    }
}
