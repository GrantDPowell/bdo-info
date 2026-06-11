package com.gpowell.bdoboss.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BdoGold = Color(0xFFD4AF37)

private val DarkColors = darkColorScheme(
    primary = BdoGold,
    onPrimary = Color.Black,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1B16),
    secondary = Color(0xFF8D7B48),
    error = Color(0xFFB3261E),
)

@Composable
fun BdoBossTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
