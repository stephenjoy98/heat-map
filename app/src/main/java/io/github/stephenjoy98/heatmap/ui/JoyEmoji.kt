package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/**
 * Emoji artwork by JoyPixels (free licence, attribution under About), loaded from the JoyPixels CDN and
 * cached by Coil. Shows the system emoji while loading or if offline. Used only for the flags and globes in
 * the profile's country, city and continent lists; everywhere else the app uses Material icons.
 */
object JoyPixels {
    private const val BASE = "https://cdn.jsdelivr.net/joypixels/assets/11.0/png/unicode/64/"

    /** JoyPixels file names are the code points in hex joined by "-", without variation selectors or joiners. */
    fun url(emoji: String): String {
        val points = emoji.codePoints().toArray()
            .filter { it != 0xFE0F && it != 0x200D }
            .joinToString("-") { Integer.toHexString(it) }
        return "$BASE$points.png"
    }

    /** The flag emoji for an ISO 3166-1 alpha-2 country code, e.g. "CA" → 🇨🇦. */
    fun flag(countryCode: String): String {
        val code = countryCode.uppercase()
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return "🏳️"
        return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }
}

@Composable
fun JoyEmoji(emoji: String, size: Int = 24, modifier: Modifier = Modifier) {
    val fallback: @Composable () -> Unit = {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { Text(emoji, fontSize = (size * 0.8f).sp) }
    }
    SubcomposeAsyncImage(
        model = JoyPixels.url(emoji),
        contentDescription = null,
        modifier = modifier.size(size.dp),
        loading = { fallback() },
        error = { fallback() },
    )
}
