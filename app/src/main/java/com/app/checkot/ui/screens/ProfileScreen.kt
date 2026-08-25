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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.checkot.ui.components.AppButton
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.app.checkot.ui.components.BackTopAppBar
import com.app.checkot.ui.components.AppVersionFooter
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel = viewModel(),
    ownerViewModel: OwnerDashboardViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    onLogout: () -> Unit,
    navController: NavController
) {
    val userData by authViewModel.currentUserData.collectAsState()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    
    var showEditNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(userData?.fullName ?: "") }
    var isSavingName by remember { mutableStateOf(false) }
    var saveNameError by remember { mutableStateOf<String?>(null) }

    var updateAvailableVersion by remember { mutableStateOf<String?>(null) }
    var updateDownloadUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.github.com/repos/Ambatucode/checkot/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "checkot-app")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(response)
                    val tagName = json.optString("tag_name", "")
                    val htmlUrl = json.optString("html_url", "")
                    val assets = json.optJSONArray("assets")
                    
                    var apkUrl = htmlUrl
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", htmlUrl)
                                break
                            }
                        }
                    }
                    
                    val currentVersion = com.app.checkot.BuildConfig.VERSION_NAME
                    if (tagName.isNotBlank() && isUpdateAvailable(currentVersion, tagName)) {
                        withContext(Dispatchers.Main) {
                            updateAvailableVersion = tagName
                            updateDownloadUrl = apkUrl
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val context = LocalContext.current

    // Logo state for owners — the logo lives in Firebase Storage now; we render
    // it straight from its download URL via Coil (no base64 decode needed).
    val isOwner = userData?.role == "owner"
    val isClient = userData?.role == "client"
    var logoError by remember { mutableStateOf<String?>(null) }
    var isSavingLogo by remember { mutableStateOf(false) }
    val shopCustomization by ownerViewModel.shopCustomization.collectAsState()
    val hasLogo = shopCustomization.logoUrl.isNotBlank()

    // Image picker launcher — compresses the pick and uploads it to Storage.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isSavingLogo = true
                logoError = null
                processSelectedLogo(context, uri, onSuccess = { bytes ->
                    ownerViewModel.uploadShopLogo(bytes) { success ->
                        isSavingLogo = false
                        if (!success) logoError =
                            "Upload failed. Check your connection and try again."
                    }
                }, onError = { error ->
                    isSavingLogo = false
                    logoError = error
                })
            }
        }
    }

    // Optional wide cover/banner image for owners.
    var isSavingBanner by remember { mutableStateOf(false) }
    var bannerError by remember { mutableStateOf<String?>(null) }
    val hasBanner = shopCustomization.bannerUrl.isNotBlank()

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isSavingBanner = true
                bannerError = null
                // Banners are wide, so allow more resolution than the logo.
                processSelectedLogo(context, uri, maxDimension = 1200, onSuccess = { bytes ->
                    ownerViewModel.uploadShopBanner(bytes) { success ->
                        isSavingBanner = false
                        if (!success) bannerError =
                            "Upload failed. Check your connection and try again."
                    }
                }, onError = { error ->
                    isSavingBanner = false
                    bannerError = error
                })
            }
        }
    }

    val activity = remember(context) { context.findFragmentActivity() }

    fun performDelete() {
        isDeletingAccount = true
        deleteError = null
        authViewModel.deleteClientAccount(
            onSuccess = {
                isDeletingAccount = false
                showDeleteConfirm = false
                onLogout()
                navController.navigate("login") {
                    popUpTo(0)
                }
            },
            onError = { msg ->
                isDeletingAccount = false
                deleteError = msg
                showDeleteConfirm = true // Force dialog open if error occurred during biometric flow
            }
        )
    }

    fun confirmDelete() {
        deleteError = null
        val act = activity
        if (act != null && BiometricAuth.canAuthenticate(context)) {
            BiometricAuth.prompt(
                activity = act,
                title = "Delete Account",
                subtitle = "Confirm identity to permanently delete your account and cars",
                onSuccess = { performDelete() },
                onError = { msg -> deleteError = msg }
            )
        } else {
            showDeleteConfirm = true
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAccount) {
                    showDeleteConfirm = false
                    deleteError = null
                }
            },
            title = { Text("Delete Account?") },
            text = {
                Column {
                    Text(
                        "This will permanently delete your account, your profile details, and all your saved vehicles. Past completed bookings will be preserved for history. This cannot be undone.",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (deleteError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = deleteError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { performDelete() },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Delete Account")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        deleteError = null
                    },
                    enabled = !isDeletingAccount
                ) { Text("Cancel") }
            }
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSavingName) {
                    showEditNameDialog = false
                    saveNameError = null
                }
            },
            title = { Text("Edit Full Name") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSavingName
                    )
                    if (saveNameError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = saveNameError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isBlank()) {
                            saveNameError = "Name cannot be empty."
                            return@TextButton
                        }
                        isSavingName = true
                        saveNameError = null
                        profileViewModel.updateUserProfile(mapOf("fullName" to nameInput)) { success, error ->
                            isSavingName = false
                            if (success) {
                                showEditNameDialog = false
                            } else {
                                saveNameError = error ?: "Failed to save changes."
                            }
                        }
                    },
                    enabled = !isSavingName
                ) {
                    if (isSavingName) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditNameDialog = false
                        saveNameError = null
                    },
                    enabled = !isSavingName
                ) { Text("Cancel") }
            }
        )
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
            if (updateAvailableVersion != null && updateDownloadUrl != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "New update available",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Update Available (${updateAvailableVersion})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "A newer version of the Checkot app is available for download. Get the latest fixes and features now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            Uri.parse(updateDownloadUrl)
                                        )
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download APK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Profile Picture — clean teal-ringed avatar
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .border(3.dp, Color(0xFF00E6C3), CircleShape)
                        .clip(CircleShape),
                    color = com.app.checkot.ui.theme.CheckotCardSurface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (!userData?.fullName.isNullOrEmpty())
                                userData!!.fullName.first().uppercase()
                            else
                                "?",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E6C3)
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
                        colors = CardDefaults.cardColors(containerColor = com.app.checkot.ui.theme.CheckotCardSurface),
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
                                if (hasLogo) {
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
                                    if (hasLogo) {
                                        AsyncImage(
                                            model = shopCustomization.logoUrl,
                                            contentDescription = "Shop Logo",
                                            contentScale = ContentScale.Crop,
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

                            AppButton(
                                text = if (hasLogo) "Upload New Logo" else "Upload Logo",
                                onClick = { imagePickerLauncher.launch("image/*") },
                                enabled = !isSavingLogo,
                                isLoading = isSavingLogo,
                                icon = if (hasLogo) Icons.Default.Refresh else Icons.Default.Upload
                            )
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
            // Owner Banner Card (optional wide cover photo)
            if (isOwner) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.app.checkot.ui.theme.CheckotCardSurface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Shop Banner",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (hasBanner) {
                                    TextButton(onClick = { bannerPickerLauncher.launch("image/*") }) {
                                        Text("Change", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))

                            // Wide banner preview
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (hasBanner) {
                                        AsyncImage(
                                            model = shopCustomization.bannerUrl,
                                            contentDescription = "Shop Banner",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
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
                                                text = "No banner yet",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            if (bannerError != null) {
                                Text(
                                    text = bannerError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            AppButton(
                                text = if (hasBanner) "Upload New Banner" else "Upload Banner",
                                onClick = { bannerPickerLauncher.launch("image/*") },
                                enabled = !isSavingBanner,
                                isLoading = isSavingBanner,
                                icon = if (hasBanner) Icons.Default.Refresh else Icons.Default.Upload
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Wide cover photo shown on your booking page. PNG or JPG.",
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
                    colors = CardDefaults.cardColors(containerColor = com.app.checkot.ui.theme.CheckotCardSurface),
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
                            val phone = userData?.phoneNumber
                            Text(
                                text = if (phone.isNullOrBlank()) "No phone number" else phone,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (userData?.phoneVerified == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { navController.navigate("phone_verification/change") }) {
                                Text("Change", style = MaterialTheme.typography.labelMedium)
                            }
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
                // Client-only navigation rows
                if (isClient) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.app.checkot.ui.theme.CheckotCardSurface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // My Cars row
                            Surface(
                                onClick = { navController.navigate("cars") },
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        color = Color(0xFF00E6C3).copy(alpha = 0.15f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.DirectionsCar,
                                                contentDescription = null,
                                                tint = Color(0xFF00E6C3),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "My Cars",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            // Bookings row
                            Surface(
                                onClick = { navController.navigate("my_bookings") },
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        color = Color(0xFF00E6C3).copy(alpha = 0.15f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Bookmark,
                                                contentDescription = null,
                                                tint = Color(0xFF00E6C3),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Bookings",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Edit Profile — standalone full-width primary button
            item {
                AppButton(
                    text = "Edit Profile",
                    onClick = {
                        nameInput = userData?.fullName ?: ""
                        showEditNameDialog = true
                    },
                    icon = Icons.Default.Edit
                )
            }
            // Logout — standalone danger outlined button
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            authViewModel.signOut()
                            onLogout()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFFF6B6B).copy(alpha = 0.1f),
                        contentColor = Color(0xFFFF6B6B)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B6B)),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFFFF6B6B),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!isOwner) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Danger Zone",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Once you delete your account, all your profile details and saved cars will be permanently removed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { confirmDelete() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                enabled = !isDeletingAccount
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Account", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                AppVersionFooter()
            }
        }
    }
}

private suspend fun processSelectedLogo(
    context: android.content.Context,
    uri: Uri,
    maxDimension: Int = 512,
    onSuccess: (ByteArray) -> Unit,
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

            // Scale down if too large (to maxDimension on the longest side)
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

            if (scaled != original) scaled.recycle()
            original.recycle()

            withContext(Dispatchers.Main) {
                onSuccess(bytes)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Error: ${e.message}")
            }
        }
    }
}

private fun isUpdateAvailable(current: String, latestTag: String): Boolean {
    val cleanLatest = latestTag.trim()
        .replace(Regex("^v"), "")
        .split("-")[0]
    
    val currentParts = current.split(".")
    val latestParts = cleanLatest.split(".")
    
    val length = maxOf(currentParts.size, latestParts.size)
    for (i in 0 until length) {
        val currentVal = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
        val latestVal = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (latestVal > currentVal) return true
        if (currentVal > latestVal) return false
    }
    return false
}
