package com.app.checkot.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.checkot.model.Booking
import com.app.checkot.model.BookingStatus
import com.app.checkot.utils.BookingUtils
import kotlinx.coroutines.delay

/**
 * A live, ticking countdown timer displayed exclusively when a booking status
 * is IN_PROGRESS. Returns nothing (hidden) for all other statuses.
 */
@Composable
fun LiveWashTimerCard(
    booking: Booking,
    modifier: Modifier = Modifier
) {
    // STRICT GUARD: Render ONLY when booking status is IN_PROGRESS
    if (booking.status != BookingStatus.IN_PROGRESS) return

    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    // Tick every 1 second while IN_PROGRESS
    LaunchedEffect(booking.bookingId, booking.inProgressAt, booking.status) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val startTimeMillis = booking.inProgressAt ?: booking.createdAt
    val totalDurationMinutes = BookingUtils.bookingDurationMinutes(booking)
    val totalDurationSeconds = maxOf(60, totalDurationMinutes * 60)

    val elapsedSeconds = maxOf(0L, (currentTimeMillis - startTimeMillis) / 1000L).toInt()
    val remainingSeconds = maxOf(0, totalDurationSeconds - elapsedSeconds)
    val progress = minOf(1.0f, elapsedSeconds.toFloat() / totalDurationSeconds.toFloat())

    val isOvertime = remainingSeconds == 0

    // Pulsing alpha for active wash indicator dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0D1E26),
        border = BorderStroke(1.dp, Color(0xFF00E6C3).copy(alpha = 0.6f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Header title + Live Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E6C3).copy(alpha = alphaPulse))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WASH IN PROGRESS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E6C3),
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOvertime) Color(0xFF33200D) else Color(0xFF0F332E),
                    border = BorderStroke(1.dp, if (isOvertime) Color(0xFFFF9800) else Color(0xFF00E6C3))
                ) {
                    Text(
                        text = if (isOvertime) "Finishing Touches" else "Live Timer",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOvertime) Color(0xFFFFB74D) else Color(0xFF00E6C3),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Middle Row: Big Countdown Display / Overtime Message
            if (!isOvertime) {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                val timerString = String.format("%02d:%02d", mins, secs)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = timerString,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Estimated time remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = Color(0xFF00E6C3),
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF00E6C3),
                    trackColor = Color(0xFF1E3542)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Almost Done!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "The shop is applying final touches to your vehicle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Bottom Row: Staff member badge (if assigned)
            if (booking.servicedBy.isNotBlank()) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF00E6C3),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Serviced by: ${booking.servicedBy}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
