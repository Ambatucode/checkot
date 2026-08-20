package com.app.checkot.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    var displayedText by remember { mutableStateOf("") }
    var showCursor by remember { mutableStateOf(true) }

    // Typewriter effect
    LaunchedEffect(text) {
        displayedText = ""
        for (i in text.indices) {
            displayedText = text.substring(0, i + 1)
            delay(60) // 60ms typing speed
        }
    }

    // Blinking cursor effect
    LaunchedEffect(text) {
        while (true) {
            showCursor = !showCursor
            delay(500)
        }
    }

    Text(
        text = displayedText + if (showCursor) "_" else " ",
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace
    )
}
