package com.app.checkot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// CHECKOT brand scheme: dark navy background, teal as the primary action
// color, white primary text. One fixed scheme — the brand look does not
// follow the system light/dark setting.
private val CheckotColorScheme = darkColorScheme(
    primary = CheckotTeal,
    onPrimary = CheckotTextPrimary,
    primaryContainer = CheckotTealDark,
    onPrimaryContainer = CheckotTextPrimary,

    secondary = CheckotTealDark,
    onSecondary = CheckotTextPrimary,
    secondaryContainer = CheckotNavyElevated,
    onSecondaryContainer = CheckotSparkle,

    tertiary = CheckotSparkle,
    onTertiary = CheckotNavy,
    tertiaryContainer = CheckotNavyElevated,
    onTertiaryContainer = CheckotSparkle,

    background = CheckotNavy,
    onBackground = CheckotTextPrimary,
    surface = CheckotNavySurface,
    onSurface = CheckotTextPrimary,
    surfaceVariant = CheckotNavyElevated,
    onSurfaceVariant = CheckotTextSecondary,

    outline = CheckotOutline,
    outlineVariant = CheckotOutline,

    error = CheckotError,
    onError = CheckotTextPrimary,
    errorContainer = CheckotErrorContainer,
    onErrorContainer = CheckotOnErrorContainer
)

@Composable
fun CheckotTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CheckotColorScheme,
        typography = CheckotTypography,
        content = content
    )
}
