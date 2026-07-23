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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSignupScreen(
    onNavigateToLogin: () -> Unit,
    onSignupSuccess: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var shopName by remember { mutableStateOf("") }
    var shopAddress by remember { mutableStateOf("") }
    var shopLocation by remember { mutableStateOf<com.google.android.gms.maps.model.LatLng?>(null) }

    var firstNameError by remember { mutableStateOf<String?>(null) }
    var lastNameError by remember { mutableStateOf<String?>(null) }
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
            // Header
            Text(
                text = "Register Your Shop",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create an owner account to manage your car wash",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            // ── Shop Section ──
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Shop Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    if (shopAddressError != null) {
                        Text(
                            text = shopAddressError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    // Shop location on the map — required. Admins use it to verify
                    // the shop is real, and clients use it to find/navigate there.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Shop Location",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Tap the map to drop your shop's pin, or use your current location.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    com.app.checkot.ui.components.LocationPickerMap(
                        location = shopLocation,
                        onLocationChange = { shopLocation = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Your Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // First Name
            OutlinedTextField(
                value = firstName,
                onValueChange = { input ->
                    if (input.length <= 50) {
                        firstName = input
                        firstNameError = when {
                            input.trim().isEmpty() -> "First name is required"
                            !input.matches(nameAllowedPattern) -> "Only letters, spaces, ñ, and accents are allowed"
                            else -> null
                        }
                    }
                },
                label = { Text("First Name") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = authState != AuthState.Loading,
                isError = firstNameError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
            )
            if (firstNameError != null) {
                Text(
                    text = firstNameError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Last Name
            OutlinedTextField(
                value = lastName,
                onValueChange = { input ->
                    if (input.length <= 50) {
                        lastName = input
                        lastNameError = when {
                            input.trim().isEmpty() -> "Last name is required"
                            !input.matches(nameAllowedPattern) -> "Only letters, spaces, ñ, and accents are allowed"
                            else -> null
                        }
                    }
                },
                label = { Text("Last Name") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = authState != AuthState.Loading,
                isError = lastNameError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
            )
            if (lastNameError != null) {
                Text(
                    text = lastNameError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

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
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = authState != AuthState.Loading,
                isError = emailError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            if (emailError != null) {
                Text(
                    text = emailError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

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
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = authState != AuthState.Loading,
                isError = phoneError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            if (phoneError != null) {
                Text(
                    text = phoneError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

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
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = authState != AuthState.Loading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                isError = passwordError != null
            )
            if (passwordError != null) {
                Text(
                    text = passwordError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Confirm Password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = authState != AuthState.Loading,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                isError = confirmPassword.isNotEmpty() && password != confirmPassword
            )
            if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                Text(
                    text = "Passwords do not match",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
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
            Button(
                onClick = {
                    val trimmedFirst = firstName.trim()
                    val trimmedLast = lastName.trim()
                    val trimmedShopName = shopName.trim()
                    val trimmedAddress = shopAddress.trim()
                    if (trimmedFirst.isNotEmpty() &&
                        trimmedLast.isNotEmpty() &&
                        email.isNotEmpty() &&
                        phoneNumber.isNotEmpty() &&
                        password.isNotEmpty() &&
                        password == confirmPassword &&
                        trimmedShopName.isNotEmpty() &&
                        trimmedAddress.isNotEmpty() &&
                        shopLocation != null &&
                        firstNameError == null &&
                        lastNameError == null &&
                        passwordError == null &&
                        emailError == null &&
                        phoneError == null &&
                        shopNameError == null &&
                        shopAddressError == null
                    ) {
                        val fullMergedName = "$trimmedFirst $trimmedLast"
                        authViewModel.signUpOwner(
                            email = email.trim(),
                            password = password,
                            fullName = fullMergedName,
                            phoneNumber = "+63${phoneNumber.trim()}",
                            shopName = trimmedShopName,
                            shopAddress = trimmedAddress,
                            latitude = shopLocation!!.latitude,
                            longitude = shopLocation!!.longitude
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = authState != AuthState.Loading &&
                        firstName.trim().isNotEmpty() &&
                        lastName.trim().isNotEmpty() &&
                        email.isNotEmpty() &&
                        phoneNumber.isNotEmpty() &&
                        password.isNotEmpty() &&
                        password == confirmPassword &&
                        shopName.trim().isNotEmpty() &&
                        shopAddress.trim().isNotEmpty() &&
                        shopLocation != null &&
                        firstNameError == null &&
                        lastNameError == null &&
                        passwordError == null &&
                        emailError == null &&
                        phoneError == null &&
                        shopNameError == null &&
                        shopAddressError == null
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Register Shop",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
                    color = MaterialTheme.colorScheme.primary,
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
