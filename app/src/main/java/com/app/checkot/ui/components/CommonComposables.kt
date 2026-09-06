package com.app.checkot.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Chat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.checkot.model.BookingStatus

/**
 * TopAppBar with a back-navigation icon, used across detail/form screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = actions
    )
}

/**
 * A confirm/cancel AlertDialog for the app's status-change confirmations
 * (approve, reject, cancel, no-show, etc.).
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    confirmColor: Color = Color.Unspecified
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = confirmColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}

/**
 * A "label: value" row used throughout detail/summary screens.
 * [singleLine] truncates both label and value with an ellipsis; set to
 * false for values that may need to wrap (e.g. a comma-joined service list).
 */
@Composable
fun DetailRow(label: String, value: String, singleLine: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip
        )
        Text(
            text = value,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip
        )
    }
}

/**
 * The booking-status icon with a status-driven motion, shared by the booking
 * detail header and the status badge on each booking card. Each active status
 * gets its own animation so the state reads at a glance:
 *   • PENDING     → hourglass rocks back and forth (waiting to be picked up)
 *   • CONFIRMED   → check pulses (locked in)
 *   • IN_PROGRESS → car-wash icon wiggles side to side (being scrubbed / in the queue)
 *   • COMPLETED   → double-check pops once on appear, then rests (nothing left to do)
 *   • CANCELLED   → static (no motion — nothing is happening)
 *
 * Uses only Compose's built-in infinite-transition animations, so there are no
 * new dependencies and the motion pauses automatically when off-screen.
 */
@Composable
fun AnimatedStatusIcon(
    status: BookingStatus,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (status) {
        BookingStatus.PENDING -> Icons.Filled.HourglassEmpty
        BookingStatus.CONFIRMED -> Icons.Filled.CheckCircle
        BookingStatus.IN_PROGRESS -> Icons.Filled.LocalCarWash
        BookingStatus.COMPLETED -> Icons.Filled.DoneAll
        BookingStatus.CANCELLED -> Icons.Filled.Cancel
    }

    val transition = rememberInfiniteTransition(label = "status-$status")

    // Quick side-to-side scrub, used by IN_PROGRESS (car being washed).
    val wiggle by transition.animateFloat(
        initialValue = if (status == BookingStatus.IN_PROGRESS) -14f else 0f,
        targetValue = if (status == BookingStatus.IN_PROGRESS) 14f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 220),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggle"
    )

    // Back-and-forth tilt, used by PENDING (hourglass tipping).
    val tilt by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (status == BookingStatus.PENDING) 180f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    // Scale pulse — only CONFIRMED keeps pulsing (still waiting on the shop).
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == BookingStatus.CONFIRMED) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // COMPLETED gets a single celebratory pop, then settles and stays still.
    val completedPop = remember { Animatable(1f) }
    LaunchedEffect(status) {
        if (status == BookingStatus.COMPLETED) {
            completedPop.snapTo(0.6f)
            completedPop.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val scale = pulse * completedPop.value
    Icon(
        imageVector = icon,
        contentDescription = status.displayName,
        tint = tint,
        modifier = modifier.graphicsLayer {
            rotationZ = wiggle + tilt
            scaleX = scale
            scaleY = scale
        }
    )
}

/**
 * A standard, centralized footer that displays the application version
 * and code (e.g. "Checkot v1.4 (5)").
 */
@Composable
fun AppVersionFooter(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = "Checkot v${com.app.checkot.BuildConfig.VERSION_NAME} (${com.app.checkot.BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

/**
 * TopAppBar Chat Icon Button with real-time unread badge counter.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatIconButton(
    onClick: () -> Unit,
    userUid: String,
    isOwner: Boolean = false,
    ownedShopId: String = "",
    modifier: Modifier = Modifier
) {
    val authUid = com.google.firebase.ktx.Firebase.auth.currentUser?.uid ?: ""
    val effectiveUid = userUid.ifBlank { authUid }

    var unreadCount by androidx.compose.runtime.remember(effectiveUid, isOwner, ownedShopId) { 
        androidx.compose.runtime.mutableIntStateOf(0) 
    }

    androidx.compose.runtime.DisposableEffect(effectiveUid, isOwner, ownedShopId) {
        if (effectiveUid.isBlank() && ownedShopId.isBlank()) return@DisposableEffect onDispose {}

        val db = Firebase.firestore
        val query = if (isOwner && ownedShopId.isNotBlank()) {
            db.collection("chats").whereEqualTo("shopId", ownedShopId)
        } else {
            db.collection("chats").whereEqualTo("userId", effectiveUid)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val sum = snapshot.documents.sumOf { doc ->
                val field = if (isOwner) "unreadCountOwner" else "unreadCountCustomer"
                (doc.getLong(field) ?: 0).toInt()
            }
            unreadCount = sum
        }

        onDispose { listener.remove() }
    }

    IconButton(onClick = onClick, modifier = modifier) {
        androidx.compose.material3.BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    androidx.compose.material3.Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "Messages"
            )
        }
    }
}
