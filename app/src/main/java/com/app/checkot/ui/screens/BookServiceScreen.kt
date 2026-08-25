package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.checkot.ui.components.AppButton
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.tasks.await
import com.app.checkot.ui.components.BackTopAppBar
import com.app.checkot.ui.components.ShopLogo
import com.app.checkot.ui.components.DetailRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.app.checkot.viewmodel.PhoneVerifyState
import coil.compose.AsyncImage

data class AvailableService(
    val config: CustomServiceConfig,
    val serviceType: ServiceType? // null for custom "Others" services
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookServiceScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    bookingViewModel: BookingViewModel = viewModel(),
    carViewModel: CarViewModel = viewModel(),
    shopId: String = "",
    preselectedService: ServiceType? = null
) {
    val userData by authViewModel.currentUserData.collectAsState()
    val scope = rememberCoroutineScope()
    val firestore: FirebaseFirestore = Firebase.firestore

    // Available services from shop's customization
    var availableServices by remember { mutableStateOf<List<AvailableService>>(emptyList()) }
    var loadingServices by remember { mutableStateOf(true) }
    var servicesLoadError by remember { mutableStateOf<String?>(null) }
    // Shop's working hours (minutes since midnight); defaults match the legacy 9–4 window
    var shopOpenMinutes by remember { mutableStateOf(540) }
    var shopCloseMinutes by remember { mutableStateOf(960) }
    // Shop location for the map (0 = not set)
    var shopLatitude by remember { mutableStateOf(0.0) }
    var shopLongitude by remember { mutableStateOf(0.0) }
    var shopDisplayName by remember { mutableStateOf("Shop") }
    var shopLogoUrl by remember { mutableStateOf("") }
    var shopBannerUrl by remember { mutableStateOf("") }
    var showLogoViewer by remember { mutableStateOf(false) }
    var showShopInfoSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    // Dates the whole shop is closed — clients can't book these.
    var shopClosedDates by remember { mutableStateOf(emptyList<Long>()) }
    // One-off hours overrides (date → open/close). Applied only on that date.
    var shopDayOverrides by remember { mutableStateOf(emptyList<DayHoursOverride>()) }

    // Progressive phone verification guard: if the client hasn't verified a
    // phone number yet, show an inline dialog before completing the booking.
    var showPhoneVerifyDialog by remember { mutableStateOf(false) }
    // Stash the pending booking so we can submit it after verification succeeds.
    var pendingBooking by remember { mutableStateOf<Booking?>(null) }

    // Real-time listener for shop services — updates instantly when owner changes services
    DisposableEffect(shopId) {
        if (shopId.isEmpty()) return@DisposableEffect onDispose {}
        loadingServices = true
        servicesLoadError = null
        val listener = firestore.collection("shop_services").document(shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("❌ Services listener error: ${error.message}")
                    servicesLoadError = "Could not load services. Check your connection."
                    loadingServices = false
                    return@addSnapshotListener
                }
                val customization = snapshot?.toObject(ShopCustomization::class.java)
                val services = mutableListOf<AvailableService>()
                if (customization != null) {
                    shopOpenMinutes = customization.openMinutes
                    shopCloseMinutes = customization.closeMinutes
                    shopLatitude = customization.latitude
                    shopLongitude = customization.longitude
                    if (customization.shopName.isNotBlank()) shopDisplayName = customization.shopName
                    shopLogoUrl = customization.logoUrl
                    shopBannerUrl = customization.bannerUrl
                    shopClosedDates = customization.closedDates
                    shopDayOverrides = customization.dayOverrides
                    for (config in customization.services) {
                        val type = if (!config.isCustom) {
                            ServiceType.values().find { it.name == config.serviceName }
                        } else null
                        services.add(AvailableService(config = config, serviceType = type))
                    }
                }
                availableServices = services
                loadingServices = false
            }
        onDispose {
            listener.remove()
        }
    }

    var selectedServiceConfigs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCar by remember { mutableStateOf<Car?>(null) }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedTimeSlot by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) }
    var isCreating by remember { mutableStateOf(false) }

    val availableTimeSlots by bookingViewModel.availableTimeSlots.collectAsState()
    val savedCars by carViewModel.savedCars.collectAsState()

    LaunchedEffect(savedCars) {
        if (selectedCar == null && savedCars.isNotEmpty()) {
            selectedCar = savedCars.find { it.isDefault } ?: savedCars.first()
        }
    }

    // Availability helpers for the selected date.
    val selectedDay = BookingUtils.startOfDay(selectedDate)
    val shopClosedOnSelected = shopClosedDates.contains(selectedDay)
    // Foodpanda-style cart cleanup: if the shop is closed on the selected date,
    // or the owner marks a selected service unavailable (or removes it), drop it
    // from the selection and tell the user — so no bookable-looking steps or
    // slots remain on a day the client can't actually book.
    var droppedNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(availableServices, selectedDate, shopClosedDates) {
        if (shopClosedOnSelected) {
            if (selectedServiceConfigs.isNotEmpty() || selectedTimeSlot.isNotEmpty()) {
                selectedServiceConfigs = emptySet()
                selectedTimeSlot = ""
                droppedNotice = "This shop is closed on ${DateUtils.formatDate(selectedDay)} — your selection was cleared."
            } else {
                droppedNotice = null
            }
            return@LaunchedEffect
        }
        val nowUnavailable = selectedServiceConfigs.filter { name ->
            val config = availableServices.firstOrNull { it.config.serviceName == name }?.config
            config == null || config.unavailableDates.contains(selectedDay)
        }
        if (nowUnavailable.isNotEmpty()) {
            selectedServiceConfigs = selectedServiceConfigs - nowUnavailable.toSet()
            selectedTimeSlot = ""
            droppedNotice = "A service you selected is no longer available for this date and was removed."
        } else {
            droppedNotice = null
        }
    }

    // Calculate total duration from selected services — prefer the duration
    // the owner configured; fall back to built-in defaults for legacy configs
    val totalDurationMinutes = remember(selectedServiceConfigs, availableServices) {
        val selectedAvails = availableServices.filter { selectedServiceConfigs.contains(it.config.serviceName) }
        selectedAvails.sumOf { avail ->
            when {
                avail.config.durationMinutes > 0 -> avail.config.durationMinutes
                avail.serviceType != null -> BookingUtils.parseDurationMinutes(avail.serviceType.duration)
                else -> 60 // legacy custom service with no configured duration
            }
        }.coerceAtLeast(30)
    }

    // Effective hours for the selected date — a per-day override wins over the
    // permanent open/close (e.g. owner closed early today due to an emergency).
    val (effectiveOpen, effectiveClose) = BookingUtils.effectiveHours(
        shopOpenMinutes, shopCloseMinutes, shopDayOverrides, selectedDate
    )
    val activeOverride = shopDayOverrides.firstOrNull { it.date == selectedDay }

    // Shop reviews — verified client reviews (only writable against completed
    // bookings, see Firestore rules). Shown as average + full list.
    var shopReviews by remember { mutableStateOf<List<Review>>(emptyList()) }
    LaunchedEffect(shopId) {
        if (shopId.isEmpty()) return@LaunchedEffect
        // Best-effort: a denied read (rules not yet deployed) or a network error
        // must never crash the screen — just show no reviews.
        try {
            val snapshot = firestore.collection("reviews").whereEqualTo("shopId", shopId).get().await()
            shopReviews = snapshot.documents
                .mapNotNull { it.toObject(Review::class.java) }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            println("❌ Failed to load shop reviews: ${e.message}")
        }
    }
    val shopRating = shopReviews.takeIf { it.isNotEmpty() }
        ?.let { reviews -> reviews.map { it.rating }.average() to reviews.size }

    LaunchedEffect(selectedDate, shopId, totalDurationMinutes, effectiveOpen, effectiveClose) {
        bookingViewModel.fetchAvailableTimeSlots(
            selectedDate, shopId, totalDurationMinutes, effectiveOpen, effectiveClose
        )
    }
    // Date picker state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val currentDayStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                // Allow today + 3 days in the future
                val maxDayStart = currentDayStart + (3 * 24 * 60 * 60 * 1000L)
                
                return utcTimeMillis in currentDayStart..maxDayStart
            }
        }
    )
    val showDatePicker = remember { mutableStateOf(false) }
    if (showDatePicker.value) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rawDate = datePickerState.selectedDateMillis
                        selectedDate = if (rawDate != null) {
                            com.app.checkot.utils.BookingUtils.utcMidnightToLocalMidnight(rawDate)
                        } else {
                            com.app.checkot.utils.BookingUtils.startOfDay(System.currentTimeMillis())
                        }
                        showDatePicker.value = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker.value = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Show error dialog when booking is rejected
    val bookingError by bookingViewModel.error.collectAsState()
    if (bookingError != null) {
        val isCooldown = bookingError?.startsWith("cooldown:") == true
        val cooldownEndTime = remember(bookingError) {
            if (isCooldown) {
                bookingError!!.substringAfter("cooldown:").toLongOrNull() ?: 0L
            } else 0L
        }
        var remainingSeconds by remember { mutableStateOf(0) }

        // Live countdown ticker + auto-dismiss
        LaunchedEffect(cooldownEndTime) {
            if (cooldownEndTime > 0) {
                while (true) {
                    remainingSeconds = ((cooldownEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    if (remainingSeconds <= 0) break
                    kotlinx.coroutines.delay(1000)
                }
                bookingViewModel.clearError()
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!isCooldown || remainingSeconds <= 0) {
                    bookingViewModel.clearError()
                }
            },
            title = { Text(if (isCooldown) "Please Wait" else "Cannot Book") },
            text = {
                if (isCooldown) {
                    val min = remainingSeconds / 60
                    val sec = remainingSeconds % 60
                    Text("You cancelled a booking recently. Please wait ${min}:${String.format("%02d", sec)} before booking again.")
                } else {
                    Text(bookingError!!)
                }
            },
            confirmButton = {
                TextButton(onClick = { bookingViewModel.clearError() }) {
                    Text("Got it")
                }
            }
        )
    }

    // Full-view logo dialog — clients tap the shop logo to appreciate it larger.
    if (showLogoViewer && shopLogoUrl.isNotBlank()) {
        Dialog(onDismissRequest = { showLogoViewer = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = shopLogoUrl,
                        contentDescription = "$shopDisplayName logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = shopDisplayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showLogoViewer = false }) {
                        Text("Close")
                    }
                }
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity(x = 0f, y = available.y)
            }
        }
    }

    if (showShopInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShopInfoSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            sheetGesturesEnabled = true,
            modifier = Modifier.nestedScroll(nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = shopDisplayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showShopInfoSheet = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Map and Address
                if (shopLatitude != 0.0 || shopLongitude != 0.0) {
                    item {
                        com.app.checkot.ui.components.ShopLocationView(
                            latitude = shopLatitude,
                            longitude = shopLongitude,
                            shopName = shopDisplayName
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Store Hours
                item {
                    Text(
                        text = "Store Hours",
                        style = MaterialTheme.typography.titleMedium
                      )
                      Spacer(modifier = Modifier.height(4.dp))
                      Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text(
                              text = "${BookingUtils.minutesToSlotLabel(shopOpenMinutes)} - ${BookingUtils.minutesToSlotLabel(shopCloseMinutes)}",
                              style = MaterialTheme.typography.bodyMedium
                          )
                      }
                      Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Reviews Header
                item {
                    if (shopReviews.isNotEmpty()) {
                        Text(
                            text = "Reviews (${shopReviews.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Text(
                            text = "No reviews yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                
                // Reviews Items
                if (shopReviews.isNotEmpty()) {
                    items(shopReviews) { review ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    repeat(review.rating) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${review.userName.split(" ").first()} · " +
                                            DateUtils.formatDate(review.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                if (review.comment.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = review.comment,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Booking form is valid only when a service, a car, and a time slot are chosen.
    val isBookingValid =
        selectedServiceConfigs.isNotEmpty() && selectedCar != null && selectedTimeSlot.isNotEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                // Dark bar that extends seamlessly behind the system nav bar.
                color = Color(0xFF0F2530),
                shadowElevation = 8.dp,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Pad the CONTENT above the nav bar (background stays full-bleed),
                        // so the buttons clear the 3-button navigation overlay.
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (step >= 1 && selectedServiceConfigs.isNotEmpty()) {
                        val selectedAvails = availableServices.filter { selectedServiceConfigs.contains(it.config.serviceName) }
                        val totalPrice = selectedAvails.sumOf {
                            if (it.config.customPrice > 0) it.config.customPrice
                            else it.serviceType?.price ?: 0.0
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Price:", style = MaterialTheme.typography.titleMedium)
                            Text("₱${totalPrice.toInt()}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val buttonShape = RoundedCornerShape(24.dp)
                        // 52dp for comfortable touch clearance on all display sizes.
                        val buttonHeight = Modifier.height(52.dp)
                        if (step > 1) {
                            OutlinedButton(
                                onClick = { step-- },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(buttonHeight),
                                shape = buttonShape
                            ) {
                                Text("Back", maxLines = 1, softWrap = false)
                            }
                        }
                        AppButton(
                            text = if (step < 4) "Continue" else "Confirm",
                            onClick = {
                                if (step < 4) {
                                    step++
                                } else {
                                    scope.launch {
                                        isCreating = true
                                        val selectedAvails = availableServices.filter { selectedServiceConfigs.contains(it.config.serviceName) }
                                        val serviceTypes = selectedAvails.map { it.serviceType ?: ServiceType.CUSTOM }
                                        val customNames = selectedAvails.filter { it.serviceType == null }
                                            .map { it.config.customName.ifBlank { it.config.displayName } }
                                        val totalPrice = selectedAvails.sumOf {
                                            if (it.config.customPrice > 0) it.config.customPrice
                                            else it.serviceType?.price ?: 0.0
                                        }
                                        val booking = Booking(
                                            userId = authViewModel.getCurrentUser()?.uid ?: "",
                                            shopId = shopId,
                                            carId = selectedCar?.carId ?: "",
                                            carDetails = "${selectedCar?.brand} ${selectedCar?.model} - ${selectedCar?.plateNumber}",
                                            services = serviceTypes,
                                            customServiceNames = customNames,
                                            bookingDate = selectedDate,
                                            timeSlot = selectedTimeSlot,
                                            price = totalPrice,
                                            durationMinutes = totalDurationMinutes,
                                            notes = notes,
                                            status = BookingStatus.PENDING
                                        )
                                        // Progressive guard: if the user hasn't verified
                                        // a phone number yet, stash the booking and show
                                        // the inline verification dialog first.
                                        val user = userData
                                        if (user != null && !user.phoneVerified) {
                                            pendingBooking = booking
                                            showPhoneVerifyDialog = true
                                            isCreating = false
                                        } else {
                                            bookingViewModel.createBooking(booking)
                                            kotlinx.coroutines.delay(1500)
                                            if (bookingViewModel.error.value == null) {
                                                navController.popBackStack()
                                            } else {
                                                isCreating = false
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(if (step > 1) 1f else 2f)
                                .then(buttonHeight),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            enabled = if (step < 4) when (step) {
                                1 -> selectedServiceConfigs.isNotEmpty()
                                2 -> selectedCar != null
                                3 -> selectedTimeSlot.isNotEmpty()
                                else -> true
                            } else isBookingValid,
                            isLoading = isCreating
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = paddingValues.calculateBottomPadding()
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    val logoSize = 80.dp
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 180.dp Banner (edge-to-edge)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .then(
                                    if (shopBannerUrl.isBlank())
                                        Modifier.background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF00BFA5), Color(0xFF0D2B35))
                                            )
                                        )
                                    else Modifier
                                )
                        ) {
                            if (shopBannerUrl.isNotBlank()) {
                                AsyncImage(
                                    model = shopBannerUrl,
                                    contentDescription = "Shop banner",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.45f to Color.Transparent,
                                            1f to Color.Black.copy(alpha = 0.45f)
                                        )
                                    )
                            )
                            // Floating back and info buttons (statusBarsPadding-aligned)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(top = 16.dp, start = 14.dp, end = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack(Screen.Home.route, inclusive = false)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White, shape = RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = { showShopInfoSheet = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White, shape = RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Shop Info",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Logo overlapping
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = logoSize / 2)
                        ) {
                            ShopLogo(
                                logoUrl = shopLogoUrl,
                                size = logoSize,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(4.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                                    .clickable { showLogoViewer = true }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(logoSize / 2 + 16.dp))
                }

                // Shop details on solid background surface (high contrast)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = shopDisplayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val ratingInfo = shopRating
                            if (ratingInfo != null) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "%.1f (%d review%s)".format(
                                        ratingInfo.first,
                                        ratingInfo.second,
                                        if (ratingInfo.second == 1) "" else "s"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showShopInfoSheet = true }
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "View Map >",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Stepper right below header details
                item {
                    LinearProgressIndicator(
                        progress = { step / 4f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                // Step 1: Select Service
                if (step >= 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Step 1: Select Service",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (loadingServices) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    if (droppedNotice != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(droppedNotice!!, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                        }
                    }
                    if (shopClosedOnSelected) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.EventBusy,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "This shop is closed on ${DateUtils.formatDate(selectedDay)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No bookings are available on this date. Please choose another day.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (activeOverride != null && !shopClosedOnSelected) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Special hours on ${DateUtils.formatDate(selectedDay)}: " +
                                            "${BookingUtils.minutesToSlotLabel(activeOverride.openMinutes)} – " +
                                            "${BookingUtils.minutesToSlotLabel(activeOverride.closeMinutes)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    if (servicesLoadError != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(servicesLoadError!!, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    } else if (availableServices.isEmpty() && !loadingServices) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Storefront,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "This shop is currently not in service",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "The owner hasn't configured any services yet. Check back later!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    } else if (!shopClosedOnSelected) {
                        items(availableServices, key = { it.config.serviceName }) { avail ->
                            val isSelected = selectedServiceConfigs.contains(avail.config.serviceName)
                            // Foodpanda-style "sold out today": greyed out + disabled.
                            val isUnavailable = avail.config.unavailableDates.contains(selectedDay)
                            val displayPrice = if (avail.config.customPrice > 0) avail.config.customPrice
                                               else avail.serviceType?.price ?: 0.0
                            val displayName = if (avail.config.isCustom) avail.config.customName
                                              else avail.config.displayName
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                ShopServiceSelectionCard(
                                    name = displayName,
                                    price = displayPrice,
                                    description = avail.config.description,
                                    isSelected = isSelected,
                                    unavailable = isUnavailable,
                                    onSelect = {
                                        if (!isUnavailable) {
                                            selectedServiceConfigs = if (isSelected) {
                                                selectedServiceConfigs - avail.config.serviceName
                                            } else {
                                                selectedServiceConfigs + avail.config.serviceName
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                // Step 2: Select Car
                if (step >= 2 && selectedServiceConfigs.isNotEmpty()) {
                    item {
                        Text(
                            text = "Step 2: Select Car",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp).padding(horizontal = 16.dp)
                        )
                    }
                    if (savedCars.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("No cars added yet")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { navController.navigate("add_car") }
                                    ) {
                                        Text("Add Car")
                                    }
                                }
                            }
                        }
                    } else {
                        items(savedCars, key = { it.carId }) { car ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                CarSelectionCard(
                                    car = car,
                                    isSelected = selectedCar?.carId == car.carId,
                                    onSelect = { selectedCar = car }
                                )
                            }
                        }
                    }
                }
                // Step 3: Select Date & Time
                if (step >= 3 && selectedCar != null) {
                    item {
                        Text(
                            text = "Step 3: Select Date & Time",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp).padding(horizontal = 16.dp)
                        )
                    }
                    // Date strip — rendered as a composable Column inside item{} so
                    // it doesn't need its own lazy scope.
                    item {
                        val today = java.time.LocalDate.now()
                        val dates = remember { (0..13).map { today.plusDays(it.toLong()) } }
                        var selectedDateLocal by remember {
                            mutableStateOf(
                                java.time.Instant.ofEpochMilli(selectedDate)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            )
                        }
                        // Sync outward when date changes
                        LaunchedEffect(selectedDateLocal) {
                            selectedDate = selectedDateLocal
                                .atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                        }
                        Column {
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(dates) { date ->
                                    val dayAbbr = date.dayOfWeek.name.take(3)
                                    val dayNum = date.dayOfMonth
                                    val isDateSelected = date == selectedDateLocal
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDateSelected)
                                                Color(0xFF00BFA5).copy(alpha = 0.25f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        border = if (isDateSelected)
                                            BorderStroke(2.dp, Color(0xFF00BFA5))
                                        else null,
                                        modifier = Modifier.clickable { selectedDateLocal = date }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = dayAbbr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isDateSelected) Color(0xFF00BFA5)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = dayNum.toString(),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isDateSelected) Color(0xFF00BFA5)
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Time slots header
                    item {
                        Text(
                            text = "Available Time Slots",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 8.dp).padding(horizontal = 16.dp)
                        )
                    }
                    // Time slot grid — rendered as a regular Column+FlowRow equivalent
                    // using chunked rows so it doesn't conflict with the parent LazyColumn.
                    item {
                        val slotRows = availableTimeSlots.chunked(3)
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (row in slotRows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (slot in row) {
                                        val isSlotSelected = selectedTimeSlot == slot.slot
                                        val isAvailable = slot.available
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = when {
                                                    !isAvailable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                    isSlotSelected -> Color(0xFF00BFA5).copy(alpha = 0.2f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ),
                                            border = if (isSlotSelected)
                                                BorderStroke(2.dp, Color(0xFF00BFA5))
                                            else null,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = isAvailable) {
                                                    selectedTimeSlot = slot.slot
                                                }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp)
                                            ) {
                                                Text(
                                                    text = slot.slot,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = when {
                                                        !isAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                                        isSlotSelected -> Color(0xFF00BFA5)
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    // Fill remaining columns if row has < 3 items
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                // Step 4: Additional Notes
                if (step >= 4 && selectedTimeSlot.isNotEmpty()) {
                    item {
                        Text(
                            text = "Step 4: Additional Notes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp).padding(horizontal = 16.dp)
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { 
                                if (it.length <= 500) {
                                    notes = it 
                                }
                            },
                            label = { Text("Special requests or notes (Optional)") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            minLines = 3,
                            maxLines = 5
                        )
                    }
                    item {
                        val selectedAvails = availableServices.filter { selectedServiceConfigs.contains(it.config.serviceName) }
                        val selectedNames = selectedAvails.joinToString(", ") {
                            if (it.config.isCustom) it.config.customName else it.config.displayName
                        }
                        val totalPrice = selectedAvails.sumOf {
                            if (it.config.customPrice > 0) it.config.customPrice
                            else it.serviceType?.price ?: 0.0
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Booking Summary",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                DetailRow("Services:", selectedNames, singleLine = false)
                                DetailRow("Car:", "${selectedCar?.brand} ${selectedCar?.model} (${selectedCar?.plateNumber})", singleLine = false)
                                DetailRow("Date:", DateUtils.formatDate(selectedDate), singleLine = false)
                                DetailRow("Time:", selectedTimeSlot, singleLine = false)
                                DetailRow("Total Price:", "₱${totalPrice}", singleLine = false)
                                DetailRow("Payment:", "Cash — pay at the shop", singleLine = false)
                                if (notes.isNotBlank()) {
                                    DetailRow("Notes:", notes, singleLine = false)
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    // --- Progressive phone verification dialog ---
    // Watches for successful phone verification and auto-submits the stashed booking.
    val verifyState by authViewModel.phoneVerifyState.collectAsState()
    LaunchedEffect(verifyState) {
        if (verifyState is PhoneVerifyState.Success && pendingBooking != null) {
            showPhoneVerifyDialog = false
            isCreating = true
            bookingViewModel.createBooking(pendingBooking!!)
            kotlinx.coroutines.delay(1500)
            if (bookingViewModel.error.value == null) {
                navController.popBackStack()
            } else {
                isCreating = false
            }
            pendingBooking = null
        }
    }

    if (showPhoneVerifyDialog) {
        PhoneVerifyBookingDialog(
            authViewModel = authViewModel,
            onDismiss = {
                showPhoneVerifyDialog = false
                pendingBooking = null
                authViewModel.resetPhoneVerify()
            }
        )
    }
}
@Composable
fun ShopServiceSelectionCard(
    name: String,
    price: Double,
    isSelected: Boolean,
    onSelect: () -> Unit,
    description: String = "",
    unavailable: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (isSelected && !unavailable) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00BFA5)) else null,
        colors = when {
            unavailable -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
            isSelected -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            else -> CardDefaults.cardColors()
        },
        onClick = { if (!unavailable) onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unavailable) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    } else if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified
                )
                if (description.isNotBlank() && !unavailable) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (unavailable) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Unavailable on this date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (unavailable) "—" else "₱${price.toInt()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (unavailable) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    } else if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { if (!unavailable) onSelect() },
                    enabled = !unavailable,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF00BFA5)
                    )
                )
            }
        }
    }
}
@Composable
fun CarSelectionCard(
    car: Car,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },
        onClick = onSelect
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
                    text = "${car.brand} ${car.model}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = car.plateNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (car.isDefault) {
                Surface(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
@Composable
fun TimeSlotCard(
    slot: TimeSlot,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else if (slot.available) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        },
        enabled = slot.available,
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = slot.slot,
                style = MaterialTheme.typography.bodyLarge
            )
            if (!slot.available) {
                Text(
                    text = "Booked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Inline phone verification dialog shown when a client without a verified
 * phone number attempts to confirm a booking. Once the OTP succeeds, the
 * caller's LaunchedEffect on [PhoneVerifyState.Success] auto-submits the
 * stashed booking.
 */
@Composable
private fun PhoneVerifyBookingDialog(
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    val verifyState by authViewModel.phoneVerifyState.collectAsState()
    var localDigits by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { authViewModel.resetPhoneVerify() }

    val awaitingCode = verifyState is PhoneVerifyState.CodeSent ||
        verifyState is PhoneVerifyState.Verifying ||
        (verifyState is PhoneVerifyState.Error && authViewModel.hasPendingCode())
    val busy = verifyState is PhoneVerifyState.Sending || verifyState is PhoneVerifyState.Verifying

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2530))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = Color(0xFF00E6C3),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Verify your phone to book",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (!awaitingCode)
                        "We need to verify your phone number before your first booking."
                    else
                        "Enter the 6-digit code we sent to +63$localDigits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(16.dp))

                if (!awaitingCode) {
                    OutlinedTextField(
                        value = localDigits,
                        onValueChange = { input -> localDigits = input.filter { it.isDigit() }.take(10) },
                        label = { Text("Phone Number") },
                        prefix = { Text("+63 ") },
                        placeholder = { Text("9XXXXXXXXX") },
                        singleLine = true,
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        )
                    )
                    val validNumber = localDigits.length == 10 && localDigits.startsWith("9")
                    Spacer(Modifier.height(16.dp))
                    AppButton(
                        text = "Send code",
                        onClick = {
                            if (activity != null) {
                                authViewModel.startPhoneVerification(activity, "+63$localDigits", "signup")
                            }
                        },
                        enabled = validNumber && activity != null,
                        isLoading = busy
                    )
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("6-digit code") },
                        singleLine = true,
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    AppButton(
                        text = "Verify",
                        onClick = { authViewModel.confirmPhoneCode(code) },
                        enabled = code.length == 6,
                        isLoading = busy
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { authViewModel.resetPhoneVerify(); code = "" },
                        enabled = !busy
                    ) { Text("Use a different number", color = Color(0xFF00E6C3)) }
                }

                if (verifyState is PhoneVerifyState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = (verifyState as PhoneVerifyState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}
