package com.app.checkot.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
        else -> Icons.Filled.HourglassEmpty
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
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        val unreadMap = mutableMapOf<String, Int>()

        fun updateTotalUnread() {
            unreadCount = unreadMap.values.sum()
        }

        // Listener 1: Customer chats (userId)
        if (effectiveUid.isNotBlank()) {
            val userQuery = db.collection("chats").whereEqualTo("userId", effectiveUid)
            listeners.add(userQuery.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    val count = (doc.getLong("unreadCountCustomer") ?: 0).toInt()
                    unreadMap["cust_${doc.id}"] = count
                }
                updateTotalUnread()
            })
        }

        // Listener 2: Shop Owner chats (shopId)
        if (isOwner && ownedShopId.isNotBlank()) {
            val shopQuery = db.collection("chats").whereEqualTo("shopId", ownedShopId)
            listeners.add(shopQuery.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    val count = (doc.getLong("unreadCountOwner") ?: 0).toInt()
                    unreadMap["own_${doc.id}"] = count
                }
                updateTotalUnread()
            })
        }

        onDispose {
            listeners.forEach { it.remove() }
        }
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

/**
 * Data structures and composables for the App Guide & Help Center.
 */
data class GuideStep(
    val stepNumber: Int,
    val title: String,
    val description: String
)

data class GuideTutorialItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val steps: List<GuideStep>
)

val CHECKOT_APP_GUIDES = listOf(
    GuideTutorialItem(
        id = "booking",
        title = "How to Book a Car Wash",
        subtitle = "Step-by-step on selecting shops, packages, and time slots",
        icon = Icons.Default.LocalCarWash,
        steps = listOf(
            GuideStep(1, "Select a Car Wash Shop", "Browse featured shops on the Home screen or filter by specific services to find a detailer near you."),
            GuideStep(2, "Choose Vehicle & Wash Package", "Select your registered vehicle, then pick from available packages (e.g. Exterior, Interior, Deep Detail) and add-ons."),
            GuideStep(3, "Pick a Convenient Time Slot", "Choose an open date and time slot aligned with the shop's live operating schedule and available bays."),
            GuideStep(4, "Confirm & Track Reservation", "Review details, add special requests for the detailer, and confirm your booking to track real-time progress.")
        )
    ),
    GuideTutorialItem(
        id = "ai_scan",
        title = "AI Vehicle Condition Scan",
        subtitle = "Take a photo for instant AI package recommendations",
        icon = Icons.Default.AutoAwesome,
        steps = listOf(
            GuideStep(1, "Open AI Scanner", "Tap the AI Scan feature from the main menu or vehicle management section."),
            GuideStep(2, "Snap a Clear Photo", "Take a clear picture of your vehicle's exterior or dirty areas in bright lighting."),
            GuideStep(3, "Instant AI Analysis", "Checkot's AI model evaluates paint condition and dirt severity to recommend the ideal wash package."),
            GuideStep(4, "One-Tap Package Select", "Apply the AI recommended package directly to your booking request with a single tap.")
        )
    ),
    GuideTutorialItem(
        id = "live_queue",
        title = "Live Bay Queue",
        subtitle = "Real-time bay occupancy and countdown timers",
        icon = Icons.Default.Timer,
        steps = listOf(
            GuideStep(1, "View Live Occupancy", "See active bay status (Available, In Service, or Cleaning) for your chosen shop in real time."),
            GuideStep(2, "Real-Time Countdown Timers", "Watch the countdown timer tick down live as detailers work on your vehicle in the wash bay."),
            GuideStep(3, "Queue Estimated Wait", "Automatic wait-time calculations let you know exactly when your slot begins based on queue velocity."),
            GuideStep(4, "Stage Notifications", "Get alerted instantly when your car enters the bay and when it's clean and ready for pickup.")
        )
    ),
    GuideTutorialItem(
        id = "managing_bookings",
        title = "Managing Bookings",
        subtitle = "Tracking active reservations and notifications",
        icon = Icons.Default.Bookmark,
        steps = listOf(
            GuideStep(1, "Access Bookings Dashboard", "View all current, upcoming, and past reservations under the Bookings tab in your profile."),
            GuideStep(2, "Direct Chat with Detailers", "Message shop owners directly to coordinate arrival times, special requests, or location directions."),
            GuideStep(3, "Push Notifications", "Receive push alerts for booking confirmations, status updates, and bay queue readiness."),
            GuideStep(4, "Receipts & Cancellations", "View detailed digital receipts or manage cancellations directly from the booking detail screen.")
        )
    )
)

/**
 * Interactive App Guide & Help Center section featuring tutorial cards.
 */
@Composable
fun AppGuideSection(
    modifier: Modifier = Modifier,
    onGuideSelected: (GuideTutorialItem) -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.app.checkot.ui.theme.CheckotCardSurface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "App Guide & Help Center",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            CHECKOT_APP_GUIDES.forEachIndexed { index, guide ->
                Surface(
                    onClick = { onGuideSelected(guide) },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFF00E6C3).copy(alpha = 0.15f)
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Icon(
                                    imageVector = guide.icon,
                                    contentDescription = null,
                                    tint = Color(0xFF00E6C3),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = guide.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = guide.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open guide",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (index < CHECKOT_APP_GUIDES.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

/**
 * Modal Dialog displaying structured step-by-step instructions for an App Guide.
 */
@Composable
fun AppGuideDetailDialog(
    guide: GuideTutorialItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = com.app.checkot.ui.theme.CheckotCardSurface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = Color(0xFF00E6C3).copy(alpha = 0.2f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = guide.icon,
                                contentDescription = null,
                                tint = Color(0xFF00E6C3),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = guide.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = guide.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))

                // Steps list (scrollable)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    guide.steps.forEach { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = Color(0xFF00E6C3)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${step.stepNumber}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E6C3),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Got it!",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
