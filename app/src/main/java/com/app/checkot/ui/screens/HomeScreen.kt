package com.app.checkot.ui.screens

import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.app.checkot.utils.BookingUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    bookingViewModel: BookingViewModel = viewModel()
) {
    val userData by authViewModel.currentUserData.collectAsState()
    val recentBookings by bookingViewModel.userBookings.collectAsState()

    // Load shops from Firestore
    var shopList by remember { mutableStateOf<List<CarWashShop>>(emptyList()) }
    var loadingShops by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = Firebase.firestore.collection("shop_services").get().await()
                val shops = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("shopName") ?: return@mapNotNull null
                    val address = doc.getString("shopAddress") ?: ""
                    val status = doc.getString("status") ?: "active"
                    // Only show active shops (pending/rejected are hidden from customers)
                    if (status != "active") return@mapNotNull null
                    CarWashShop(shopId = doc.id, name = name, address = address)
                }
                withContext(Dispatchers.Main) {
                    shopList = shops
                    loadingShops = false
                }
            } catch (e: Exception) {
                println("❌ Failed to load shops: ${e.message}")
                withContext(Dispatchers.Main) {
                    loadError = "Could not load shops. Check your connection."
                    loadingShops = false
                }
            }
        }
    }

    // Build a map of shopId -> shop name for quick lookup in BookingCard
    val shopNameMap = remember(shopList) {
        shopList.associate { it.shopId to it.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkot") },
                actions = {
                    IconButton(onClick = { navController.navigate("profile") }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("bookings") },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = "Bookings") },
                    label = { Text("Bookings") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("cars") },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Cars") },
                    label = { Text("My Cars") }
                )
            }
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = userData?.fullName ?: "Guest",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ready to get your car sparkling clean?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Quick action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { navController.navigate("my_bookings") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("My Bookings", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { navController.navigate("cars") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("My Cars", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select a Car Wash",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${shopList.size} shops",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (loadingShops) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            if (loadingShops) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (loadError != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(loadError!!, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            items(shopList, key = { it.shopId }) { shop ->
                ShopCard(
                    shop = shop,
                    onClick = {
                        navController.navigate("book_service/${shop.shopId}")
                    }
                )
            }

            if (shopList.isEmpty() && !loadingShops) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No car wash shops available yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            if (recentBookings.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Bookings",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                items(recentBookings.take(3), key = { it.bookingId }) { booking ->
                    BookingCard(
                        booking = booking,
                        onClick = { navController.navigate("booking_details/${booking.bookingId}") },
                        shopName = shopNameMap[booking.shopId] ?: "Shop"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCard(
    shop: CarWashShop,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LocalCarWash,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shop.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = shop.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Book",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingCard(
    booking: Booking,
    onClick: () -> Unit,
    // Resolved shop name instead of a Map param: Map is an unstable type in
    // Compose and made every card recompose whenever the parent did.
    shopName: String = "Shop",
    bookingViewModel: BookingViewModel = viewModel()
) {
    var queueInfo by remember { mutableStateOf(QueueInfo()) }

    // Direct Firestore listener — more reliable than callbackFlow
    DisposableEffect(booking.bookingId, booking.shopId, booking.bookingDate) {
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: shop name + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                BookingStatusBadge(status = booking.status)
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Service name
            Text(
                text = booking.displayServiceNames(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Car + Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = booking.carDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = booking.timeSlot,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
            // Queue info — always show for active bookings
            if (booking.status == BookingStatus.PENDING || booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (queueInfo.position > 0) {
                                val carsAhead = queueInfo.position - 1
                                if (carsAhead == 0) "Queue: #${queueInfo.position} — You're next!"
                                else "Queue: #${queueInfo.position} — $carsAhead ahead"
                            } else {
                                "Loading queue position..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            }
            // Countdown timer for pending/confirmed bookings
            val countdownText = remember { mutableStateOf("") }
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
            LaunchedEffect(countdownEnd) {
                while (countdownEnd > 0 && countdownEnd > System.currentTimeMillis()) {
                    val diff = countdownEnd - System.currentTimeMillis()
                    val totalMin = (diff / 60000).toInt()
                    if (totalMin > 0) {
                        val h = totalMin / 60
                        val m = totalMin % 60
                        countdownText.value = when (booking.status) {
                            BookingStatus.PENDING -> if (h > 0) "Auto-cancels in ${h}h ${m}m" else "Auto-cancels in ${m}m"
                            BookingStatus.CONFIRMED -> if (h > 0) "Arrive within ${h}h ${m}m" else "Arrive within ${m}m"
                            else -> ""
                        }
                    } else {
                        countdownText.value = when (booking.status) {
                            BookingStatus.PENDING -> "Cancelling soon..."
                            BookingStatus.CONFIRMED -> "Almost expired!"
                            else -> ""
                        }
                    }
                    kotlinx.coroutines.delay(1000)
                }
                countdownText.value = when (booking.status) {
                    BookingStatus.PENDING -> "Booking expired"
                    BookingStatus.CONFIRMED -> "Time expired"
                    else -> ""
                }
            }
            if (countdownText.value.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = countdownText.value,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (booking.status) {
                        BookingStatus.PENDING -> MaterialTheme.colorScheme.secondary
                        BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                )
            }
            // Price row
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "₱${booking.price}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BookingStatusBadge(status: BookingStatus) {
    val (backgroundColor, textColor) = when (status) {
        BookingStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BookingStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        BookingStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
