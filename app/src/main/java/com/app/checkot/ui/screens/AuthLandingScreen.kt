package com.app.checkot.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.app.checkot.R
import com.app.checkot.viewmodel.AuthState
import com.app.checkot.viewmodel.AuthViewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import com.app.checkot.ui.components.TypewriterText

private fun resolveWebClientId(context: Context): String? {
    val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (id != 0) context.getString(id) else null
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun AuthLandingScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    var isOwnerMode by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val authState by authViewModel.authState.collectAsState()
    val currentUser by authViewModel.currentUserData.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    fun signInWithGoogle() {
        googleError = null
        val webClientId = resolveWebClientId(context)
        if (webClientId == null) {
            googleError = "Google Sign-In isn't set up yet."
            return
        }
        val buttonOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val credentialManager = CredentialManager.create(context)
        scope.launch {
            try {
                val request = GetCredentialRequest.Builder().addCredentialOption(buttonOption).build()
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    authViewModel.signInWithGoogle(googleCred.idToken, isOwnerMode)
                } else {
                    googleError = "Unexpected credential type: ${cred.type}"
                }
            } catch (e: GetCredentialCancellationException) {
                // User dismissed
            } catch (e: NoCredentialException) {
                googleError = "No Google account on this device. Add one in Settings."
            } catch (e: GetCredentialException) {
                googleError = "Google sign-in failed: ${e.message ?: ""}"
            } catch (e: Exception) {
                googleError = "Sign-in failed: ${e.message ?: ""}"
            }
        }
    }

    LaunchedEffect(authState, currentUser) {
        val user = currentUser
        if (authState is AuthState.Authenticated && user != null) {

            // Customer or Owner -> go straight to home or owner_dashboard
            val dest = when {
                user.role == "admin" -> "admin_dashboard"
                user.role == "owner" -> "owner_dashboard"
                else -> "home"
            }
            navController.navigate(dest) {
                popUpTo("auth_landing") { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) { authViewModel.clearError() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1921))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Checkot",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(96.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isOwnerMode) "Checkot Business" else "Checkot",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 36.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            TypewriterText(
                text = if (isOwnerMode) "Manage your car wash shop" else "Book your wash in seconds",
                fontSize = 15.sp,
                color = Color(0xFF94A3B8)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = { signInWithGoogle() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0B1921)
                )
            ) {
                Text("G", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF4285F4))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            if (googleError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = googleError!!,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            if (authState is AuthState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = (authState as AuthState.Error).message,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            if (authState is AuthState.Loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF00E6C3), modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.weight(1f))
            
            TextButton(
                onClick = { isOwnerMode = !isOwnerMode },
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    text = if (isOwnerMode) "Looking to book a wash? Tap here"
                           else "Are you a business owner? Tap here",
                    color = Color(0xFF00E6C3),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
