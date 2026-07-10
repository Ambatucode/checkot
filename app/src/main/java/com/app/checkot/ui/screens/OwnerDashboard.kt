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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.app.checkot.ui.components.ConfirmDialog
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
                            imageVector = Icons.AutoMirrored.Filled.Logout,
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
        ConfirmDialog(
            title = "Logout",
            text = "Are you sure you want to logout?",
            confirmLabel = "Yes",
            dismissLabel = "No",
            onConfirm = {
                authViewModel.signOut()
                ownerViewModel.logout {
                    showLogoutDialog = false
                    navController.navigate("login") {
                        popUpTo(0) // Clears all screens from back stack
                    }
                }
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}
