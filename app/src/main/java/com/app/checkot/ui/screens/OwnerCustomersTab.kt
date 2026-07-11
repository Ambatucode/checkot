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

// Customers Tab
@Composable
fun OwnerCustomersTab(
    ownerViewModel: OwnerDashboardViewModel,
    paddingValues: PaddingValues
) {
    val allUsers by ownerViewModel.allUsers.collectAsState()
    val allUsersLoaded by ownerViewModel.allUsersLoaded.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = remember(allUsers, searchQuery) {
        if (searchQuery.isEmpty()) {
            allUsers
        } else {
            allUsers.filter {
                it.fullName.contains(searchQuery, ignoreCase = true) ||
                        it.email.contains(searchQuery, ignoreCase = true) ||
                        it.phoneNumber.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search customers...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            singleLine = true
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = allUsers.size.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("Total Customers")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = allUsers.count { it.createdAt > System.currentTimeMillis() - 7 * 86400000 }.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("This Week")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!allUsersLoaded) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (filteredUsers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No customers found")
                    }
                }
            } else {
                items(
                    items = filteredUsers,
                    key = { it.userId }
                ) { user ->
                    CustomerCard(user = user)
                }
            }
        }
    }
}
@Composable
fun CustomerCard(user: CarWashUser) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = user.fullName.first().uppercase(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.fullName, style = MaterialTheme.typography.titleMedium)
                Text(text = user.email, style = MaterialTheme.typography.bodyMedium)
                Text(text = user.phoneNumber, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text(text = "${user.savedCars.size} cars", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
