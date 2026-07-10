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
                        modifier = Modifier.size(48.dp),
                        enabled = (bayCountText.toIntOrNull() ?: 1) > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease bay count", modifier = Modifier.size(16.dp))
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
                        modifier = Modifier.size(48.dp),
                        enabled = (bayCountText.toIntOrNull() ?: 1) < 10
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase bay count", modifier = Modifier.size(16.dp))
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
                    .padding(32.dp),
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
