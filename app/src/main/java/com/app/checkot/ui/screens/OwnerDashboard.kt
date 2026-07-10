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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboard(
    navController: NavController,
    authViewModel: AuthViewModel,
    ownerViewModel: OwnerDashboardViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveResult by ownerViewModel.saveResult.collectAsState()

    LaunchedEffect(saveResult) {
        saveResult?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
        }
    }

    val shopCust by ownerViewModel.shopCustomization.collectAsState()
    val shopStatus = shopCust.status

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val userData by authViewModel.currentUserData.collectAsState()
            TopAppBar(
                title = { 
                    Column {
                        Text("Owner Dashboard", style = MaterialTheme.typography.titleMedium)
                        val shopCust = ownerViewModel.shopCustomization.collectAsState().value
                        if (shopCust.shopName.isNotEmpty()) {
                            Text(shopCust.shopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
            if (shopStatus != "rejected") {
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
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (shopCust.status) {
                "pending" -> {
                    // Pending banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Shop Pending Approval",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "Your shop is not yet visible to customers. Wait for admin approval.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    // Show tabs normally
                    when (selectedTab) {
                        0 -> OwnerBookingsTab(navController, ownerViewModel, PaddingValues(0.dp))
                        1 -> OwnerCustomersTab(ownerViewModel, PaddingValues(0.dp))
                        2 -> OwnerRevenueTab(ownerViewModel, PaddingValues(0.dp))
                        3 -> OwnerServicesTab(ownerViewModel, PaddingValues(0.dp))
                    }
                }
                "rejected" -> {
                    // Full-screen rejection message instead of tabs
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(100.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Cancel,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "Shop Application Not Approved",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Unfortunately, your shop \"${shopCust.shopName}\" was not approved at this time.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ContactMail,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Need help?",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "If you have questions about this decision, please contact support or re-register with updated information.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Active — normal dashboard, no banner
                    when (selectedTab) {
                        0 -> OwnerBookingsTab(navController, ownerViewModel, PaddingValues(0.dp))
                        1 -> OwnerCustomersTab(ownerViewModel, PaddingValues(0.dp))
                        2 -> OwnerRevenueTab(ownerViewModel, PaddingValues(0.dp))
                        3 -> OwnerServicesTab(ownerViewModel, PaddingValues(0.dp))
                    }
                }
            }
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
                        ownerViewModel.logout {
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
    ownerViewModel: OwnerDashboardViewModel,
    paddingValues: PaddingValues
) {
    val allBookings by ownerViewModel.allBookings.collectAsState()
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
        ScrollableTabRow(
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
            if (filteredBookings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
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
// Customers Tab
@Composable
fun OwnerCustomersTab(
    ownerViewModel: OwnerDashboardViewModel,
    paddingValues: PaddingValues
) {
    val allUsers by ownerViewModel.allUsers.collectAsState()
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
                items(
                    items = filteredUsers,
                    key = { it.userId }
                ) { user ->
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
fun OwnerRevenueTab(ownerViewModel: OwnerDashboardViewModel, paddingValues: PaddingValues) {
    val allBookings by ownerViewModel.allBookings.collectAsState()
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
            items(
                items = filteredBookings.take(10),
                key = { it.bookingId }
            ) { booking ->
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
                Text(text = booking.displayServiceNames(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

    if (showApproveDialog) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false },
            title = { Text("Approve Booking") },
            text = { Text("Are you sure you want to approve this booking? The customer will be notified.") },
            confirmButton = {
                TextButton(onClick = {
                    showApproveDialog = false
                    scope.launch {
                        isProcessing = true
                        onApprove()
                        kotlinx.coroutines.delay(2000)
                        isProcessing = false
                    }
                }) { Text("Yes, Approve") }
            },
            dismissButton = {
                TextButton(onClick = { showApproveDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject Booking") },
            text = { Text("Are you sure you want to reject this booking? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showRejectDialog = false
                    scope.launch {
                        isProcessing = true
                        onReject()
                        kotlinx.coroutines.delay(2000)
                        isProcessing = false
                    }
                }) { Text("Yes, Reject", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = { Text("Start Service") },
            text = { Text("Are you sure you want to start this service? The customer will be notified that their car is now being worked on.") },
            confirmButton = {
                TextButton(onClick = {
                    showStartDialog = false
                    scope.launch {
                        isProcessing = true
                        onStart()
                        kotlinx.coroutines.delay(2000)
                        isProcessing = false
                    }
                }) { Text("Yes, Start") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Complete Booking") },
            text = { Text("Are you sure you want to mark this booking as completed? Please ensure the service is fully done.") },
            confirmButton = {
                TextButton(onClick = {
                    showCompleteDialog = false
                    scope.launch {
                        isProcessing = true
                        onComplete()
                        kotlinx.coroutines.delay(2000)
                        isProcessing = false
                    }
                }) { Text("Yes, Complete") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNoShowDialog) {
        AlertDialog(
            onDismissRequest = { showNoShowDialog = false },
            title = { Text("Mark as No Show") },
            text = { Text("The customer didn't arrive for their ${booking.timeSlot} slot. This will cancel the booking and notify the customer.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoShowDialog = false
                    scope.launch {
                        isProcessing = true
                        onNoShow()
                        kotlinx.coroutines.delay(2000)
                        isProcessing = false
                    }
                }) { Text("Mark No Show", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showNoShowDialog = false }) { Text("Go Back") }
            }
        )
    }

    if (showCancelConfirmedDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmedDialog = false },
            title = { Text("Cancel Booking") },
            text = { Text("Cancel this confirmed booking? The customer will be notified.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirmedDialog = false
                    scope.launch {
                        isProcessing = true
                        onReject()
                        kotlinx.coroutines.delay(2000)
                        isProcessing = false
                    }
                }) { Text("Cancel Booking", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmedDialog = false }) { Text("Go Back") }
            }
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onTertiary)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerServicesTab(
    ownerViewModel: OwnerDashboardViewModel,
    paddingValues: PaddingValues
) {
    val customization by ownerViewModel.shopCustomization.collectAsState()
    val allBookings by ownerViewModel.allBookings.collectAsState()
    var editedServices by remember { mutableStateOf<List<CustomServiceConfig>>(customization.services) }
    var bayCountText by remember { mutableStateOf(customization.bayCount.toString()) }
    var showAddDropdown by remember { mutableStateOf(false) }
    var showCustomNameDialog by remember { mutableStateOf(false) }
    var customServiceNameInput by remember { mutableStateOf("") }
    var isSavingServices by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val maxServices = 15

    // A price is invalid if below 150, above 5000, or 0.0 for custom services (no default)
    val hasInvalidPrice = editedServices.any { config ->
        (config.customPrice > 0.0 && config.customPrice < 150) || 
        config.customPrice > 5000 ||
        (config.isCustom && config.customPrice == 0.0)
    }
    val bayCountChanged = bayCountText.toIntOrNull() != customization.bayCount
    val canSave = (editedServices != customization.services || bayCountChanged) && !hasInvalidPrice

    LaunchedEffect(customization) {
        editedServices = customization.services
        bayCountText = customization.bayCount.toString()
    }

    val atMaxLimit = editedServices.size >= maxServices
    val availableTypesToAdd = ServiceType.values().filter { type ->
        type != ServiceType.CUSTOM && editedServices.none { it.serviceName == type.name }
    }

    if (showCustomNameDialog) {
        AlertDialog(
            onDismissRequest = { showCustomNameDialog = false },
            title = { Text("Custom Service Name") },
            text = {
                OutlinedTextField(
                    value = customServiceNameInput,
                    onValueChange = { if (it.length <= 30) customServiceNameInput = it },
                    label = { Text("Service name") },
                    placeholder = { Text("e.g. Headlight Polish") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = customServiceNameInput.trim()
                        if (name.isNotEmpty()) {
                            val id = "custom_${System.currentTimeMillis()}"
                            editedServices = editedServices + CustomServiceConfig(
                                serviceName = id,
                                displayName = name,
                                customName = name,
                                customPrice = 0.0,
                                isCustom = true
                            )
                            customServiceNameInput = ""
                            showCustomNameDialog = false
                        }
                    },
                    enabled = customServiceNameInput.trim().isNotEmpty()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Manage Services", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${editedServices.size}/$maxServices services",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (atMaxLimit) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Garage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Service Bays:", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val current = bayCountText.toIntOrNull() ?: 1
                            if (current > 1) {
                                bayCountText = (current - 1).toString()
                            }
                        },
                        modifier = Modifier.size(28.dp),
                        enabled = (bayCountText.toIntOrNull() ?: 1) > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = bayCountText,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = {
                            val current = bayCountText.toIntOrNull() ?: 1
                            if (current < 10) {
                                bayCountText = (current + 1).toString()
                            }
                        },
                        modifier = Modifier.size(28.dp),
                        enabled = (bayCountText.toIntOrNull() ?: 1) < 10
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Box {
                OutlinedButton(
                    onClick = { showAddDropdown = true },
                    enabled = !atMaxLimit,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Service", style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(
                    expanded = showAddDropdown,
                    onDismissRequest = { showAddDropdown = false }
                ) {
                    availableTypesToAdd.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                editedServices = editedServices + CustomServiceConfig(
                                    serviceName = type.name,
                                    displayName = type.displayName,
                                    customPrice = type.price
                                )
                                showAddDropdown = false
                            }
                        )
                    }
                    if (availableTypesToAdd.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Others (Custom Service)")
                            }
                        },
                        onClick = {
                            showAddDropdown = false
                            customServiceNameInput = ""
                            showCustomNameDialog = true
                        }
                    )
                }
            }
        }

        if (editedServices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No services configured",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap \"Add Service\" to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = editedServices,
                    key = { it.serviceName }
                ) { config ->
                    val isInUse = allBookings.any { booking ->
                        val status = booking.status
                        val isActive = status == BookingStatus.PENDING
                            || status == BookingStatus.CONFIRMED
                            || status == BookingStatus.IN_PROGRESS
                        if (!isActive) return@any false
                        // Check if this booking uses the service being deleted
                        if (config.isCustom) {
                            // Custom service: check customServiceNames
                            config.customName.isNotEmpty() && booking.customServiceNames.contains(config.customName)
                        } else {
                            // Predefined service: check ServiceType list
                            booking.services.any { it.name == config.serviceName }
                        }
                    }
                    ServiceConfigCard(
                        config = config,
                        canDelete = !isInUse,
                        deleteReason = if (isInUse) "Cannot delete — service has active bookings" else null,
                        onPriceChange = { newPrice ->
                            editedServices = editedServices.map {
                                if (it.serviceName == config.serviceName) it.copy(customPrice = newPrice) else it
                            }
                        },
                        onNameChange = { newName ->
                            editedServices = editedServices.map {
                                if (it.serviceName == config.serviceName) it.copy(customName = newName, displayName = newName) else it
                            }
                        },
                        onDelete = {
                            editedServices = editedServices.filter { it.serviceName != config.serviceName }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    editedServices = customization.services
                },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Reset")
            }
            Button(
                onClick = {
                    isSavingServices = true
                    val bayCount = bayCountText.toIntOrNull() ?: customization.bayCount
                    val updated = customization.copy(
                        services = editedServices,
                        bayCount = bayCount
                    )
                    ownerViewModel.saveShopCustomization(updated)
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        isSavingServices = false
                    }
                },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                enabled = canSave && !isSavingServices
            ) {
                if (isSavingServices) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Changes")
            }
        }
    }
}

@Composable
fun ServiceConfigCard(
    config: CustomServiceConfig,
    canDelete: Boolean = true,
    deleteReason: String? = null,
    onPriceChange: (Double) -> Unit,
    onNameChange: (String) -> Unit = {},
    onDelete: () -> Unit
) {
    val defaultPrice = ServiceType.values().find { it.name == config.serviceName }?.price ?: 0.0
    var priceText by remember(config.customPrice) {
        mutableStateOf(if (config.customPrice > 0) config.customPrice.toString() else "")
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Service") },
            text = {
                Text("Removing \"${config.displayName}\" will delete this service from your shop's service list. Clients will no longer see it.\n\nAre you sure you want to proceed?")
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Yes, Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (config.isCustom) {
                        OutlinedTextField(
                            value = config.customName,
                            onValueChange = { if (it.length <= 30) onNameChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium,
                            placeholder = { Text("Service name") },
                            shape = MaterialTheme.shapes.small
                        )
                    } else {
                        Text(
                            text = config.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = if (config.isCustom) "Custom service" else "Default: ₱${defaultPrice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                IconButton(
                    onClick = { if (canDelete) showDeleteConfirm = true },
                    enabled = canDelete
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (canDelete) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }
            }

            if (!canDelete && deleteReason != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = deleteReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Your Price: ₱",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            val parts = filtered.split(".")
                            val limited = if (parts.size == 2 && parts[1].length > 2) {
                                "${parts[0]}.${parts[1].take(2)}"
                            } else filtered
                            priceText = limited
                            val parsed = limited.toDoubleOrNull()
                            if (parsed != null && parsed >= 150 && parsed <= 5000) {
                                onPriceChange(parsed)
                            }
                            // When empty or invalid: don't call onPriceChange,
                            // keep the previous valid customPrice.
                            // The red error state will show below.
                        }
                    },
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    singleLine = true,
                    isError = priceText.isNotEmpty() && (priceText.toDoubleOrNull() == null
                        || priceText.toDoubleOrNull()!! < 150
                        || priceText.toDoubleOrNull()!! > 5000),
                    placeholder = { Text("${defaultPrice}") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.small
                )
            }
            val price = priceText.toDoubleOrNull()
            // Show error when: field is non-empty with invalid value,
            // or custom service with empty/0 price
            val showError = priceText.isNotEmpty() || (config.isCustom && priceText.isEmpty())
            if (showError) {
                when {
                    price == null || price < 150 -> Text(
                        "Minimum price is ₱150.00",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                    price > 5000 -> Text(
                        "Maximum price is ₱5,000.00",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }
        }
    }
}
