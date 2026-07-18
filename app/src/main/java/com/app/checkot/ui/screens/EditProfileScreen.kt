package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.app.checkot.ui.components.BackTopAppBar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    bookingViewModel: BookingViewModel = viewModel()
) {
    val userData by authViewModel.currentUserData.collectAsState()
    val scope = rememberCoroutineScope()
    var fullName by remember { mutableStateOf(userData?.fullName ?: "") }
    var phoneNumber by remember { mutableStateOf(userData?.phoneNumber ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Identity is locked while a booking is live: the owner reads the customer's
    // *current* name and phone for active jobs (OwnerBookingsTab looks names up
    // from the live users list), so changing them mid-service would desync the
    // owner's view. Editing reopens once every booking is completed or cancelled.
    val userBookings by bookingViewModel.userBookings.collectAsState()
    val bookingsLoaded by bookingViewModel.userBookingsLoaded.collectAsState()
    val hasActiveBooking = remember(userBookings) {
        userBookings.any {
            it.status == BookingStatus.PENDING ||
                it.status == BookingStatus.CONFIRMED ||
                it.status == BookingStatus.IN_PROGRESS
        }
    }
    val canEditIdentity = bookingsLoaded && !hasActiveBooking
    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Edit Profile",
                onBack = { if (navController.previousBackStackEntry != null) navController.popBackStack() },
                actions = {
                    TextButton(
                        onClick = {
                            isLoading = true
                            saveError = null
                            val updates = mapOf(
                                "fullName" to fullName,
                                "phoneNumber" to phoneNumber
                            )
                            profileViewModel.updateUserProfile(updates) { success, error ->
                                isLoading = false
                                if (success) {
                                    navController.popBackStack()
                                } else {
                                    saveError = error
                                }
                            }
                        },
                        enabled = !isLoading && canEditIdentity
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("Save")
                        }
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Picture
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = userData?.fullName?.first()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Lock notice: name/phone can't change during an active booking
            if (hasActiveBooking) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your name and phone number are locked while you have an active booking. You can edit them once the booking is completed or cancelled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            // Form Fields in Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Full Name Field
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = canEditIdentity
                    )
                    // Phone Number Field
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = canEditIdentity
                    )
                    // Email (read-only)
                    OutlinedTextField(
                        value = userData?.email ?: "",
                        onValueChange = {},
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = false
                    )
                }
            }
            if (saveError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = saveError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Save button at bottom
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        val updates = mapOf(
                            "fullName" to fullName,
                            "phoneNumber" to phoneNumber
                        )
                        profileViewModel.updateUserProfile(updates)
                        isLoading = false
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !isLoading && canEditIdentity
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Changes")
                }
            }
        }
    }
}

