package com.app.checkot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloatingAdminNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    pendingCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF13222B),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFF1E2D38)),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdminNavItem(
                    icon = Icons.Default.HourglassEmpty,
                    label = "Pending",
                    selected = selectedTab == 0,
                    badgeCount = pendingCount,
                    onClick = { onTabSelected(0) }
                )
                AdminNavItem(
                    icon = Icons.Default.CheckCircle,
                    label = "Active",
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) }
                )
                AdminNavItem(
                    icon = Icons.Default.Cancel,
                    label = "Rejected",
                    selected = selectedTab == 2,
                    onClick = { onTabSelected(2) }
                )
                AdminNavItem(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    label = "Reviews",
                    selected = selectedTab == 3,
                    onClick = { onTabSelected(3) }
                )
            }
        }
    }
}

@Composable
private fun AdminNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val color = if (selected) Color(0xFF00E6C3) else Color(0xFF94A3B8)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        if (badgeCount > 0) {
            BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
