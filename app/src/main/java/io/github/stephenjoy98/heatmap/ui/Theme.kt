package io.github.stephenjoy98.heatmap.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import io.github.stephenjoy98.heatmap.data.MapTheme
import io.github.stephenjoy98.heatmap.data.Prefs

val HeatOrange = Color(0xFFFF7A1A)
val RecordRed = Color(0xFFE53935)
val FriendBlue = Color(0xFF3D9BFF)

// Strava-like: near-black surfaces with an orange accent.
private val darkColors = darkColorScheme(
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

// Clean white surfaces with soft greys, in the style of an in-car navigation screen.
private val lightColors = lightColorScheme(
    primary = Color(0xFFE8590C),
    onPrimary = Color.White,
    secondary = Color(0xFF3E4A59),
    background = Color(0xFFF7F7F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFEFEF),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF2F2F2),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF6B6B70),
)

/** Whether the map (and so the whole app) is currently dark. */
@Composable
fun isDarkTheme(): Boolean {
    val theme by Prefs.mapTheme.collectAsStateWithLifecycle()
    return when (theme) {
        MapTheme.DARK -> true
        MapTheme.LIGHT -> false
        MapTheme.SYSTEM -> isSystemInDarkTheme()
    }
}

/**
 * Material 3 theme. On Android 12+ it uses Material You dynamic colour from the wallpaper (unless turned
 * off in Settings); otherwise the heatmap's orange palette. The heat ramps themselves never change.
 */
@Composable
fun TickerTheme(dark: Boolean = isDarkTheme(), content: @Composable () -> Unit) {
    val dynamic by Prefs.dynamicColor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColors
        else -> lightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
