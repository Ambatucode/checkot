package com.app.checkot.ui.screens

import com.app.checkot.R
import com.app.checkot.model.*
import com.app.checkot.navigation.Screen
import com.app.checkot.viewmodel.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.app.checkot.ui.components.ConfirmDialog
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

// The web OAuth client id is generated into string resources by the google-services
// plugin ONLY after google-services.json contains an oauth_client. Resolve it by name
// at runtime so the app still compiles/runs before that console step is done.
private fun resolveWebClientId(context: Context): String? {
    val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (id != 0) context.getString(id) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    onNavigateToSignup: () -> Unit,
    onLoginSuccess: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val authState by authViewModel.authState.collectAsState()
    val currentUserData by authViewModel.currentUserData.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var googleError by remember { mutableStateOf<String?>(null) }

    fun signInWithGoogle() {
        googleError = null
        val webClientId = resolveWebClientId(context)
        if (webClientId == null) {
            googleError = "Google Sign-In isn't set up yet. Add the app's SHA-1 in Firebase and drop in the new google-services.json."
            return
        }
        // Single, consistent flow: the explicit "Sign in with Google" account picker.
        // (We dropped the one-tap GetGoogleIdOption fallback because it made the UI shift
        // between a bottom sheet and a center dialog and behaved inconsistently.)
        val buttonOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val credentialManager = CredentialManager.create(context)

        scope.launch {
            try {
                val request = GetCredentialRequest.Builder().addCredentialOption(buttonOption).build()
                val cred = credentialManager.getCredential(context, request).credential
                Log.d("GoogleSignIn", "credential class=${cred.javaClass.name} type=${cred.type}")
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    Log.d("GoogleSignIn", "got id token len=${googleCred.idToken.length}, calling Firebase")
                    authViewModel.signInWithGoogle(googleCred.idToken)
                } else {
                    googleError = "Unexpected credential type: ${cred.type}"
                }
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the picker on purpose — no error to show.
            } catch (e: NoCredentialException) {
                googleError = "No Google account is available on this device. Add a Google account in Settings → Accounts, then try again."
            } catch (e: GetCredentialException) {
                Log.e("GoogleSignIn", "GetCredentialException", e)
                googleError = "Google sign-in failed (${e.type}). ${e.message ?: ""}"
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Unexpected", e)
                googleError = "Google sign-in failed: ${e.javaClass.simpleName} ${e.message ?: ""}"
            }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.clearError()
    }

    LaunchedEffect(authState, currentUserData) {
        val user = currentUserData
        if (authState is AuthState.Authenticated && user != null) {
            // Everyone but admins must have a verified phone before entering the app.
            if (user.role != "admin" && !user.phoneVerified) {
                navController.navigate("phone_verification/signup") {
                    popUpTo("login") { inclusive = true }
                }
                return@LaunchedEffect
            }
            when (user.role) {
                "admin" -> navController.navigate("admin_dashboard") {
                    popUpTo("login") { inclusive = true }
                }
                "owner" -> navController.navigate("owner_dashboard") {
                    popUpTo("login") { inclusive = true }
                }
                else -> onLoginSuccess() // goes to home
            }
        }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo — swap res/drawable/logo for your transparent PNG (keep the name `logo`).
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Checkot logo",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(150.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // App Name
        Text(
            text = "Checkot",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Welcome Back!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(48.dp))

        // LOGIN - Email and Password
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            enabled = authState != AuthState.Loading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            enabled = authState != AuthState.Loading,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Forgot Password
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Forgot Password?",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    if (email.isNotEmpty()) {
                        showResetDialog = true
                    }
                },
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Error message
        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Login Button
        Button(
            onClick = {
                authViewModel.signIn(email, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = authState != AuthState.Loading && email.isNotEmpty() && password.isNotEmpty()
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "Login",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Divider with "or"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "  or  ",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Continue with Google
        OutlinedButton(
            onClick = { signInWithGoogle() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = authState != AuthState.Loading
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Continue with Google",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (googleError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = googleError!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign Up Link
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Don't have an account? ",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Sign Up",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    if (authState != AuthState.Loading) {
                        onNavigateToSignup()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Owner Registration Link
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Own a car wash? ",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Register Your Shop",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    if (authState != AuthState.Loading) {
                        navController.navigate(Screen.OwnerSignup.route)
                    }
                }
            )
        }

        // Reset Password Dialog
        if (showResetDialog) {
            ConfirmDialog(
                title = "Reset Password",
                text = "A password reset link will be sent to $email",
                confirmLabel = "Send",
                onConfirm = {
                    authViewModel.resetPassword(email)
                    showResetDialog = false
                },
                onDismiss = { showResetDialog = false }
            )
        }
        }
    }
}
