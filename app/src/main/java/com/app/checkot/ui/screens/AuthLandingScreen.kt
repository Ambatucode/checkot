package com.app.checkot.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

private class ScaledVideoView(context: Context) : VideoView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = getDefaultSize(0, widthMeasureSpec)
        val height = getDefaultSize(0, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}

@Composable
private fun LoopingVideoBackground(
    @RawRes videoRes: Int,
    targetBias: Float
) {
    val context = LocalContext.current
    val videoUri = remember(videoRes) {
        Uri.parse("android.resource://${context.packageName}/$videoRes")
    }

    val animatedBias by animateFloatAsState(
        targetValue = targetBias,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "videoPanAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04060C)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                ScaledVideoView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        mp.start()
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.75f
                    scaleY = 1.75f
                    translationX = animatedBias * 160.dp.toPx()
                }
        )
    }
}

@Composable
private fun UpworkRoleSelector(
    isOwnerMode: Boolean,
    onRoleSelected: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = CircleShape,
        color = Color(0x33000000),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (!isOwnerMode) Color.White else Color.Transparent)
                    .clickable { onRoleSelected(false) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "I'm a car owner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (!isOwnerMode) Color(0xFF0B1921) else Color.White
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (isOwnerMode) Color.White else Color.Transparent)
                    .clickable { onRoleSelected(true) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "I'm a shop owner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isOwnerMode) Color(0xFF0B1921) else Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
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

    val targetBias = if (!isOwnerMode) 0.65f else -0.65f

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Full-screen Video Background with Horizontal Panning
        LoopingVideoBackground(
            videoRes = R.raw.landing_video2,
            targetBias = targetBias
        )

        // 2. Upwork-style Dark Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x88070B14),
                            Color(0xEE060911),
                            Color(0xFF04060C)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // 3. Main Screen UI Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Top Header: Checkot Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x44000000))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Checkot",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CHECKOT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Dynamic Headline Text with Smooth Transition
            AnimatedContent(
                targetState = isOwnerMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                },
                label = "headlineText"
            ) { owner ->
                Text(
                    text = if (!owner)
                        "Find, book, & clean your car, all in one place"
                    else
                        "Manage bays, track queue, & grow your shop, all in one place",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Upwork 2-Segment Pill Selector
            UpworkRoleSelector(
                isOwnerMode = isOwnerMode,
                onRoleSelected = { isOwnerMode = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Upwork Emerald Green Action Button
            Button(
                onClick = { signInWithGoogle() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00BFA5),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Create account",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            if (googleError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = googleError!!,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (authState is AuthState.Error) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = (authState as AuthState.Error).message,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (authState is AuthState.Loading) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(color = Color(0xFF10B981), modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Link: Already have an account? Log in
            Row(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .clickable { signInWithGoogle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Text(
                    text = "Log in",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

