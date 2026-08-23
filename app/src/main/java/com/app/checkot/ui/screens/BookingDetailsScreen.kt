package com.app.checkot.ui.screens
import com.app.checkot.R
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import com.app.checkot.ui.components.AnimatedStatusIcon
import com.app.checkot.ui.components.BackTopAppBar
import com.app.checkot.ui.components.ConfirmDialog
import com.app.checkot.ui.components.DetailRow
import com.app.checkot.ui.components.ShopLocationView
import com.app.checkot.ui.components.AppButton
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
    val context = LocalContext.current
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

    // Load the shop name + map location from Firestore (same doc, one fetch)
    var shopName by remember(booking) { mutableStateOf("") }
    var shopLatitude by remember(booking) { mutableStateOf(0.0) }
    var shopLongitude by remember(booking) { mutableStateOf(0.0) }
    var shopServices by remember(booking) { mutableStateOf<List<CustomServiceConfig>>(emptyList()) }
    var shopLogo by remember(booking) { mutableStateOf<ImageBitmap?>(null) }
    // Full shop doc — used to flag bookings impacted by closures/hours changes.
    var shopCustomization by remember(booking) { mutableStateOf<ShopCustomization?>(null) }
    var showAddOnDialog by remember { mutableStateOf(false) }
    var showReceipt by remember { mutableStateOf(false) }
    LaunchedEffect(booking?.shopId) {
        val shopId = booking?.shopId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val doc = Firebase.firestore.collection("shop_services").document(shopId).get().await()
                val name = doc.getString("shopName")
                val lat = doc.getDouble("latitude") ?: 0.0
                val lng = doc.getDouble("longitude") ?: 0.0
                val customization = doc.toObject(ShopCustomization::class.java)
                val services = customization?.services ?: emptyList()
                // Logo now lives in Firebase Storage; download it (we're on IO)
                // and decode to a bitmap so the receipt can be captured with it
                // present (an async image loader wouldn't be ready at capture time).
                val logo = customization?.logoUrl?.takeIf { it.isNotEmpty() }?.let {
                    try {
                        val bytes = java.net.URL(it).readBytes()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
                withContext(Dispatchers.Main) {
                    shopName = if (!name.isNullOrEmpty()) name else shopId.takeLast(6).uppercase()
                    shopLatitude = lat
                    shopLongitude = lng
                    shopServices = services
                    shopLogo = logo
                    shopCustomization = customization
                }
            } catch (e: Exception) {
                println("Failed to load shop details: ${e.message}")
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
    // Why this booking is impacted by the shop's current schedule (closed
    // date / special hours / unavailable service), or null if it's fine.
    val impactReason = shopCustomization?.let { BookingUtils.bookingImpactReason(booking, it) }
    if (showCancelDialog) {
        ConfirmDialog(
            title = "Cancel Booking",
            text = if (impactReason != null) {
                "This booking is impacted by a shop schedule change: $impactReason. " +
                    "You can cancel it without penalty."
            } else {
                "Are you sure you want to cancel this booking? This action cannot be undone."
            },
            confirmLabel = "Yes, Cancel",
            dismissLabel = "No",
            onConfirm = {
                scope.launch {
                    isCancelling = true
                    bookingViewModel.cancelBooking(
                        booking.bookingId,
                        skipCooldown = impactReason != null
                    )
                    isCancelling = false
                    showCancelDialog = false
                    navController.popBackStack()
                }
            },
            onDismiss = { showCancelDialog = false }
        )
    }
    if (showAddOnDialog) {
        AlertDialog(
            onDismissRequest = { showAddOnDialog = false },
            title = { Text("Add an Add-on") },
            text = {
                // Exclude services already on this booking — both the original
                // services and any add-ons already added — so a client can't
                // add a duplicate of something they're already paying for.
                val bookedNames = booking.resolvedServiceNames().toSet()
                val available = shopServices.filter { config ->
                    config.displayName !in bookedNames &&
                        booking.addOns.none { it.startsWith("${config.displayName} - ") }
                }
                if (available.isEmpty()) {
                    Text("You already have all of this shop's services on this booking.")
                } else {
                    Column {
                        Text("Add an extra paid service. It's added to your total — your booked time slot doesn't change.")
                        Spacer(modifier = Modifier.height(8.dp))
                        available.forEach { config ->
                            val addOnPrice = if (config.customPrice > 0) config.customPrice
                                else (ServiceType.values().find { it.name == config.serviceName }?.price ?: 0.0)
                            TextButton(
                                onClick = {
                                    showAddOnDialog = false
                                    bookingViewModel.addBookingAddOn(
                                        booking.bookingId,
                                        "${config.displayName} - ₱$addOnPrice",
                                        addOnPrice
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(config.displayName, modifier = Modifier.weight(1f))
                                Text("₱$addOnPrice", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOnDialog = false }) { Text("Close") }
            }
        )
    }
    if (showReceipt) {
        ReceiptDialog(booking = booking, shopName = shopName, shopLogo = shopLogo) { showReceipt = false }
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
                        AnimatedStatusIcon(
                            status = booking.status,
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

            // Countdown / Arrival card
            item {
                val countdownEnd = remember(booking.bookingId) {
                    if (booking.status == BookingStatus.PENDING) {
                        booking.createdAt + 2 * 60 * 60 * 1000L
                    } else {
                        0L
                    }
                }
                val arrivalText = remember(booking.bookingId, booking.status) {
                    if (booking.status == BookingStatus.CONFIRMED) {
                        try {
                            val sdf = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
                            val dayOfWeekAndDate = sdf.format(java.util.Date(booking.bookingDate))
                            "Arrive: $dayOfWeekAndDate • ${booking.timeSlot}"
                        } catch (e: Exception) {
                            "Arrive at ${booking.timeSlot}"
                        }
                    } else {
                        ""
                    }
                }
                var countdownText by remember { mutableStateOf("") }
                LaunchedEffect(countdownEnd) {
                    if (countdownEnd > 0) {
                        while (countdownEnd > System.currentTimeMillis()) {
                            val diff = countdownEnd - System.currentTimeMillis()
                            val totalMin = (diff / 60000).toInt()
                            countdownText = if (totalMin > 0) {
                                val h = totalMin / 60
                                val m = totalMin % 60
                                if (h > 0) "Auto-cancels in ${h}h ${m}m" else "Auto-cancels in ${m}m"
                            } else {
                                "Cancelling soon..."
                            }
                            kotlinx.coroutines.delay(1000)
                        }
                    } else {
                        countdownText = ""
                    }
                }

                val displayText = if (booking.status == BookingStatus.CONFIRMED) arrivalText else countdownText

                if (displayText.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (booking.status) {
                                BookingStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                                BookingStatus.CONFIRMED -> Color(0xFF00E6C3).copy(alpha = 0.15f) // Sleek welcome teal container
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (booking.status == BookingStatus.CONFIRMED) Icons.Default.CalendarToday else Icons.Default.Timer,
                                contentDescription = null,
                                tint = when (booking.status) {
                                    BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                                    BookingStatus.CONFIRMED -> Color(0xFF00E6C3)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = displayText,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = when (booking.status) {
                                    BookingStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                                    BookingStatus.CONFIRMED -> Color(0xFF00E6C3)
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
                // Consolidated Booking Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = com.app.checkot.ui.theme.CheckotCardSurface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Booking Summary",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Service Details Section
                        DetailRow("Shop:", shopName.ifEmpty { booking.shopId.takeLast(6).uppercase() })
                        DetailRow("Services:", booking.displayServiceNames())
                        
                        val totalMin = BookingUtils.bookingDurationMinutes(booking)
                        val durationText = when {
                            totalMin >= 60 && totalMin % 60 > 0 -> "${totalMin / 60}h ${totalMin % 60}m"
                            totalMin >= 60 -> "${totalMin / 60}h"
                            else -> "$totalMin mins"
                        }
                        DetailRow("Duration:", durationText)
                        
                        val priceStr = if (booking.price % 1.0 == 0.0) booking.price.toLong().toString() else booking.price.toString()
                        DetailRow("Price:", "₱$priceStr")
                        
                        if (booking.servicedBy.isNotBlank()) {
                            DetailRow("Serviced by:", booking.servicedBy)
                        }
                        if (booking.addOns.isNotEmpty()) {
                            DetailRow("Add-ons:", booking.addOns.joinToString(", "))
                        }
                        DetailRow("Payment:", "Cash · " + if (booking.paymentStatus == "paid") "Paid" else "Unpaid")
                        
                        if (booking.paymentStatus == "paid") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "✓ The shop marked this booking as paid in cash.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showReceipt = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Receipt")
                            }
                        }
                        if (booking.notes.isNotBlank()) {
                            DetailRow("Special Requests:", booking.notes)
                        }
                        if (booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.IN_PROGRESS) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showAddOnDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add an add-on service")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Car & Schedule Section
                        val carDetails = booking.carDetails.split(" - ")
                        if (carDetails.size == 2) {
                            DetailRow("Car:", carDetails[0])
                            DetailRow("Plate:", carDetails[1])
                        } else {
                            DetailRow("Car:", booking.carDetails)
                        }
                        DetailRow("Date:", DateUtils.formatDate(booking.bookingDate))
                        DetailRow("Time:", booking.timeSlot)

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Timeline Section
                        Text("Timeline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                        if (booking.createdAt > 0) DetailRow("Booked:", DateUtils.formatDateTime(booking.createdAt))
                        booking.confirmedAt?.let { DetailRow("Confirmed:", DateUtils.formatDateTime(it)) }
                        booking.inProgressAt?.let { DetailRow("In Progress:", DateUtils.formatDateTime(it)) }
                        booking.paidAt?.let { DetailRow("Paid:", DateUtils.formatDateTime(it)) }
                        booking.completedAt?.let { DetailRow("Completed:", DateUtils.formatDateTime(it)) }
                        booking.cancelledAt?.let { DetailRow("Cancelled:", DateUtils.formatDateTime(it)) }
                        
                        // Location Action Row
                        if (booking.status != BookingStatus.CANCELLED && (shopLatitude != 0.0 || shopLongitude != 0.0)) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                onClick = {
                                    val uri = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$shopLatitude,$shopLongitude")
                                    try {
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") })
                                    } catch (e: Exception) {
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                    }
                                },
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${shopName.ifEmpty { "Car wash" }} • View Map >",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Persistent warning when the shop's schedule no longer covers
            // this booking (closed date / special hours / unavailable service).
            if (impactReason != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$impactReason. The shop may not be able to fulfil this booking — you can cancel it without penalty.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            if (booking.status == BookingStatus.PENDING || booking.status == BookingStatus.CONFIRMED) {
                item {
                    // Cancel Button
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFF6B6B).copy(alpha = 0.1f),
                            contentColor = Color(0xFFFF6B6B)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B6B)),
                        enabled = !isCancelling
                    ) {
                        if (isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFFFF6B6B)
                            )
                        } else {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel Booking")
                        }
                    }
                }
            }
            if (booking.status == BookingStatus.COMPLETED) {
                item {
                    RateShopCard(booking = booking)
                }
            }
        }
    }
}

/**
 * Lets a client rate a shop after their booking is completed. One review per
 * booking (doc id = bookingId); Firestore rules enforce that the reviewer owns
 * a COMPLETED booking for that shop, so reviews can't be faked or spammed.
 */
@Composable
private fun RateShopCard(booking: Booking) {
    val firestore = Firebase.firestore
    var existingRating by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(booking.bookingId) {
        // Best-effort: a denied read (rules not yet deployed) must not crash
        // the details screen — just treat it as not-yet-reviewed.
        try {
            val snap = firestore.collection("reviews").document(booking.bookingId).get().await()
            existingRating = snap.toObject(Review::class.java)?.rating
        } catch (e: Exception) {
            println("❌ Failed to load review for ${booking.bookingId}: ${e.message}")
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val current = existingRating
            if (current != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "You rated this shop:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    repeat(current) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Text("Enjoyed the service?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Rate this shop — reviews are verified against completed bookings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                AppButton(
                    text = "Rate this shop",
                    onClick = { showDialog = true },
                    icon = Icons.Default.Star
                )
            }
        }
    }

    if (showDialog) {
        RateShopDialog(
            booking = booking,
            onDismiss = { showDialog = false },
            onSubmitted = { rating ->
                existingRating = rating
                showDialog = false
            }
        )
    }
}

@Composable
private fun RateShopDialog(
    booking: Booking,
    onDismiss: () -> Unit,
    onSubmitted: (Int) -> Unit
) {
    val firestore = Firebase.firestore
    val scope = rememberCoroutineScope()
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Rate this shop") },
        text = {
            Column {
                Text("How was your experience?", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { i ->
                        IconButton(onClick = { rating = i }) {
                            Icon(
                                if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i stars",
                                tint = if (i <= rating) Color(0xFFFFB300)
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 200) comment = it },
                    label = { Text("Comment (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    isSubmitting = true
                    scope.launch {
                        try {
                            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                ?: throw Exception("You're not signed in.")
                            val userDoc = firestore.collection("users").document(uid).get().await()
                            val userName = userDoc.getString("fullName") ?: ""
                            firestore.collection("reviews").document(booking.bookingId).set(
                                Review(
                                    bookingId = booking.bookingId,
                                    shopId = booking.shopId,
                                    userId = uid,
                                    userName = userName,
                                    rating = rating,
                                    comment = comment.trim(),
                                    createdAt = System.currentTimeMillis()
                                )
                            ).await()
                            onSubmitted(rating)
                        } catch (e: Exception) {
                            error = "Couldn't submit your rating: ${e.message}"
                            isSubmitting = false
                        }
                    }
                }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReceiptDialog(booking: Booking, shopName: String, shopLogo: ImageBitmap?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val surfaceColor = MaterialTheme.colorScheme.surface
    var isSaving by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }
    var pendingPdf by remember { mutableStateOf<Boolean?>(null) }

    fun doSave(asPdf: Boolean) {
        if (isSaving) return
        showFormatMenu = false
        isSaving = true
        scope.launch {
            val dest = saveReceipt(context, graphicsLayer, booking, asPdf)
            Toast.makeText(
                context,
                if (dest != null) "Receipt saved to $dest" else "Couldn't save receipt",
                Toast.LENGTH_SHORT
            ).show()
            isSaving = false
        }
    }

    // Android 8–9 need WRITE_EXTERNAL_STORAGE to write to public storage; 10+ don't.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val asPdf = pendingPdf
        pendingPdf = null
        when {
            granted && asPdf != null -> doSave(asPdf)
            !granted -> Toast.makeText(
                context, "Storage permission is needed to save the receipt", Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestSave(asPdf: Boolean) {
        showFormatMenu = false
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingPdf = asPdf
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            doSave(asPdf)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // Scrollable receipt body — the recorded Column is measured at
                // full content height inside the scroll, so the capture grabs the
                // whole receipt regardless of scroll position.
                Box(
                    modifier = Modifier
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier
                            .drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            }
                            .background(surfaceColor)
                            .padding(20.dp)
                    ) {
                        ReceiptBody(booking, shopName, shopLogo)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showFormatMenu = true },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSaving) "Saving…" else "Save")
                        }
                        DropdownMenu(
                            expanded = showFormatMenu,
                            onDismissRequest = { showFormatMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save as image") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = { requestSave(asPdf = false) }
                            )
                            DropdownMenuItem(
                                text = { Text("Save as PDF") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = { requestSave(asPdf = true) }
                            )
                        }
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/** The visible/captured receipt content, shared by the dialog and the share image. */
@Composable
private fun ReceiptBody(booking: Booking, shopName: String, shopLogo: ImageBitmap?) {
    // Shop's own logo (if uploaded) — the shop's branding on its receipt.
    if (shopLogo != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = shopLogo,
                contentDescription = "$shopName logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
    Text(
        text = shopName.ifEmpty { "Car Wash" },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Text(
        text = "Booking Receipt",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    ReceiptRow("Receipt No.", "#" + booking.bookingId.takeLast(8).uppercase())
    val issued = booking.paidAt ?: booking.completedAt ?: booking.createdAt
    ReceiptRow("Date", DateUtils.formatDateTime(issued))
    ReceiptRow("Car", booking.carDetails)
    ReceiptRow("Booked for", "${DateUtils.formatDate(booking.bookingDate)} · ${booking.timeSlot}")
    if (booking.servicedBy.isNotBlank()) {
        ReceiptRow("Serviced by", booking.servicedBy)
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text("Services", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    booking.resolvedServiceNames().forEach { name ->
        Text(
            "•  $name",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    if (booking.addOns.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text("Add-ons", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        booking.addOns.forEach { label ->
            val name = label.substringBeforeLast(" - ₱").trim().ifEmpty { label }
            val amount = label.substringAfterLast("₱", "")
            ReceiptRow(name, if (amount.isNotEmpty()) "₱$amount" else "")
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "₱${booking.price}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    ReceiptRow("Payment", "Cash · " + if (booking.paymentStatus == "paid") "Paid" else "Unpaid")
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text("Timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    if (booking.createdAt > 0) TimelineRow("Booked", booking.createdAt)
    booking.confirmedAt?.let { TimelineRow("Confirmed", it) }
    booking.inProgressAt?.let { TimelineRow("In progress", it) }
    booking.paidAt?.let { TimelineRow("Paid", it) }
    booking.completedAt?.let { TimelineRow("Completed", it) }
    booking.cancelledAt?.let { TimelineRow("Cancelled", it) }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(
        "System-generated receipt for a cash booking. Not an official BIR receipt.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    // CHECKOT app branding.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "Powered by CHECKOT",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Renders the recorded receipt layer to a PNG (→ Gallery/Pictures) or a
 * single-page PDF (→ Downloads) on the device. Fully on-device — no network.
 * On Android 10+ this uses scoped MediaStore (no permission); on 8–9 it writes
 * to public storage (caller must hold WRITE_EXTERNAL_STORAGE first).
 * Returns the human-readable destination ("Gallery"/"Downloads"), or null on failure.
 */
private suspend fun saveReceipt(
    context: android.content.Context,
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    booking: Booking,
    asPdf: Boolean
): String? {
    val bitmap: Bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
    return withContext(Dispatchers.IO) {
        try {
            val suffix = booking.bookingId.takeLast(8)
            val displayName = if (asPdf) "receipt_$suffix.pdf" else "receipt_$suffix.png"
            val mime = if (asPdf) "application/pdf" else "image/png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = if (asPdf) MediaStore.Downloads.EXTERNAL_CONTENT_URI
                                 else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (asPdf) Environment.DIRECTORY_DOWNLOADS
                        else Environment.DIRECTORY_PICTURES + "/Checkot"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    if (asPdf) writeBitmapToPdf(bitmap, out)
                    else bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return@withContext null
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(
                    if (asPdf) Environment.DIRECTORY_DOWNLOADS else Environment.DIRECTORY_PICTURES
                ).apply { mkdirs() }
                val file = File(dir, displayName)
                FileOutputStream(file).use { out ->
                    if (asPdf) writeBitmapToPdf(bitmap, out)
                    else bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                // Make the file visible in the gallery / file managers.
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            }
            if (asPdf) "Downloads" else "Gallery"
        } catch (e: Exception) {
            Log.e("ReceiptSave", "Failed to save receipt: ${e.message}")
            null
        }
    }
}

/** Draws the captured receipt bitmap onto a single PDF page sized to the image. */
private fun writeBitmapToPdf(bitmap: Bitmap, out: OutputStream) {
    // PdfDocument renders on a software canvas, which can't draw HARDWARE-config
    // bitmaps — the graphicsLayer capture is hardware-backed. Copying a hardware
    // bitmap straight to ARGB_8888 comes back blank on many devices, so round-trip
    // through PNG bytes (compress reads the hardware bitmap correctly) to get a
    // software bitmap that actually holds the pixels.
    val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val bytes = baos.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } else {
        bitmap
    }
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(softwareBitmap.width, softwareBitmap.height, 1).create()
    val page = document.startPage(pageInfo)
    page.canvas.drawColor(android.graphics.Color.WHITE)
    page.canvas.drawBitmap(softwareBitmap, 0f, 0f, null)
    document.finishPage(page)
    document.writeTo(out)
    document.close()
    if (softwareBitmap !== bitmap) softwareBitmap.recycle()
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun TimelineRow(label: String, time: Long) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(
            DateUtils.formatDateTime(time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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

    val steps = listOf(
        "Queue" to Icons.Default.HourglassEmpty,
        "Accepted" to Icons.Default.Assignment,
        "In Progress" to Icons.Default.LocalCarWash,
        "Ready" to Icons.Default.VpnKey
    )
    val currentStepIndex = when (status) {
        BookingStatus.PENDING -> 0
        BookingStatus.CONFIRMED -> 1
        BookingStatus.IN_PROGRESS -> 2
        BookingStatus.COMPLETED -> 3
        BookingStatus.CANCELLED -> -1
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.app.checkot.ui.theme.CheckotCardSurface),
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
                color = Color(0xFF00E6C3),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            // Line with circles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp) // Increased for glow
            ) {
                // Full background line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Color.White.copy(alpha = 0.1f),
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
                                Color(0xFF00E6C3),
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
                    steps.forEachIndexed { index, step ->
                        val (label, icon) = step
                        val isCompleted = index < currentStepIndex
                        val isActive = index == currentStepIndex
                        val isHighlighted = isCompleted || isActive
                        
                        val circleColor = if (isHighlighted) Color(0xFF00E6C3) else Color.White.copy(alpha = 0.2f)
                        val iconColor = if (isHighlighted) Color(0xFF0F2530) else Color.White.copy(alpha = 0.6f)
                        
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .then(
                                    if (isActive) Modifier.drawBehind {
                                        drawCircle(
                                            color = Color(0xFF00E6C3).copy(alpha = 0.4f),
                                            radius = size.minDimension / 2 + 8.dp.toPx()
                                        )
                                    } else Modifier
                                )
                                .background(circleColor, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            // Labels below circles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                steps.forEachIndexed { index, step ->
                    val (label, _) = step
                    val isActive = index == currentStepIndex
                    val isCompleted = index < currentStepIndex
                    val isHighlighted = isCompleted || isActive
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHighlighted) Color(0xFF00E6C3) else Color.White.copy(alpha = 0.6f),
                        fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun QueuePositionCard(queueInfo: QueueInfo, status: BookingStatus) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00E6C3), MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(
            containerColor = com.app.checkot.ui.theme.CheckotCardSurface
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
                    tint = Color(0xFF00E6C3)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Queue Position",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF00E6C3)
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
                color = Color(0xFF00E6C3)
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
                    BookingStatus.PENDING -> "Your booking request has been sent to the shop. We'll notify you as soon as they accept or if anything changes."
                    BookingStatus.CONFIRMED -> "Your spot is secured! We'll keep you updated on your wash status or if anything changes."
                    BookingStatus.IN_PROGRESS -> "Your car is being serviced right now"
                    else -> ""
                },
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}
