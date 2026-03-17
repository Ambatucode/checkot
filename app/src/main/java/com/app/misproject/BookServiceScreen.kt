package com.app.misproject

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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookServiceScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    preselectedService: ServiceType? = null
) {
    val userData by authViewModel.currentUserData.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedService by remember { mutableStateOf(preselectedService) }
    var selectedCar by remember { mutableStateOf<Car?>(null) }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedTimeSlot by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }

    val availableTimeSlots = authViewModel.getAvailableTimeSlots(selectedDate)

    // Date picker state
    val datePickerState = rememberDatePickerState()
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
                        Text(
                            text = "Step 1: Select Service",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(ServiceType.values()) { service ->
                        ServiceSelectionCard(
                            service = service,
                            isSelected = selectedService == service,
                            onSelect = { selectedService = service }
                        )
                    }
                }

                // Step 2: Select Car
                if (step >= 2 && selectedService != null) {
                    item {
                        Text(
                            text = "Step 2: Select Car",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    if (userData?.savedCars.isNullOrEmpty()) {
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
                        items(userData?.savedCars ?: emptyList()) { car ->
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
                            onValueChange = { notes = it },
                            label = { Text("Special requests or notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )
                    }

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
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Booking Summary",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Divider(modifier = Modifier.padding(vertical = 8.dp))

                                SummaryRow("Service:", selectedService?.displayName ?: "")
                                SummaryRow("Car:", "${selectedCar?.brand} ${selectedCar?.model} (${selectedCar?.plateNumber})")
                                SummaryRow("Date:", DateUtils.formatDate(selectedDate))
                                SummaryRow("Time:", selectedTimeSlot)
                                SummaryRow("Price:", "₱${selectedService?.price ?: 0.0}")

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
                            // Create booking
                            scope.launch {
                                isLoading = true
                                val booking = Booking(
                                    userId = authViewModel.getCurrentUser()?.uid ?: "",
                                    carId = selectedCar?.carId ?: "",
                                    carDetails = "${selectedCar?.brand} ${selectedCar?.model} - ${selectedCar?.plateNumber}",
                                    serviceType = selectedService ?: ServiceType.BASIC_WASH,
                                    bookingDate = selectedDate,
                                    timeSlot = selectedTimeSlot,
                                    price = selectedService?.price ?: 0.0,
                                    notes = notes,
                                    status = BookingStatus.PENDING
                                )
                                authViewModel.createBooking(booking)
                                navController.popBackStack()
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = when (step) {
                        1 -> selectedService != null
                        2 -> selectedCar != null
                        3 -> selectedTimeSlot.isNotEmpty()
                        else -> true
                    }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (step < 4) "Next" else "Confirm Booking")
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceSelectionCard(
    service: ServiceType,
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
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = service.duration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Text(
                text = "₱${service.price}",
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