package com.app.checkot.ui.theme

import androidx.compose.ui.graphics.Color

// CHECKOT brand identity
val CheckotTeal = Color(0xFF00BFA5)        // primary action color
// Same bright teal as CheckotTeal so primaryContainer/secondary cards render as
// the exact login-button teal. White (onPrimaryContainer) text/icons sit on top
// so content stands out on the bright teal.
val CheckotTealDark = Color(0xFF00BFA5)    // unified bright teal (matches primary)
val CheckotNavy = Color(0xFF08141E)        // app background — deep dark teal base
val CheckotNavySurface = Color(0xFF0F2530) // cards/surfaces — dark teal, lifts off background
val CheckotTextPrimary = Color(0xFFFFFFFF)
val CheckotTextSecondary = Color(0xFFB2DFDB) // muted light teal
val CheckotSparkle = Color(0xFFE0F7FA)       // accent, near white with a cool tint

// Supporting tones derived from the palette (not spec'd, kept in-family)
val CheckotNavyElevated = Color(0xFF112834)  // chips/containers a step above surface
val CheckotCardSurface = Color(0xFF0F2530)   // all card surfaces — dark teal (replaces slate blue)
val CheckotOutline = Color(0xFF35586B)
val CheckotError = Color(0xFFEF7A85)
val CheckotErrorContainer = Color(0xFF4E2A32)
val CheckotOnErrorContainer = Color(0xFFF9DEDC)

// Badge colors for "From ₱X" price chips and feature labels
val CheckotBadgeSurface = Color(0xFF112834)  // dark surface container (matches palette)
val CheckotBadgeTeal = Color(0xFF00E6C3)     // bright vibrant teal badge text
