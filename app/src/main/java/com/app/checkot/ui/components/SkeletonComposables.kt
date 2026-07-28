package com.app.checkot.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Skeleton (shimmer) loading placeholders — used instead of a bare spinner so the
 * UI shows the *shape* of the content that's coming while data / map tiles load.
 */

/** An animated left-to-right shimmer gradient. Create once per screen and share. */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-x"
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x - 400f, 0f),
        end = Offset(x, 0f)
    )
}

/** A single shimmering placeholder block. */
@Composable
fun SkeletonBox(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    Box(modifier = modifier.clip(shape).background(brush))
}

/** Placeholder that mirrors a [BookingCard]'s layout while bookings load. */
@Composable
private fun BookingCardSkeleton(brush: Brush) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Shop name + status badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(brush, Modifier.width(100.dp).height(12.dp))
                Spacer(modifier = Modifier.weight(1f))
                SkeletonBox(brush, Modifier.width(72.dp).height(22.dp), RoundedCornerShape(11.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Service title
            SkeletonBox(brush, Modifier.fillMaxWidth(0.7f).height(16.dp))
            Spacer(modifier = Modifier.height(10.dp))
            // Car + time row
            SkeletonBox(brush, Modifier.width(150.dp).height(12.dp))
        }
    }
}

/** A short column of booking-card skeletons for the My Bookings list. */
@Composable
fun BookingListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4
) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(count) { BookingCardSkeleton(brush) }
    }
}

/** Shimmer placeholder for a map, shown until the Google Map tiles finish loading. */
@Composable
fun MapSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(brush),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        )
    }
}
