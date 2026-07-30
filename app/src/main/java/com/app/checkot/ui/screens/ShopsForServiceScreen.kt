package com.app.checkot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.app.checkot.model.ServiceType
import com.app.checkot.model.ShopCustomization
import com.app.checkot.ui.components.ShopLogo
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// One shop that offers the requested service.
private data class ShopForService(
    val shopId: String,
    val name: String,
    val address: String,
    val logoUrl: String
)

/**
 * Lists active shops that offer one specific service (e.g. Exterior Wash),
 * reached from the AI car-check when it detects a dirty exterior/interior.
 * Tapping a shop opens the booking flow with that service pre-selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsForServiceScreen(navController: NavController, serviceTypeName: String) {
    // Resolve the passed enum name; fall back to Exterior Wash if it's somehow
    // invalid so the screen never crashes on a bad argument.
    val serviceType = remember(serviceTypeName) {
        ServiceType.values().find { it.name == serviceTypeName } ?: ServiceType.EXTERIOR_WASH
    }

    var shops by remember { mutableStateOf<List<ShopForService>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serviceType) {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = Firebase.firestore.collection("shop_services").get().await()
                val matches = snapshot.documents.mapNotNull { doc ->
                    val custom = doc.toObject(ShopCustomization::class.java) ?: return@mapNotNull null
                    // Only approved shops, and only those that actually offer this service.
                    if (custom.status != "active") return@mapNotNull null
                    if (custom.services.none { it.serviceName == serviceType.name }) return@mapNotNull null
                    ShopForService(
                        shopId = doc.id,
                        name = custom.shopName,
                        address = custom.shopAddress,
                        logoUrl = custom.logoUrl
                    )
                }
                withContext(Dispatchers.Main) {
                    shops = matches
                    loading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadError = "Could not load shops. Check your connection."
                    loading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shops for ${serviceType.displayName}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                loadError != null -> {
                    EmptyState(
                        icon = Icons.Default.CloudOff,
                        title = "Something went wrong",
                        message = loadError!!
                    )
                }

                shops.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No shops yet",
                        message = "No shops offer ${serviceType.displayName} right now. " +
                            "Check back later or browse all shops from Home."
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "These shops offer ${serviceType.displayName}. " +
                                    "Tap one to book.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        items(shops, key = { it.shopId }) { shop ->
                            ShopCard(
                                shop = shop,
                                serviceName = serviceType.displayName,
                                onClick = {
                                    // Open booking with this service pre-selected.
                                    navController.navigate(
                                        "book_service/${shop.shopId}/${serviceType.name}"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopCard(shop: ShopForService, serviceName: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShopLogo(logoUrl = shop.logoUrl, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shop.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (shop.address.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        shop.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(serviceName) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BoxScope.EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
