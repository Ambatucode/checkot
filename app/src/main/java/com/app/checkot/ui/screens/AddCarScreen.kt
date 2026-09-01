package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.util.UUID
import com.app.checkot.ui.components.BackTopAppBar
import com.app.checkot.ui.components.AppButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCarScreen(
    navController: NavController,
    carViewModel: CarViewModel = viewModel()
) {
    var plateNumber by remember { mutableStateOf("") }
    var plateError by remember { mutableStateOf<String?>(null) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableStateOf("S") }
    var brandDropdownExpanded by remember { mutableStateOf(false) }
    var isOtherSelected by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val popularBrands = listOf(
        "Toyota", "Honda", "Mitsubishi", "Ford", "Nissan", 
        "Suzuki", "Hyundai", "Isuzu", "Mazda", "Kia", 
        "MG", "Geely", "Yamaha", "Kawasaki", "Other"
    )
    val scope = rememberCoroutineScope()
    val isLoading by carViewModel.isLoading.collectAsState()
    val savedCars by carViewModel.savedCars.collectAsState()
    val carLimitReached = savedCars.size >= 3
    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Add New Car",
                onBack = { if (navController.previousBackStackEntry != null) navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (brand.isNotBlank() || model.isNotBlank()) "${brand.trim()} ${model.trim()}" else "Car Name Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (plateNumber.isNotBlank()) "Plate: ${plateNumber.trim()}" else "Plate Number Preview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (color.isNotBlank()) {
                                Text(
                                    text = "Color: ${color.trim()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Text(
                                text = "Size: ${CarSize.fromKey(selectedSize).label} (${selectedSize})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            // Plate Number
            OutlinedTextField(
                value = plateNumber,
                onValueChange = { 
                    if (it.length <= 15) {
                        val input = it.uppercase()
                        plateNumber = input
                        val platePattern = "^[A-Z]{3}\\s?\\d{3,4}$|^\\d{3}\\s?[A-Z]{3}$|^[A-Z]{2}\\s?\\d{4,5}$|^[A-Z]\\d{5}$".toRegex()
                        plateError = if (input.isEmpty()) {
                            "Plate number cannot be empty"
                        } else if (!input.matches(platePattern)) {
                            "Invalid Philippine plate format (e.g., ABC 123, ABC 1234, 123 ABC)"
                        } else {
                            null
                        }
                    }
                },
                label = { Text("Plate Number") },
                leadingIcon = { Icon(Icons.Default.LocalPolice, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                isError = plateError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            if (plateError != null) {
                Text(
                    text = plateError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Start).padding(start = 16.dp)
                )
            }
            // Brand Dropdown Selection
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (isOtherSelected && brand.isEmpty()) "" else brand,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select Brand") },
                    leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { 
                        Icon(
                            imageVector = if (brandDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                // Invisible click overlay covering the selector box to open menu
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(enabled = !isLoading, onClickLabel = "Select brand") { brandDropdownExpanded = true }
                        .semantics {
                            contentDescription = if (brand.isNotEmpty()) "Brand: $brand" else "Select brand"
                            role = Role.Button
                        }
                )
                // Centered anchor for the DropdownMenu positioned at the bottom-center
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(1.dp)
                ) {
                    DropdownMenu(
                        expanded = brandDropdownExpanded,
                        onDismissRequest = { brandDropdownExpanded = false },
                        offset = DpOffset(x = (-140).dp, y = 0.dp),
                        modifier = Modifier
                            .width(280.dp)
                            .heightIn(max = 240.dp) // limit height to prevent screen overflow/shift
                    ) {
                        popularBrands.forEach { selection ->
                            val isSelected = (selection == "Other" && isOtherSelected) || (selection == brand && !isOtherSelected)
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(selection)
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (selection == "Other") {
                                        isOtherSelected = true
                                        brand = ""
                                    } else {
                                        isOtherSelected = false
                                        brand = selection
                                    }
                                    brandDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            // Show custom text input if "Other" is selected
            if (isOtherSelected) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { 
                        if (it.length <= 30) brand = it 
                    },
                    label = { Text("Type Custom Brand") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
            }
            // Model
            OutlinedTextField(
                value = model,
                onValueChange = { 
                    if (it.length <= 30) model = it 
                },
                label = { Text("Model") },
                leadingIcon = { Icon(Icons.Default.CarRental, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            // Color
            OutlinedTextField(
                value = color,
                onValueChange = { 
                    if (it.length <= 30) color = it 
                },
                label = { Text("Color (Optional)") },
                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                )
            )
            // Vehicle Size Selector
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Vehicle Size",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CarSize.values().forEach { sizeEnum ->
                        FilterChip(
                            selected = selectedSize == sizeEnum.sizeKey,
                            onClick = { selectedSize = sizeEnum.sizeKey },
                            label = { Text("${sizeEnum.sizeKey} - ${sizeEnum.label}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedSize == sizeEnum.sizeKey,
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                selectedBorderWidth = 1.dp
                            )
                        )
                    }
                }
            }
            // Default Car Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Set as default car",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isDefault,
                    onCheckedChange = { isDefault = it },
                    enabled = !isLoading
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (carLimitReached) {
                Text(
                    text = "You can only save up to 3 cars per account.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (saveError != null) {
                Text(
                    text = saveError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // Add Car Button
            AppButton(
                text = "Add Car",
                onClick = {
                    val newCar = Car(
                        carId = UUID.randomUUID().toString(),
                        plateNumber = plateNumber.trim(),
                        brand = brand.trim(),
                        model = model.trim(),
                        color = color.trim(),
                        size = selectedSize,
                        isDefault = isDefault
                    )
                    saveError = null
                    carViewModel.addCar(newCar) { success, error ->
                        if (success) {
                            navController.popBackStack()
                        } else {
                            saveError = error
                        }
                    }
                },
                enabled = plateNumber.isNotBlank() && plateError == null && brand.isNotBlank() && model.isNotBlank() && !carLimitReached,
                isLoading = isLoading
            )
        }
    }
}

