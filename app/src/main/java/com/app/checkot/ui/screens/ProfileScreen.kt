package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.app.checkot.ui.components.BackTopAppBar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel = viewModel(),
    ownerViewModel: OwnerDashboardViewModel = viewModel(),
    onLogout: () -> Unit,
    navController: NavController
) {
    val userData by authViewModel.currentUserData.collectAsState()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Logo state for owners
    val isOwner = userData?.role == "owner"
    var logoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var logoError by remember { mutableStateOf<String?>(null) }
    var isSavingLogo by remember { mutableStateOf(false) }
    val shopCustomization by ownerViewModel.shopCustomization.collectAsState()

    // Decode existing logo base64
    LaunchedEffect(shopCustomization.logoBase64) {
        val base64 = shopCustomization.logoBase64
        if (!base64.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    withContext(Dispatchers.Main) { logoBitmap = bitmap }
                } catch (e: Exception) {
                    println("Failed to decode logo: ${e.message}")
                }
            }
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                processSelectedLogo(context, uri, onSuccess = { bitmap, base64 ->
                    logoBitmap = bitmap
                    logoError = null
                    // Save immediately
                    ownerViewModel.saveLogoBase64(base64, "image/jpeg")
                }, onError = { error ->
                    logoError = error
                })
            }
        }
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "My Profile",
                onBack = { if (navController.previousBackStackEntry != null) navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                // Profile Picture - FIXED HERE
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            // SAFE VERSION - won't crash on empty string
                            text = if (!userData?.fullName.isNullOrEmpty())
                                userData!!.fullName.first().uppercase()
                            else
                                "?",
                            fontSize = 48.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item {
                Text(
                    text = userData?.fullName ?: "User Name",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            // Owner Logo Card
            if (isOwner) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Store,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Shop Logo",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (logoBitmap != null) {
                                    TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                        Text("Change", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))

                            // Logo preview
                            Surface(
                                modifier = Modifier.size(120.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (logoBitmap != null) {
                                        Image(
                                            bitmap = logoBitmap!!.asImageBitmap(),
                                            contentDescription = "Shop Logo",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(40.dp),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "No logo yet",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            if (logoError != null) {
                                Text(
                                    text = logoError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                enabled = !isSavingLogo
                            ) {
                                if (isSavingLogo) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        if (logoBitmap != null) Icons.Default.Refresh else Icons.Default.Upload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (logoBitmap != null) "Upload New Logo" else "Upload Logo")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "PNG or JPG, max 2MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.ContactMail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Contact Information",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = userData?.email ?: "No email", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = userData?.phoneNumber ?: "No phone number", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Member since: ${userData?.createdAt?.let { DateUtils.formatDate(it) } ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Quick Access",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { navController.navigate("cars") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("My Cars", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { navController.navigate("my_bookings") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bookings", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Button(
                            onClick = { navController.navigate("edit_profile") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Profile")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    authViewModel.signOut()
                                    onLogout()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Logout")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun processSelectedLogo(
    context: android.content.Context,
    uri: Uri,
    onSuccess: (android.graphics.Bitmap, String) -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            // Check file size first
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
            cursor?.moveToFirst()
            val fileSize = if (sizeIndex != null && sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
            cursor?.close()

            if (fileSize > 0 && fileSize > 2 * 1024 * 1024) {
                withContext(Dispatchers.Main) {
                    onError("Image too large (${fileSize / 1024 / 1024}MB). Maximum is 2MB.")
                }
                return@withContext
            }

            // Decode and compress
            val inputStream = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (original == null) {
                withContext(Dispatchers.Main) {
                    onError("Failed to decode image. Use PNG or JPG format.")
                }
                return@withContext
            }

            // Scale down if too large (max 512px)
            val maxDimension = 512
            val scale = minOf(
                maxDimension.toFloat() / original.width,
                maxDimension.toFloat() / original.height,
                1f
            )
            val scaled = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(),
                    true
                )
            } else {
                original
            }

            // Compress to JPEG at 80% quality
            val outputStream = ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)

            if (scaled != original) scaled.recycle()
            original.recycle()

            withContext(Dispatchers.Main) {
                onSuccess(scaled, base64)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Error: ${e.message}")
            }
        }
    }
}
