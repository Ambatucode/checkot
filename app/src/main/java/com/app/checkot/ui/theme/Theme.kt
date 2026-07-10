package com.app.checkot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
        typography = CheckotTypography,
        content = content
    )
}
