package com.app.checkot.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.app.checkot.ui.components.BackTopAppBar
import com.app.checkot.ui.components.LocationPickerMap
import com.app.checkot.viewmodel.OwnerDashboardViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetShopLocationScreen(
    navController: NavController,
    ownerViewModel: OwnerDashboardViewModel = viewModel()
) {
    val customization by ownerViewModel.shopCustomization.collectAsState()
    var picked by remember { mutableStateOf<LatLng?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Seed the pin from the saved location once it loads (only if not yet placed).
    LaunchedEffect(customization.latitude, customization.longitude) {
        if (picked == null && (customization.latitude != 0.0 || customization.longitude != 0.0)) {
            picked = LatLng(customization.latitude, customization.longitude)
        }
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Shop Location",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Tap the map to place your shop's pin, or use your current location. Clients will see this when they book.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(16.dp)
            )
            LocationPickerMap(
                location = picked,
                onLocationChange = { picked = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Button(
                onClick = {
                    val p = picked ?: return@Button
                    isSaving = true
                    ownerViewModel.saveShopCustomization(
                        customization.copy(latitude = p.latitude, longitude = p.longitude)
                    )
                    scope.launch {
                        kotlinx.coroutines.delay(1200)
                        isSaving = false
                        navController.popBackStack()
                    }
                },
                enabled = picked != null && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Location")
                }
            }
        }
    }
}
