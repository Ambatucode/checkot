package com.app.checkot.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

// Phases of the check flow.
private enum class Phase { IDLE, ANALYZING, RESULT, ERROR }

// Mirrors the server-side DAILY_LIMIT in functions/index.js. Used only for the
// intro hint shown before the first result — the live count always comes from
// the server response, which is the real source of truth.
private const val DAILY_LIMIT_DISPLAY = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckCarScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var verdict by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    // How many checks the user has left today (null until the first result).
    var remaining by remember { mutableStateOf<Int?>(null) }
    var dailyLimit by remember { mutableStateOf(DAILY_LIMIT_DISPLAY) }

    fun reset() {
        bitmap = null
        phase = Phase.IDLE
        verdict = ""; reason = ""; error = ""
    }

    fun onPhotoChosen(bmp: Bitmap?) {
        if (bmp == null) return
        bitmap = bmp
        phase = Phase.IDLE
        verdict = ""; reason = ""; error = ""
    }

    // Gallery picker — modern photo picker, needs no storage permission.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeSampledBitmap(context, uri) }
                onPhotoChosen(bmp)
            }
        }
    }

    // Camera — returns a preview bitmap, needs no CAMERA permission or FileProvider.
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? -> onPhotoChosen(bmp) }

    fun analyze() {
        val bmp = bitmap ?: return
        phase = Phase.ANALYZING
        error = ""
        scope.launch {
            try {
                val base64 = withContext(Dispatchers.Default) { encodeToBase64Jpeg(bmp) }
                val data = hashMapOf<String, Any>(
                    "imageBase64" to base64,
                    "mimeType" to "image/jpeg"
                )
                val callable = Firebase.functions("asia-southeast1")
                    .getHttpsCallable("checkCar")
                    .apply { setTimeout(120, java.util.concurrent.TimeUnit.SECONDS) }
                val result = callable.call(data).await()
                val map = result.data as? Map<*, *>
                    ?: throw IllegalStateException("Unexpected response from the AI.")
                verdict = (map["verdict"] as? String) ?: "Not a car"
                reason = (map["reason"] as? String) ?: ""
                (map["remaining"] as? Number)?.let { remaining = it.toInt() }
                (map["dailyLimit"] as? Number)?.let { dailyLimit = it.toInt() }
                phase = Phase.RESULT
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong. Please try again."
                phase = Phase.ERROR
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Car Check") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bmp = bitmap
            if (bmp == null) {
                // ---- No photo yet: intro + capture options ----
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Is your car due for a wash?",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Take or upload a photo of your car and our AI will tell you " +
                        "how dirty it looks — exterior and seats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Take a photo")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose from gallery")
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "You get $DAILY_LIMIT_DISPLAY free AI car checks each day.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                DisclaimerBanner()
            } else {
                // ---- Photo selected: preview + state-driven content ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Your car",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                    if (phase == Phase.ANALYZING) {
                        Surface(
                            modifier = Modifier.matchParentSize(),
                            color = Color.Black.copy(alpha = 0.45f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Analyzing your car…",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                when (phase) {
                    Phase.IDLE, Phase.ANALYZING -> {
                        Button(
                            onClick = { analyze() },
                            enabled = phase != Phase.ANALYZING,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (phase == Phase.ANALYZING) "Analyzing…" else "Analyze this photo")
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { reset() },
                            enabled = phase != Phase.ANALYZING,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Choose a different photo") }
                    }

                    Phase.RESULT -> {
                        VerdictCard(verdict = verdict, reason = reason)
                        Spacer(Modifier.height(12.dp))
                        DisclaimerBanner()
                        Spacer(Modifier.height(16.dp))
                        if (verdict == "Needs a wash" || verdict == "Lightly dirty") {
                            Button(
                                onClick = {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.LocalCarWash, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Book a wash")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { reset() },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Check another car")
                        }
                        remaining?.let { left ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "$left of $dailyLimit checks left today",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Phase.ERROR -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { analyze() },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Try again")
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Choose a different photo") }
                    }
                }
            }
        }
    }
}

// Always-visible caution that the AI is a guide, not an authority. Shown on the
// intro screen and again under every result so it can't be missed. Uses the
// brand palette — navy-elevated card with a teal border and teal warning icon —
// so it stays bold and on-brand.
@Composable
private fun DisclaimerBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "AI can make mistakes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "This is only a rough guide. Don't let it replace your own eyes " +
                        "or the shop's assessment when you decide.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun VerdictCard(verdict: String, reason: String) {
    val (container, content, icon) = verdictStyle(verdict)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    verdict,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = content
                )
            }
            if (reason.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.9f)
                )
            }
        }
    }
}

// Verdict → (container color, content color, icon). Uses fixed accent colors so
// the meaning reads the same in light and dark themes.
@Composable
private fun verdictStyle(verdict: String): Triple<Color, Color, ImageVector> = when (verdict) {
    "Clean" -> Triple(
        Color(0xFFE3F4E6), Color(0xFF1B5E20), Icons.Default.CheckCircle
    )
    "Lightly dirty" -> Triple(
        Color(0xFFFDF3E0), Color(0xFF8A5A00), Icons.Default.WaterDrop
    )
    "Needs a wash" -> Triple(
        Color(0xFFFDE7E7), Color(0xFF9A1B1B), Icons.Default.LocalCarWash
    )
    else -> Triple( // "Not a car" / unknown
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
        Icons.AutoMirrored.Filled.HelpOutline
    )
}

// ---- Image helpers ----

// Decodes a gallery Uri to a memory-safe software bitmap (two-pass so a huge
// phone photo never OOMs), capped near [reqDim] on its longest side.
private fun decodeSampledBitmap(context: Context, uri: Uri, reqDim: Int = 1024): Bitmap? {
    val resolver = context.contentResolver
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > reqDim * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }
}

// Scales down to [maxDim] and encodes as a base64 JPEG for the function payload.
private fun encodeToBase64Jpeg(bitmap: Bitmap, maxDim: Int = 1024, quality: Int = 80): String {
    val longest = max(bitmap.width, bitmap.height)
    val scaled = if (longest > maxDim) {
        val ratio = maxDim.toFloat() / longest
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    } else bitmap
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    if (scaled !== bitmap) scaled.recycle()
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}
