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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.tasks.await

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

    // Real-time listener for shop services — updates instantly when owner changes services
    DisposableEffect(shopId) {
        if (shopId.isEmpty()) return@DisposableEffect onDispose {}
        loadingServices = true
        val listener = firestore.collection("shop_services").document(shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("❌ Services listener error: ${error.message}")
                    loadingServices = false
                    return@addSnapshotListener
                }
                val customization = snapshot?.toObject(ShopCustomization::class.java)
                val services = mutableListOf<AvailableService>()
                if (customization != null) {
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

    LaunchedEffect(selectedDate, shopId) {
        bookingViewModel.fetchAvailableTimeSlots(selectedDate, shopId)
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
                        selectedDate = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Car Wash") },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack(Screen.Home.route, inclusive = false)
                        }
                    }) {
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
            // Stepper
            LinearProgressIndicator(
                progress = step / 4f,
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step 1: Select Service
                if (step >= 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                    if (availableServices.isEmpty() && !loadingServices) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
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
                    } else {
                        items(availableServices) { avail ->
                            val isSelected = selectedServiceConfigs.contains(avail.config.serviceName)
                            val displayPrice = if (avail.config.customPrice > 0) avail.config.customPrice
                                               else avail.serviceType?.price ?: 0.0
                            val displayName = if (avail.config.isCustom) avail.config.customName
                                              else avail.config.displayName
                            ShopServiceSelectionCard(
                                name = displayName,
                                price = displayPrice,
                                isSelected = isSelected,
                                onSelect = {
                                    selectedServiceConfigs = if (isSelected) {
                                        selectedServiceConfigs - avail.config.serviceName
                                    } else {
                                        selectedServiceConfigs + avail.config.serviceName
                                    }
                                }
                            )
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
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    if (savedCars.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
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
                        items(savedCars) { car ->
                            CarSelectionCard(
                                car = car,
                                isSelected = selectedCar?.carId == car.carId,
                                onSelect = { selectedCar = car }
                            )
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
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showDatePicker.value = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Select Date",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = DateUtils.formatDate(selectedDate),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            text = "Available Time Slots",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(availableTimeSlots) { slot ->
                        TimeSlotCard(
                            slot = slot,
                            isSelected = selectedTimeSlot == slot.slot,
                            onSelect = { selectedTimeSlot = slot.slot }
                        )
                    }
                }
                // Step 4: Additional Notes
                if (step >= 4 && selectedTimeSlot.isNotEmpty()) {
                    item {
                        Text(
                            text = "Step 4: Additional Notes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp)
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
                            modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth(),
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
                                SummaryRow("Services:", selectedNames)
                                SummaryRow("Car:", "${selectedCar?.brand} ${selectedCar?.model} (${selectedCar?.plateNumber})")
                                SummaryRow("Date:", DateUtils.formatDate(selectedDate))
                                SummaryRow("Time:", selectedTimeSlot)
                                SummaryRow("Total Price:", "₱${totalPrice}")
                                if (notes.isNotBlank()) {
                                    SummaryRow("Notes:", notes)
                                }
                            }
                        }
                    }
                }
            }
            // Bottom Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (step < 4) {
                            step++
                        } else {
                            scope.launch {
                                isCreating = true
                                val selectedAvails = availableServices.filter { selectedServiceConfigs.contains(it.config.serviceName) }
                                val serviceTypes = selectedAvails.map { it.serviceType ?: ServiceType.CUSTOM }
                                val customNames = selectedAvails.filter { it.config.isCustom }.map { it.config.customName }
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
                                    notes = notes,
                                    status = BookingStatus.PENDING
                                )
                                bookingViewModel.createBooking(booking)
                                kotlinx.coroutines.delay(1500)
                                navController.popBackStack()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isCreating && when (step) {
                        1 -> selectedServiceConfigs.isNotEmpty()
                        2 -> selectedCar != null
                        3 -> selectedTimeSlot.isNotEmpty()
                        else -> true
                    }
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            when {
                                step < 4 -> "Next"
                                else -> "Confirm Booking"
                            }
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun ShopServiceSelectionCard(
    name: String,
    price: Double,
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
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "₱${price}",
                style = MaterialTheme.typography.titleLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
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
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
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
@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
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
