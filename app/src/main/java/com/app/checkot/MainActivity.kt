package com.app.checkot

import com.app.checkot.service.NotificationHelper
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.app.checkot.navigation.NavigationGraph
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.checkot.utils.ConnectivityObserver
import com.app.checkot.viewmodel.AuthViewModel
import com.app.checkot.viewmodel.RoleLoadState
import com.app.checkot.ui.theme.CheckotTheme

class MainActivity : ComponentActivity() {

    private var pendingBookingId by mutableStateOf<String?>(null)
    private var navReady by mutableStateOf(false)
    private lateinit var connectivityObserver: ConnectivityObserver

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            println("✅ Notification permission granted")
        } else {
            println("❌ Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Checkot_App)
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        // Create notification channel early
        NotificationHelper.createNotificationChannel(this)

        // Request notification permission for Android 13+
        requestNotificationPermission()

        connectivityObserver = ConnectivityObserver(this)
        connectivityObserver.start()

        setContent {
            CheckotTheme {
                // Kept OUTSIDE the offline guard so the NavHost (and every
                // screen's state) survives while the overlay is shown.
                Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    val roleLoadState by authViewModel.roleLoadState.collectAsState()

                    // RBAC gate: nothing below (NavHost included) is composed
                    // until the signed-in user's role is confirmed, so no
                    // screen of another role can flash or be tapped.
                    when (val roleState = roleLoadState) {
                        is RoleLoadState.Loading -> RoleLoadingScreen()
                        is RoleLoadState.Error -> RoleLoadErrorScreen(
                            message = roleState.message,
                            onRetry = { authViewModel.loadUserData() }
                        )
                        is RoleLoadState.Ready -> {
                            val navController = rememberNavController()
                            val currentUser by authViewModel.currentUserData.collectAsState()

                            // Mark NavHost as ready after first composition
                            LaunchedEffect(Unit) {
                                navReady = true
                            }

                            LaunchedEffect(pendingBookingId, currentUser, navReady) {
                                if (!navReady) return@LaunchedEffect
                                val role = currentUser?.role
                                val bookingId = pendingBookingId
                                if (bookingId != null && role != null) {
                                    try {
                                        if (role == "owner") {
                                            navController.navigate("owner_dashboard") {
                                                launchSingleTop = true
                                            }
                                        } else {
                                            navController.navigate("booking_details/$bookingId") {
                                                launchSingleTop = true
                                            }
                                        }
                                        pendingBookingId = null
                                    } catch (e: Exception) {
                                        println("❌ Navigation from notification failed: ${e.message}")
                                        pendingBookingId = null
                                    }
                                }
                            }

                            NavigationGraph(navController = navController)
                        }
                    }
                }

                // Global internet guard: opaque, tap-consuming, back-blocking
                // overlay above everything; auto-dismisses when back online.
                val isOnline by connectivityObserver.isOnline.collectAsState()
                if (!isOnline) {
                    NoInternetScreen(onRetry = { connectivityObserver.refresh() })
                }
                }
            }
        }
    }

    override fun onDestroy() {
        connectivityObserver.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val bookingId = intent?.getStringExtra("bookingId")
        if (!bookingId.isNullOrEmpty()) {
            pendingBookingId = bookingId
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private val loadingTaglines = listOf(
    "Getting your ride ready...",
    "Warming up the hose...",
    "Sudsing things up...",
    "Making your car shine...",
    "Queuing up the clean...",
    "One spotless ride coming up...",
    "Bubbles incoming...",
    "Prepping the wash bay...",
    "Your car deserves this...",
    "Almost there, just like your car wash..."
)

@Composable
private fun RoleLoadingScreen() {
    val tagline = remember { loadingTaglines.random() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color(0xFF00BFA5))
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoInternetScreen(onRetry: () -> Unit) {
    // Swallow the system back button so nothing underneath can navigate.
    BackHandler {}
    Surface(
        modifier = Modifier
            .fillMaxSize()
            // Consume all taps so the UI underneath is unreachable.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "No Internet Connection",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "CHECKOT requires an internet connection to work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun RoleLoadErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Couldn't load your account",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
