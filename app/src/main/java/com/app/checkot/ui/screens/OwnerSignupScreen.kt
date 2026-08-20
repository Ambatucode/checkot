package com.app.checkot.ui.screens

import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.maps.model.LatLng
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.app.checkot.ui.components.AppButton

private fun resolveWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (resId != 0) context.getString(resId) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSignupScreen(
    onNavigateToLogin: () -> Unit,
    onSignupSuccess: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
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
                    authViewModel.signInWithGoogle(googleCred.idToken, isOwnerMode = true)
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
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    var shopName by remember { mutableStateOf("") }
    var shopAddress by remember { mutableStateOf("") }
    var shopLocation by remember { mutableStateOf<LatLng?>(null) }
    var showMapPicker by remember { mutableStateOf(false) }

    var fullNameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var shopNameError by remember { mutableStateOf<String?>(null) }
    var shopAddressError by remember { mutableStateOf<String?>(null) }

    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.clearError()
    }
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> onSignupSuccess()
            else -> {}
        }
    }

    val scrollState = rememberScrollState()
    val nameAllowedPattern = "^[a-zA-Z\\u00D1\\u00F1\\u00C0-\\u00FF ]*$".toRegex()
    
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF0F2530),
        unfocusedContainerColor = Color(0xFF0F2530),
        focusedBorderColor = Color(0xFF00E6C3),
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        errorContainerColor = Color(0xFF0F2530),
        disabledContainerColor = Color(0xFF0F2530)
    )

    val isFormValid = fullName.trim().isNotEmpty() &&
            email.isNotEmpty() &&
            phoneNumber.isNotEmpty() &&
            password.isNotEmpty() &&
            shopName.trim().isNotEmpty() &&
            shopAddress.trim().isNotEmpty() &&
            shopLocation != null &&
            fullNameError == null &&
            passwordError == null &&
            emailError == null &&
            phoneError == null &&
            shopNameError == null &&
            shopAddressError == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .navigationBarsPadding(),
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
                text = "Register Your Shop",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E6C3)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create an owner account to manage your car wash",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            // ── Shop Section ──
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2530)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color(0xFF00E6C3).copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Store,
                                    contentDescription = null,
                                    tint = Color(0xFF00E6C3),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Shop Information",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF00E6C3)
                        )
                    }

                    OutlinedTextField(
                        value = shopName,
                        onValueChange = {
                            if (it.length <= 60) shopName = it
                            shopNameError = if (it.trim().isEmpty()) "Shop name is required" else null
                        },
                        label = { Text("Shop Name") },
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = authState != AuthState.Loading,
                        isError = shopNameError != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = textFieldColors
                    )
                    if (shopNameError != null) {
                        Text(
                            text = shopNameError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    OutlinedTextField(
                        value = shopAddress,
                        onValueChange = {
                            if (it.length <= 120) shopAddress = it
                            shopAddressError = if (it.trim().isEmpty()) "Shop address is required" else null
                        },
                        label = { Text("Shop Address") },
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        minLines = 2,
                        maxLines = 3,
                        enabled = authState != AuthState.Loading,
                        isError = shopAddressError != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = textFieldColors
                    )
                    if (shopAddressError != null) {
                        Text(
                            text = shopAddressError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    // Shop Location Picker Row
                    Surface(
                        onClick = { showMapPicker = true },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = null,
                                tint = Color(0xFF00E6C3),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pin Shop Location on Map",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (shopLocation != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF00E6C3).copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF00E6C3),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Coordinates saved",
                                                fontSize = 11.sp,
                                                color = Color(0xFF00E6C3),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                    if (shopLocation == null) {
                        Text(
                            text = "Shop location is required",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Personal Info Section ──
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2530)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color(0xFF00E6C3).copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF00E6C3),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Owner Information",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF00E6C3)
                        )
                    }

                    // Full Name
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
                            Icon(Icons.Default.PersonOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = authState != AuthState.Loading,
                        isError = fullNameError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        colors = textFieldColors
                    )
                    if (fullNameError != null) {
                        Text(
                            text = fullNameError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            val emailPattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
                            emailError = if (it.isEmpty()) "Email cannot be empty"
                            else if (!it.matches(emailPattern)) "Please enter a valid email address"
                            else null
                        },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = authState != AuthState.Loading,
                        isError = emailError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = textFieldColors
                    )
                    if (emailError != null) {
                        Text(
                            text = emailError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    // Phone
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
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        prefix = { Text("+63 ") },
                        placeholder = { Text("9XXXXXXXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = authState != AuthState.Loading,
                        isError = phoneError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        colors = textFieldColors
                    )
                    if (phoneError != null) {
                        Text(
                            text = phoneError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#\$%^&+=!]).{8,}$".toRegex()
                            passwordError = if (it.isEmpty()) "Password cannot be empty"
                            else if (!it.matches(passwordPattern)) "Must be 8+ chars with 1 uppercase, 1 lowercase, 1 number, and 1 special character"
                            else null
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = authState != AuthState.Loading,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        isError = passwordError != null,
                        colors = textFieldColors
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error message
            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Submit Button
            AppButton(
                text = "Register Shop",
                onClick = {
                    val trimmedName = fullName.trim()
                    val trimmedShopName = shopName.trim()
                    val trimmedAddress = shopAddress.trim()
                    if (isFormValid) {
                        authViewModel.signUpOwner(
                            email = email.trim(),
                            password = password,
                            fullName = trimmedName,
                            phoneNumber = "+63${phoneNumber.trim()}",
                            shopName = trimmedShopName,
                            shopAddress = trimmedAddress,
                            latitude = shopLocation!!.latitude,
                            longitude = shopLocation!!.longitude
                        )
                    }
                },
                enabled = isFormValid,
                isLoading = authState is AuthState.Loading,
                icon = Icons.Default.Store
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Or divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = " OR ",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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

    // Map Picker Bottom Sheet
    if (showMapPicker) {
        ModalBottomSheet(
            onDismissRequest = { showMapPicker = false },
            containerColor = com.app.checkot.ui.theme.CheckotCardSurface,
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Pin Shop Location",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Drag the map to precisely locate your shop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    com.app.checkot.ui.components.LocationPickerMap(
                        location = shopLocation,
                        onLocationChange = { shopLocation = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                AppButton(
                    text = "Confirm Location",
                    onClick = { showMapPicker = false },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}
