package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import com.app.checkot.ui.screens.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.util.UUID
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCarScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    var plateNumber by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isLoading by authViewModel.isLoading.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Car") },
                navigationIcon = {
                    IconButton(onClick = { if(navController.previousBackStackEntry != null) navController.popBackStack() }) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Car Icon
            Surface(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Plate Number
            OutlinedTextField(
                value = plateNumber,
                onValueChange = { plateNumber = it.uppercase() },
                label = { Text("Plate Number") },
                leadingIcon = { Icon(Icons.Default.LocalPolice, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            // Brand
            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Brand (e.g., Toyota, Honda)") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            // Model
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model (e.g., Vios, Civic)") },
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
                onValueChange = { color = it },
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
            // Add Car Button
            Button(
                onClick = {
                    val newCar = Car(
                        carId = UUID.randomUUID().toString(),
                        plateNumber = plateNumber,
                        brand = brand,
                        model = model,
                        color = color,
                        isDefault = isDefault
                    )
                    authViewModel.addCar(newCar)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = plateNumber.isNotBlank() && brand.isNotBlank() && model.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Add Car")
                }
            }
        }
    }
}

