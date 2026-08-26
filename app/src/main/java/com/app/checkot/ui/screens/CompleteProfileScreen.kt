package com.app.checkot.ui.screens

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.app.checkot.ui.components.AppButton
import com.app.checkot.viewmodel.AuthViewModel
import com.app.checkot.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    
    var isPendingVerification by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    
    var resendTimer by remember { mutableStateOf(0) }
    
    val nameAllowedPattern = "^[a-zA-Z\u00D1\u00F1\u00C0-\u00FF ]*$".toRegex()
    
    val isFormValid = fullName.trim().isNotEmpty() &&
            email.isNotEmpty() &&
            fullNameError == null &&
            emailError == null

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF0F2530),
        unfocusedContainerColor = Color(0xFF0F2530),
        focusedBorderColor = Color(0xFF00E6C3),
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        errorContainerColor = Color(0xFF0F2530),
        disabledContainerColor = Color(0xFF0F2530)
    )

    val scrollState = rememberScrollState()

    // Resend countdown timer logic
    LaunchedEffect(isPendingVerification, resendTimer) {
        if (isPendingVerification && resendTimer > 0) {
            while (resendTimer > 0) {
                kotlinx.coroutines.delay(1000)
                resendTimer--
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1921))
            .verticalScroll(scrollState)
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isPendingVerification) {
                // ========================================================
                // STATE 1: Profile Details Form
                // ========================================================
                Text(
                    text = "Complete Your Profile",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Please enter your name and email. The email is a fallback recovery option in case you cannot receive SMS verification codes.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Full Name Input
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { input ->
                        fullName = input
                        fullNameError = when {
                            input.trim().isEmpty() -> "Full name is required"
                            !nameAllowedPattern.matches(input) -> "Only letters and spaces are allowed"
                            else -> null
                        }
                    },
                    label = { Text("Full Name") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    isError = fullNameError != null,
                    supportingText = fullNameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    enabled = !isSubmitting,
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Email Input
                OutlinedTextField(
                    value = email,
                    onValueChange = { input ->
                        email = input.trim()
                        emailError = when {
                            input.isEmpty() -> "Email address is required"
                            !Patterns.EMAIL_ADDRESS.matcher(input.trim()).matches() -> "Invalid email address format"
                            else -> null
                        }
                    },
                    label = { Text("Email Address") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    enabled = !isSubmitting,
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    )
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (submitError != null) {
                    Text(
                        text = submitError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                AppButton(
                    text = "Continue",
                    onClick = {
                        if (isFormValid) {
                            isSubmitting = true
                            submitError = null
                            authViewModel.startEmailVerification(
                                email = email.trim(),
                                onSuccess = {
                                    isSubmitting = false
                                    isPendingVerification = true
                                    resendTimer = 60
                                },
                                onFailure = { err ->
                                    isSubmitting = false
                                    submitError = err
                                }
                            )
                        }
                    },
                    enabled = isFormValid && !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // ========================================================
                // STATE 2: Pending Email Verification Check Screen
                // ========================================================
                Text(
                    text = "Verify Your Email",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "We've sent a verification link to:",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = email,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Please check your inbox (and spam folder). Click the verification link in the email, then tap the button below to complete registration.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (submitError != null) {
                    Text(
                        text = submitError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // I've Verified Button
                AppButton(
                    text = "I've Verified",
                    onClick = {
                        isSubmitting = true
                        submitError = null
                        authViewModel.checkEmailVerification(
                            fullName = fullName.trim(),
                            email = email.trim(),
                            onSuccess = {
                                isSubmitting = false
                                navController.navigate(Screen.Home.route) {
                                    popUpTo("complete_profile") { inclusive = true }
                                }
                            },
                            onFailure = { err ->
                                isSubmitting = false
                                submitError = err
                            }
                        )
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Resend Email Button (with 60-second cooldown)
                OutlinedButton(
                    onClick = {
                        isSubmitting = true
                        submitError = null
                        authViewModel.resendVerificationEmail(
                            email = email.trim(),
                            onSuccess = {
                                isSubmitting = false
                                resendTimer = 60
                                submitError = "Verification email resent successfully."
                            },
                            onFailure = { err ->
                                isSubmitting = false
                                submitError = err
                            }
                        )
                    },
                    enabled = resendTimer == 0 && !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (resendTimer == 0) Color(0xFF00E6C3) else Color.Gray)
                ) {
                    Text(
                        text = if (resendTimer > 0) "Resend Email in ${resendTimer}s" else "Resend Email",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Change Email option (Return to State 1)
                TextButton(
                    onClick = {
                        isPendingVerification = false
                        submitError = null
                    },
                    enabled = !isSubmitting
                ) {
                    Text(
                        text = "Change Email / Name",
                        color = Color(0xFF00E6C3),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
