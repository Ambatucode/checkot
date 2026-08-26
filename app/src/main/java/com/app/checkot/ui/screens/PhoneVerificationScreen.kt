package com.app.checkot.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
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
    authViewModel: AuthViewModel = viewModel()
) {
    val isChange = mode == "change"
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val verifyState by authViewModel.phoneVerifyState.collectAsState()
    val currentUser by authViewModel.currentUserData.collectAsState()

    // Prefill the signup gate with any number already on the profile (e.g. from the
    // email signup form); the change flow starts blank.
    var localDigits by remember(currentUser?.phoneNumber, isChange) {
        mutableStateOf(
            if (!isChange) currentUser?.phoneNumber?.removePrefix("+63")?.filter { it.isDigit() }?.take(10) ?: ""
            else ""
        )
    }
    var code by remember { mutableStateOf("") }

    // We're in the code-entry step once a code has been sent (stays there while
    // verifying, and while showing a "wrong code" error so the field remains).
    val awaitingCode = verifyState is PhoneVerifyState.CodeSent ||
        verifyState is PhoneVerifyState.Verifying ||
        (verifyState is PhoneVerifyState.Error && authViewModel.hasPendingCode())
    val busy = verifyState is PhoneVerifyState.Sending || verifyState is PhoneVerifyState.Verifying

    var timerSeconds by remember { mutableStateOf(60) }
    var isTimerActive by remember { mutableStateOf(false) }
    var resendTrigger by remember { mutableStateOf(0) }

    // Start a 60-second countdown whenever we transition into the code entry step,
    // or when the user triggers a resend.
    LaunchedEffect(awaitingCode, resendTrigger) {
        if (awaitingCode) {
            timerSeconds = 60
            isTimerActive = true
            while (timerSeconds > 0) {
                kotlinx.coroutines.delay(1000)
                timerSeconds--
            }
            isTimerActive = false
        }
    }

    // Fresh start each time this screen opens.
    LaunchedEffect(Unit) { authViewModel.resetPhoneVerify() }

    // On success, route out of the screen.
    LaunchedEffect(verifyState) {
        if (verifyState is PhoneVerifyState.Success) {
            if (isChange) {
                navController.popBackStack()
            } else {

                val dest = when {
                    currentUser?.role == "admin" -> "admin_dashboard"
                    currentUser?.role == "owner" -> "owner_dashboard"
                    currentUser?.fullName == "New User" || currentUser?.email.isNullOrEmpty() -> "complete_profile"
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
                title = { Text(if (isChange) "Change phone number" else "Verify your number") },
                navigationIcon = {
                    if (isChange) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
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
                text = if (!isChange && !awaitingCode)
                    "We'll text a code to confirm this number is really yours. Every account needs a verified number."
                else if (isChange && !awaitingCode)
                    "Enter your new number — we'll text a code to verify it before switching."
                else
                    "Enter the 6-digit code we sent to +63$localDigits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (!awaitingCode) {
                // --- Step 1: enter number ---
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
                        text = "Firebase SMS gateway works best with Globe, Smart, TM, and TNT. GOMO/DITO numbers may experience delivery delays due to gateway routing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                val validNumber = localDigits.length == 10 && localDigits.startsWith("9")
                Spacer(Modifier.height(24.dp))
                AppButton(
                    text = "Send code",
                    onClick = {
                        val act = activity
                        if (act != null) {
                            authViewModel.startPhoneVerification(act, "+63$localDigits", mode)
                        }
                    },
                    enabled = validNumber && activity != null,
                    isLoading = busy
                )
            } else {
                // --- Step 2: enter code ---
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("6-digit code") },
                    singleLine = true,
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )
                Spacer(Modifier.height(24.dp))
                AppButton(
                    text = "Verify",
                    onClick = { authViewModel.confirmPhoneCode(code) },
                    enabled = code.length == 6,
                    isLoading = busy
                )
                Spacer(Modifier.height(8.dp))
                if (isTimerActive) {
                    Text(
                        text = "Resend code in ${timerSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    TextButton(
                        onClick = {
                            val act = activity
                            if (act != null) {
                                authViewModel.startPhoneVerification(act, "+63$localDigits", mode)
                                resendTrigger++
                            }
                        },
                        enabled = !busy && activity != null
                    ) {
                        Text("Resend code", color = Color(0xFF00E6C3), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { authViewModel.resetPhoneVerify(); code = "" },
                    enabled = !busy
                ) { Text("Use a different number") }
            }

            if (verifyState is PhoneVerifyState.Error) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = (verifyState as PhoneVerifyState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            // Signup gate has no back button — give a way out that doesn't bypass it.
            if (!isChange) {
                TextButton(onClick = {
                    authViewModel.signOut()
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                }) { Text("Sign in with a different account") }
            }
        }
    }
}
