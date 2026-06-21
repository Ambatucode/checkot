package com.app.checkot

import com.app.checkot.service.NotificationHelper
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.app.checkot.navigation.NavigationGraph
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.checkot.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private var pendingBookingId by mutableStateOf<String?>(null)
    private var navReady by mutableStateOf(false)

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

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = viewModel()
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
