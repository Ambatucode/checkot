package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var showChecklistDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveResult by ownerViewModel.saveResult.collectAsState()

    LaunchedEffect(saveResult) {
        saveResult?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
        }
    }

    val shopCust by ownerViewModel.shopCustomization.collectAsState()
    val shopStatus = shopCust.status
    val userData by authViewModel.currentUserData.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
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
                    // Opens the shared profile screen (shop logo, contact info,
                    // Edit Profile, and Logout) — same pattern as the client home.
                    IconButton(
                        onClick = { navController.navigate("profile") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (shopStatus != "rejected") {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Bookmark, contentDescription = "Book") },
                        label = { Text("Book", fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.People, contentDescription = "Clients") },
                        label = { Text("Clients", fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.AttachMoney, contentDescription = "Stats") },
                        label = { Text("Stats", fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Build, contentDescription = "Menu") },
                        label = { Text("Menu", fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Setup") },
                        label = { Text("Setup", fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChecklistDialog = true },
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
                                    "Complete all requirements for admin review (Tap to view setup checklist)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E6C3),
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Checklist popup dialog
                    if (showChecklistDialog) {
                        val phoneMissing = userData?.phoneVerified != true || userData?.phoneNumber.isNullOrEmpty()
                        val locationMissing = shopCust.latitude == 0.0 && shopCust.longitude == 0.0
                        val profileMissing = shopCust.shopAddress.isBlank()

                        AlertDialog(
                            onDismissRequest = { showChecklistDialog = false },
                            title = { Text("Shop Setup Checklist") },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Complete these 3 requirements so that the administrator can approve your shop and make it visible to clients.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Requirement 1: Phone number
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (!phoneMissing) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (!phoneMissing) Color(0xFF00E6C3) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Verify Phone Number",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (phoneMissing) {
                                            TextButton(
                                                onClick = {
                                                    showChecklistDialog = false
                                                    navController.navigate("phone_verification/signup")
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    text = "Verify Now",
                                                    color = Color(0xFF00E6C3),
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Verified",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF00E6C3),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Requirement 2: Map location
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (!locationMissing) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (!locationMissing) Color(0xFF00E6C3) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Set Shop Location on Map",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (locationMissing) {
                                            TextButton(
                                                onClick = {
                                                    showChecklistDialog = false
                                                    navController.navigate("set_shop_location")
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    text = "Set Now",
                                                    color = Color(0xFF00E6C3),
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Set",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF00E6C3),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Requirement 3: Shop profile
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (!profileMissing) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (!profileMissing) Color(0xFF00E6C3) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Configure Name & Address",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (profileMissing) {
                                            TextButton(
                                                onClick = {
                                                    showChecklistDialog = false
                                                    selectedTab = 4
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    text = "Configure",
                                                    color = Color(0xFF00E6C3),
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Configured",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF00E6C3),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Once you have successfully completed all requirements, the administrator can review and activate your shop.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showChecklistDialog = false }) {
                                    Text("Close", color = Color(0xFF00E6C3))
                                }
                            }
                        )
                    }

                    // Show tabs normally
                    when (selectedTab) {
                        0 -> OwnerBookingsTab(navController, ownerViewModel, PaddingValues(0.dp))
                        1 -> OwnerCustomersTab(ownerViewModel, PaddingValues(0.dp))
                        2 -> OwnerRevenueTab(ownerViewModel, PaddingValues(0.dp))
                        3 -> OwnerServicesTab(ownerViewModel, PaddingValues(0.dp), navController)
                        4 -> OwnerSettingsTab(ownerViewModel, PaddingValues(0.dp), navController)
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
                        3 -> OwnerServicesTab(ownerViewModel, PaddingValues(0.dp), navController)
                        4 -> OwnerSettingsTab(ownerViewModel, PaddingValues(0.dp), navController)
                    }
                }
            }
        }
    }
}
