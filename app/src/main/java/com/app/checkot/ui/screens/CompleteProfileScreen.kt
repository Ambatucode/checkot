package com.app.checkot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
    var fullNameError by remember { mutableStateOf<String?>(null) }
    
    val currentUser by authViewModel.currentUserData.collectAsState()
    
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    
    val nameAllowedPattern = "^[a-zA-Z\u00D1\u00F1\u00C0-\u00FF ]*$".toRegex()
    
    val isFormValid = fullName.trim().isNotEmpty() && fullNameError == null

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF0F2530),
        unfocusedContainerColor = Color(0xFF0F2530),
        focusedBorderColor = Color(0xFF00E6C3),
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        errorContainerColor = Color(0xFF0F2530),
        disabledContainerColor = Color(0xFF0F2530)
    )

    val scrollState = rememberScrollState()

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
            Text(
                text = "Complete Your Profile",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Please enter your name to complete your registration and start booking services.",
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
                text = "Complete Profile",
                onClick = {
                    if (isFormValid) {
                        isSubmitting = true
                        submitError = null
                        authViewModel.completeProfile(
                            fullName = fullName.trim(),
                            onSuccess = {
                                isSubmitting = false
                                val dest = if (currentUser?.role == "owner") {
                                     Screen.OwnerDashboard.route
                                 } else {
                                     Screen.Home.route
                                 }
                                 navController.navigate(dest) {
                                     popUpTo("complete_profile") { inclusive = true }
                                 }
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
        }
    }
}
