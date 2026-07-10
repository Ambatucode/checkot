package com.app.checkot.ui.screens

import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

private enum class AdminDialogType { APPROVE, REJECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    adminViewModel: SuperAdminViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val userData by authViewModel.currentUserData.collectAsState()
    val pendingShops by adminViewModel.pendingShops.collectAsState()
    val activeShops by adminViewModel.activeShops.collectAsState()
    val rejectedShops by adminViewModel.rejectedShops.collectAsState()
    val isLoading by adminViewModel.isLoading.collectAsState()

    // Initial load
    LaunchedEffect(Unit) {
        adminViewModel.loadShops()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin Dashboard", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Super Admin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { adminViewModel.loadShops() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        BadgedBox(badge = {
                            if (pendingShops.isNotEmpty()) {
                                Badge { Text(pendingShops.size.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = "Pending")
                        }
                    },
                    label = { Text("Pending") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Active") },
                    label = { Text("Active") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Cancel, contentDescription = "Rejected") },
                    label = { Text("Rejected") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                selectedTab == 0 -> PendingShopsTab(pendingShops, adminViewModel)
                selectedTab == 1 -> ActiveShopsTab(activeShops)
                selectedTab == 2 -> RejectedShopsTab(rejectedShops)
            }
        }
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authViewModel.signOut()
                    navController.navigate("login") {
                        popUpTo(0)
                    }
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("No") }
            }
        )
    }
}

@Composable
private fun PendingShopsTab(
    pendingShops: List<ShopWithOwner>,
    adminViewModel: SuperAdminViewModel
) {
    if (pendingShops.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("All caught up!", style = MaterialTheme.typography.titleLarge)
                Text(
                    "No pending shops to review",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "${pendingShops.size} shop${if (pendingShops.size > 1) "s" else ""} pending approval",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(pendingShops, key = { it.shopId }) { shop ->
                PendingShopCard(
                    shop = shop,
                    verifyPassword = { password, callback -> adminViewModel.verifyPassword(password, callback) },
                    onApprove = { callback -> adminViewModel.approveShop(shop.shopId, callback) },
                    onReject = { callback -> adminViewModel.rejectShop(shop.shopId, callback) }
                )
            }
        }
    }
}

@Composable
private fun PendingShopCard(
    shop: ShopWithOwner,
    verifyPassword: (password: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onApprove: (onResult: (Boolean) -> Unit) -> Unit,
    onReject: (onResult: (Boolean) -> Unit) -> Unit
) {
    var activeDialog by remember { mutableStateOf<AdminDialogType?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // Shared password confirmation dialog for both Approve and Reject
    activeDialog?.let { type ->
        val title = if (type == AdminDialogType.APPROVE) "Approve Shop" else "Reject Shop"
        val verb = if (type == AdminDialogType.APPROVE) "approve" else "reject"
        val actionColor = if (type == AdminDialogType.APPROVE) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.error

        AlertDialog(
            onDismissRequest = {
                if (!isVerifying) {
                    activeDialog = null
                    password = ""
                    passwordError = null
                }
            },
            title = { Text(title) },
            text = {
                Column {
                    Text("Are you sure you want to $verb \"${shop.shopName}\"?")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        label = { Text("Enter your password to confirm") },
                        singleLine = true,
                        enabled = !isVerifying,
                        isError = passwordError != null,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (password.isEmpty()) {
                            passwordError = "Please enter your password"
                            return@TextButton
                        }
                        isVerifying = true
                        passwordError = null
                        verifyPassword(password) { success, errorMsg ->
                            if (success) {
                                password = ""
                                passwordError = null
                                isVerifying = false
                                activeDialog = null
                                isProcessing = true
                                if (type == AdminDialogType.APPROVE) {
                                    onApprove { isProcessing = false }
                                } else {
                                    onReject { isProcessing = false }
                                }
                            } else {
                                isVerifying = false
                                passwordError = errorMsg
                            }
                        }
                    },
                    enabled = !isVerifying,
                    colors = ButtonDefaults.textButtonColors(contentColor = actionColor)
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (type == AdminDialogType.APPROVE) "Yes, Approve" else "Yes, Reject")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        activeDialog = null
                        password = ""
                        passwordError = null
                    },
                    enabled = !isVerifying
                ) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Shop header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        shop.shopName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        shop.shopAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Owner info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Owner: ${shop.ownerName}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    shop.ownerEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { activeDialog = AdminDialogType.REJECT },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }
                Button(
                    onClick = { activeDialog = AdminDialogType.APPROVE },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveShopsTab(activeShops: List<ShopWithOwner>) {
    if (activeShops.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Store,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No active shops", style = MaterialTheme.typography.titleLarge)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "${activeShops.size} active shop${if (activeShops.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(activeShops, key = { it.shopId }) { shop ->
                ShopInfoCard(shop = shop, statusColor = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RejectedShopsTab(rejectedShops: List<ShopWithOwner>) {
    if (rejectedShops.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No rejected shops", style = MaterialTheme.typography.titleLarge)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "${rejectedShops.size} rejected shop${if (rejectedShops.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(rejectedShops, key = { it.shopId }) { shop ->
                ShopInfoCard(shop = shop, statusColor = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ShopInfoCard(shop: ShopWithOwner, statusColor: androidx.compose.ui.graphics.Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Store,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shop.shopName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    shop.shopAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    "Owner: ${shop.ownerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
