package com.app.checkot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Interactive Jetpack Compose dialog displaying Checkot's Terms of Service & Privacy Policy.
 * Matches the app's dark teal design system (#0B1921 background, #00E6C3 primary teal).
 */
@Composable
fun TermsAndPrivacyDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F2530),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF00E6C3),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Terms & Privacy Policy",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close terms dialog",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )

                // Scrollable Content
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "Welcome to Checkot. Please read our Terms of Service and Privacy Policy carefully before using our car wash booking platform.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TermsSection(
                        title = "1. Booking & Service Rules",
                        content = "• All car wash appointments are reserved for specific time slots and vehicle size classes (S, M, L, XL, XXL).\n" +
                                "• Shops reserve the right to re-evaluate vehicle size categorization upon arrival at the bay.\n" +
                                "• Please arrive at least 5 minutes prior to your booked slot to ensure timely service."
                    )

                    TermsSection(
                        title = "2. Account Responsibilities",
                        content = "• Users must provide accurate contact information, including a verified phone number.\n" +
                                "• You are responsible for all activities occurring under your account.\n" +
                                "• Fraudulent bookings or repeated no-shows may lead to account suspension."
                    )

                    TermsSection(
                        title = "3. Data Privacy & Protection",
                        content = "• Checkot collects personal information (name, phone number, vehicle details) strictly for booking fulfillment and queue notifications.\n" +
                                "• We do not sell your personal data to third parties.\n" +
                                "• Location services are used solely to calculate distances to nearby car wash shops."
                    )

                    TermsSection(
                        title = "4. Cancellation & No-Show Policy",
                        content = "• Bookings may be cancelled up to 30 minutes before the scheduled start time.\n" +
                                "• Pending bookings older than 2 hours or past their date will be automatically cancelled by the system.\n" +
                                "• Refund policy for paid services is managed directly by the servicing shop."
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )

                // Understand / Accept Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E6C3),
                        contentColor = Color(0xFF0B1921)
                    )
                ) {
                    Text(
                        text = "I Understand",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TermsSection(
    title: String,
    content: String
) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00E6C3)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 18.sp
        )
    }
}

/**
 * Reusable footer link for authentication screens.
 */
@Composable
fun TermsAndPrivacyFooterLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Terms of Service & Privacy Policy",
            fontSize = 12.sp,
            color = Color(0xFF00E6C3),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
