package io.github.stephenjoy98.heatmap.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.stephenjoy98.heatmap.data.MapTheme
import io.github.stephenjoy98.heatmap.data.Prefs

val HeatOrange = Color(0xFFFF7A1A)
val RecordRed = Color(0xFFE53935)
val FriendBlue = Color(0xFF3D9BFF)

// Google-style dark: near-black background, slightly lifted surfaces, and a neutral near-white accent (like
// Android's own dark apps) rather than a coloured one.
private val darkColors = darkColorScheme(
    primary = Color(0xFFE3E3E3),
    onPrimary = Color(0xFF1B1B1B),
    primaryContainer = Color(0xFF474747),
    onPrimaryContainer = Color(0xFFE3E3E3),
    inversePrimary = Color(0xFF5E5E5E),
    secondary = Color(0xFFC7C7C7),
    onSecondary = Color(0xFF2F2F2F),
    secondaryContainer = Color(0xFF474747),
    onSecondaryContainer = Color(0xFFE3E3E3),
    tertiary = Color(0xFFC7C7C7),
    onTertiary = Color(0xFF2F2F2F),
    tertiaryContainer = Color(0xFF474747),
    onTertiaryContainer = Color(0xFFE3E3E3),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceVariant = Color(0xFF2A2A2A),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF282A2C),
    surfaceContainerHighest = Color(0xFF333537),
    outline = Color(0xFF8E918F),
    outlineVariant = Color(0xFF444746),
)

// Clean white surfaces with soft greys, in the style of an in-car navigation screen.
private val lightColors = lightColorScheme(
    primary = Color(0xFFE8590C),
    onPrimary = Color.White,
    secondary = Color(0xFF3E4A59),
    background = Color(0xFFF7F7F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFEFEF),
    surfaceContainer = Color(0xFFF4F4F6),
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFEDEDEF),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF6B6B70),
)

/**
 * Light themes put cards on a background only a shade or two lighter, which washes out (especially with Monet
 * colours from a mostly white or grey wallpaper). This gives a light-grey page with crisp white cards, sheets
 * and buttons, grey tiles inside cards, and slightly stronger secondary text and dividers.
 */
private fun ColorScheme.withLightContrast(): ColorScheme {
    val page = surfaceContainerHigh
    val card = surfaceContainerLowest
    return copy(
        background = page,
        surface = page,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = surfaceContainer,
        onSurfaceVariant = lerp(onSurfaceVariant, onSurface, 0.35f),
        outlineVariant = lerp(outlineVariant, outline, 0.3f),
    )
}

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

/** Whether Material expressive is on: Monet wallpaper colours, rounder shapes and springier motion. */
val LocalExpressive = staticCompositionLocalOf { false }

/** Material expressive needs Android 12's Monet colour engine. */
val expressiveSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// Expressive shapes: generously rounded, in the spirit of Material 3 Expressive.
private val expressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

// Type a size up from Material's defaults, without the extra letter spacing, closer to how Gmail and other
// Google apps set their text: easier to read at arm's length.
private val baseTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontSize = 23.sp, lineHeight = 30.sp),
        titleMedium = titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    )
}

// Emphasised type for headings when expressive is on.
private val expressiveTypography = baseTypography.run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * Spatial motion (moving and resizing things): a gentle bouncy spring with Material expressive, a quick
 * settle without overshoot otherwise.
 */
@Composable
fun <T> spatialSpring(): FiniteAnimationSpec<T> =
    if (LocalExpressive.current) spring(dampingRatio = 0.72f, stiffness = 380f) else spring(dampingRatio = 1f, stiffness = 600f)

/**
 * Material 3 theme. Light, dark or following the system; with Material expressive on (Android 12+), colours
 * come from the wallpaper through Android's Monet engine, with expressive shapes, type and motion. Otherwise
 * a neutral dark theme or the orange light one. The heat ramps themselves never change.
 */
@Composable
fun TickerTheme(dark: Boolean = isDarkTheme(), content: @Composable () -> Unit) {
    val wanted by Prefs.dynamicColor.collectAsStateWithLifecycle()
    val expressive = wanted && expressiveSupported
    val context = LocalContext.current
    val scheme = when {
        expressive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColors
        else -> lightColors
    }.let { if (dark) it else it.withLightContrast() }
    CompositionLocalProvider(LocalExpressive provides expressive) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = if (expressive) expressiveShapes else MaterialTheme.shapes,
            typography = if (expressive) expressiveTypography else baseTypography,
            content = content,
        )
    }
}
