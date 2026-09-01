package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import android.content.Context
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.checkot.ui.components.AppButton
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private fun resolveWebClientIdSignup(context: Context): String? {
    val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (id != 0) context.getString(id) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    onSignupSuccess: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        authViewModel.clearError()
    }
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                onSignupSuccess()
            }
            else -> {}
        }
    }

    fun signInWithGoogle() {
        googleError = null
        val webClientId = resolveWebClientIdSignup(context)
        if (webClientId == null) {
            googleError = "Google Sign-In isn't set up yet. Add the app's SHA-1 in Firebase and drop in the new google-services.json."
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
                    authViewModel.signInWithGoogle(googleCred.idToken)
                } else {
                    googleError = "Unexpected credential type: ${cred.type}"
                }
            } catch (_: GetCredentialCancellationException) {
                // User cancelled — do nothing
            } catch (e: NoCredentialException) {
                googleError = "No Google account is available on this device."
            } catch (e: GetCredentialException) {
                Log.e("GoogleSignIn", "GetCredentialException", e)
                googleError = "Google sign-in failed (${e.type}). ${e.message ?: ""}"
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Unexpected", e)
                googleError = "Google sign-in failed: ${e.javaClass.simpleName} ${e.message ?: ""}"
            }
        }
    }

    val scrollState = rememberScrollState()
    // Validation regex: Only letters, spaces, and Filipino/Spanish accents
    val nameAllowedPattern = "^[a-zA-Z\u00D1\u00F1\u00C0-\u00FF ]*$".toRegex()

    val isFormValid = fullName.trim().isNotEmpty() &&
            email.isNotEmpty() &&
            phoneNumber.isNotEmpty() &&
            password.isNotEmpty() &&
            fullNameError == null &&
            phoneError == null &&
            passwordError == null &&
            emailError == null

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF0F2530),
        unfocusedContainerColor = Color(0xFF0F2530),
        focusedBorderColor = Color(0xFF00E6C3),
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        errorContainerColor = Color(0xFF0F2530),
        disabledContainerColor = Color(0xFF0F2530)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Header
        Text(
            text = "Create Account",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00E6C3)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sign up to get started",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Full Name Field
        OutlinedTextField(
            value = fullName,
            onValueChange = { input ->
                if (input.length <= 80) {
                    fullName = input
                    fullNameError = when {
                        input.trim().isEmpty() -> "Full name is required"
                        input.trim().split("\\s+".toRegex()).size < 2 -> "Please enter your first and last name"
                        !input.matches(nameAllowedPattern) -> "Only letters, spaces, and accents are allowed"
                        else -> null
                    }
                }
            },
            label = { Text("Full Name") },
            placeholder = { Text("e.g. Juan Dela Cruz") },
            leadingIcon = {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            enabled = authState != AuthState.Loading,
            isError = fullNameError != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            colors = textFieldColors
        )
        if (fullNameError != null) {
            Text(
                text = fullNameError!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                val emailPattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
                emailError = if (it.isEmpty()) {
                    "Email cannot be empty"
                } else if (!it.matches(emailPattern)) {
                    "Please enter a valid email address"
                } else {
                    null
                }
            },
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
            isError = emailError != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = textFieldColors
        )
        if (emailError != null) {
            Text(
                text = emailError!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Phone Number Field
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(10)
                phoneNumber = digits
                phoneError = when {
                    digits.isEmpty() -> "Phone number cannot be empty"
                    digits.length != 10 -> "Enter 10 digits after +63 (e.g. 9123456789)"
                    !digits.startsWith("9") -> "Number must start with 9"
                    else -> null
                }
            },
            label = { Text("Phone Number") },
            leadingIcon = {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            prefix = { Text("+63 ") },
            placeholder = { Text("9XXXXXXXXX") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            enabled = authState != AuthState.Loading,
            isError = phoneError != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            colors = textFieldColors
        )
        if (phoneError != null) {
            Text(
                text = phoneError!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#\$%^&+=!]).{8,}$".toRegex()
                passwordError = if (it.isEmpty()) {
                    "Password cannot be empty"
                } else if (!it.matches(passwordPattern)) {
                    "Must be 8+ chars with 1 uppercase, 1 lowercase, 1 number, and 1 special character"
                } else {
                    null
                }
            },
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
            isError = passwordError != null,
            colors = textFieldColors
        )
        if (passwordError != null) {
            Text(
                text = passwordError!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Terms and Conditions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "By signing up, you agree to our ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Terms",
                fontSize = 12.sp,
                color = Color(0xFF00E6C3),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { /* Handle terms click */ }
            )
            Text(
                text = " and ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Privacy Policy",
                fontSize = 12.sp,
                color = Color(0xFF00E6C3),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { /* Handle privacy click */ }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Error message if any
        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Sign Up Button — vibrant teal
        AppButton(
            text = "Sign Up",
            onClick = {
                val trimmedName = fullName.trim()
                if (trimmedName.isNotEmpty() &&
                    email.isNotEmpty() &&
                    phoneNumber.isNotEmpty() &&
                    password.isNotEmpty() &&
                    fullNameError == null &&
                    phoneError == null &&
                    passwordError == null &&
                    emailError == null) {
                    authViewModel.signUp(email.trim(), password, trimmedName, "+63${phoneNumber.trim()}")
                }
            },
            enabled = isFormValid,
            isLoading = authState is AuthState.Loading
        )

        Spacer(modifier = Modifier.height(24.dp))

        // OR divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
            Text(
                text = "  OR  ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Continue with Google
        OutlinedButton(
            onClick = { signInWithGoogle() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            ),
            enabled = authState != AuthState.Loading
        ) {
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
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Login Link
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Already have an account? ",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Login",
                color = Color(0xFF00E6C3),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    if (authState != AuthState.Loading) {
                        onNavigateToLogin()
                    }
                }
            )
        }
        }
    }
}
