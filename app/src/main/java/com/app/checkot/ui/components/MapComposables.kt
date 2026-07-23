package com.app.checkot.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

// Fallback map center when no location is set yet — Valenzuela City, around the
// Ugong / Gen. T. de Leon area (the project's locale). This is only the initial
// camera position; no pin is placed here — the owner still taps to set the real one.
private val DEFAULT_LOCATION = LatLng(14.6850, 120.9980)
private const val DEFAULT_ZOOM = 13.5f

/**
 * Interactive map for picking a location: tap the map to drop/move the pin, or
 * tap "Use my location" to fill it from GPS. The caller owns the [location]
 * state and is notified of changes via [onLocationChange]. Caller sets the
 * size via [modifier] (e.g. a fixed height or weight).
 */
@Composable
fun LocationPickerMap(
    location: LatLng?,
    onLocationChange: (LatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            location ?: DEFAULT_LOCATION,
            if (location != null) 16f else DEFAULT_ZOOM
        )
    }
    // Recenter when the pin is set programmatically (e.g. from current location).
    androidx.compose.runtime.LaunchedEffect(location) {
        location?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
        }
    }

    val onUseMyLocation = rememberUseMyLocation(context, onLocationChange)

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { onLocationChange(it) },
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            location?.let {
                Marker(
                    // Key on the position so the marker follows each new tap.
                    state = rememberMarkerState(key = it.toString(), position = it),
                    title = "Shop location"
                )
            }
        }
        FilledTonalButton(
            onClick = onUseMyLocation,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Use my location")
        }
    }
}

/**
 * Read-only map showing a shop's location with a marker, plus an optional
 * "Get Directions" button that opens Google Maps. Map gestures are disabled so
 * it doesn't fight the surrounding scroll — clients tap Directions to navigate.
 */
@Composable
fun ShopLocationView(
    latitude: Double,
    longitude: Double,
    shopName: String,
    modifier: Modifier = Modifier,
    showDirectionsButton: Boolean = true
) {
    val context = LocalContext.current
    val shopLatLng = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(shopLatLng, 16f)
    }
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            GoogleMap(
                modifier = Modifier.matchParentSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    scrollGesturesEnabled = false,
                    zoomGesturesEnabled = false,
                    zoomControlsEnabled = false,
                    tiltGesturesEnabled = false,
                    rotationGesturesEnabled = false,
                    mapToolbarEnabled = false
                )
            ) {
                Marker(
                    state = rememberMarkerState(key = shopLatLng.toString(), position = shopLatLng),
                    title = shopName
                )
            }
        }
        if (showDirectionsButton) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openDirections(context, latitude, longitude) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get Directions")
            }
        }
    }
}

/** Opens turn-by-turn directions to the location in Google Maps (no API key needed). */
fun openDirections(context: Context, lat: Double, lng: Double) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
        )
    } catch (e: Exception) {
        // Google Maps not installed — fall back to any maps/browser handler.
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

/**
 * Returns a callback that fills the current location from GPS, requesting the
 * location permission first if it isn't granted yet.
 */
@Composable
private fun rememberUseMyLocation(
    context: Context,
    onResult: (LatLng) -> Unit
): () -> Unit {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchLastLocation(context, onResult)
        else Toast.makeText(context, "Location permission denied — tap the map instead.", Toast.LENGTH_SHORT).show()
    }
    return {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) fetchLastLocation(context, onResult)
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

@SuppressLint("MissingPermission")
private fun fetchLastLocation(context: Context, onResult: (LatLng) -> Unit) {
    LocationServices.getFusedLocationProviderClient(context).lastLocation
        .addOnSuccessListener { loc ->
            if (loc != null) onResult(LatLng(loc.latitude, loc.longitude))
            else Toast.makeText(context, "Couldn't get current location. Tap the map instead.", Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener {
            Toast.makeText(context, "Location unavailable. Tap the map instead.", Toast.LENGTH_SHORT).show()
        }
}
