package io.github.stephenjoy98.heatmap.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HeatOrange = Color(0xFFFF7A1A)
val RecordRed = Color(0xFFE53935)

private val colors = darkColorScheme(
    primary = HeatOrange,
    onPrimary = Color(0xFF1A0E00),
    secondary = Color(0xFFFFC266),
    background = Color(0xFF141414),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerLow = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF262626),
)

@Composable
fun TickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
