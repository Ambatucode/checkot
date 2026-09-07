package com.app.checkot.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.checkot.ui.components.AppButton
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.app.checkot.viewmodel.AuthViewModel
import com.app.checkot.viewmodel.PhoneVerifyState

/** Walk up the Compose context wrappers to the hosting Activity (needed by Firebase phone auth). */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Verifies a phone number via SMS OTP. Used two ways:
 *  - mode == "signup": the entry gate after a new sign-in (can't be skipped except by
 *    signing out); on success routes the user into the app by role.
 *  - mode == "change": reached from Profile; on success pops back to Profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneVerificationScreen(
    navController: NavController,
    mode: String,
    isOwner: Boolean = false,
    authViewModel: AuthViewModel = viewModel()
) {
    val isChange = mode == "change"

    val verifyState by authViewModel.phoneVerifyState.collectAsState()
    val currentUser by authViewModel.currentUserData.collectAsState()

    var localDigits by remember(currentUser?.phoneNumber, isChange) {
        mutableStateOf(
            currentUser?.phoneNumber?.removePrefix("+63")?.filter { it.isDigit() }?.take(10) ?: ""
        )
    }

    val busy = verifyState is PhoneVerifyState.Verifying

    // Fresh start each time this screen opens.
    LaunchedEffect(Unit) { authViewModel.resetPhoneVerify() }

    // On success, route out of the screen.
    LaunchedEffect(verifyState) {
        if (verifyState is PhoneVerifyState.Success) {
            if (isChange || navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                val dest = when {
                    currentUser?.role == "admin" -> "admin_dashboard"
                    currentUser?.role == "owner" -> "owner_dashboard"
                    currentUser?.fullName == "New User" -> "complete_profile"
                    else -> "home"
                }
                navController.navigate(dest) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isChange) "Change phone number" else "Set phone number") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Icon(
                Icons.Default.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Enter your 10-digit mobile number below to update your profile contact information.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = localDigits,
                onValueChange = { input -> localDigits = input.filter { it.isDigit() }.take(10) },
                label = { Text("Phone Number") },
                prefix = { Text("+63 ") },
                placeholder = { Text("9XXXXXXXXX") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Your phone number allows carwash owners and customers to communicate easily regarding bookings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            val validNumber = localDigits.length == 10 && localDigits.startsWith("9")
            Spacer(Modifier.height(24.dp))
            AppButton(
                text = "Save phone number",
                onClick = {
                    authViewModel.savePhoneNumberDirect("+63$localDigits")
                },
                enabled = validNumber,
                isLoading = busy
            )

            if (verifyState is PhoneVerifyState.Error) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = (verifyState as PhoneVerifyState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
