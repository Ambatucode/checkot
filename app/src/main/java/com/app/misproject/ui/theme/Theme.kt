package com.app.misproject.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Modern purple accent color for your app
val CheckotPurple = Color(0xFF6C63FF)
val CheckotPurpleLight = Color(0xFF9D94FF)
val CheckotPurpleDark = Color(0xFF4A42CC)
val CheckotTeal = Color(0xFF03DAC6)

private val DarkColorScheme = darkColorScheme(
    primary = CheckotPurple,
    secondary = CheckotTeal,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = CheckotPurpleDark,
    onPrimaryContainer = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CheckotPurple,
    secondary = CheckotTeal,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    primaryContainer = CheckotPurpleLight,
    onPrimaryContainer = Color.Black
)

@Composable
fun CheckotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            bodyLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp
            )
        ),
        content = content
    )
}