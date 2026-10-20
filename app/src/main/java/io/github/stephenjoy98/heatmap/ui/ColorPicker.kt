package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

/** An opaque ARGB colour from hue (degrees), saturation and value (0–1). */
private fun hsv(h: Float, sat: Float, v: Float): Long {
    val c = v * sat
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt() % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(f: Float) = ((f + m) * 255 + 0.5f).toLong().coerceIn(0, 255)
    return 0xFF000000L or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

// Preset colours: twelve hues round the rainbow (red, orange, yellow … magenta, rose) in a vivid, a light and a
// deep tone, then greys and browns. Each row is one line of the grid in the picker.
internal val SWATCH_ROWS: List<List<Long>> = listOf(
    List(12) { hsv(it * 30f, 0.80f, 0.92f) },
    List(12) { hsv(it * 30f, 0.38f, 0.98f) },
    List(12) { hsv(it * 30f, 0.85f, 0.58f) },
    listOf(
        0xFFFFFFFF, 0xFFE0E0E0, 0xFFBDBDBD, 0xFF9E9E9E, 0xFF757575, 0xFF616161,
        0xFF424242, 0xFF212121, 0xFFD7CCC8, 0xFFA1887F, 0xFF795548, 0xFF4E342E,
    ),
)

/** Every preset, vivid ones first (new travel modes take the first one not in use). */
internal val SWATCHES: List<Long> = SWATCH_ROWS.flatten()

/** "#RRGGBB" for an ARGB colour. */
fun hexOf(color: Long): String = String.format(Locale.US, "#%06X", color and 0xFFFFFF)

/** Parses "#RRGGBB" or "RRGGBB" (also the 3-digit "#RGB" shorthand) into an opaque ARGB colour. */
fun parseHex(text: String): Long? {
    var h = text.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    if (h.length != 6 || h.any { it.digitToIntOrNull(16) == null }) return null
    return 0xFF000000L or h.toLong(16)
}

/**
 * Picks a colour: drag on the saturation–brightness square and the hue bar, tap a swatch, or type a hex code.
 * [onReset] goes back to [default].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Long,
    default: Long,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    val current = (android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL)

    fun set(color: Long, fromText: Boolean = false) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toInt(), hsv)
        // Greys have no hue; keep the bar where it was.
        if (hsv[1] > 0f) hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        if (!fromText) hex = hexOf(color)
    }

    AlertDialog(
        // As wide as the other full-width cards, so the colour grid has room.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Swatch(Color(current), big = true)
                    Column {
                        Text("New", style = MaterialTheme.typography.labelLarge)
                        Text(hexOf(current), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.weight(1f))
                    Column {
                        Text("Default", style = MaterialTheme.typography.labelLarge)
                        Row {
                            Swatch(Color(default), onClick = { set(default) })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SaturationValueBox(hue, sat, value) { s, v ->
                    sat = s
                    value = v
                    hex = hexOf(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)).toLong())
                }
                Spacer(Modifier.height(12.dp))
                HueBar(hue) { h ->
                    hue = h
                    hex = hexOf(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, value)).toLong())
                }
                Spacer(Modifier.height(16.dp))
                // A grid that fits the dialog's width: hues across, tones down.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SWATCH_ROWS.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { c ->
                                Swatch(
                                    Color(c),
                                    selected = c == current,
                                    onClick = { set(c) },
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                val valid = parseHex(hex) != null
                OutlinedTextField(
                    value = hex,
                    onValueChange = { t ->
                        hex = t.take(7).uppercase(Locale.US)
                        parseHex(t)?.let { set(it, fromText = true) }
                    },
                    label = { Text("Hex code") },
                    placeholder = { Text("#RRGGBB") },
                    isError = !valid,
                    supportingText = { if (!valid) Text("Use 6 digits, like #4285F4") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset to default") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun Swatch(
    color: Color,
    big: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier.size(if (big) 48.dp else 32.dp),
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** Saturation left to right, brightness top to bottom, for the current [hue]. */
@Composable
private fun SaturationValueBox(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val update = { p: Offset, w: Int, h: Int ->
        onChange((p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f))
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures { update(it, size.width, size.height) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> update(change.position, size.width, size.height) } },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val c = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 10.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 12.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
    }
}

/** The rainbow of hues, 0° to 360°. */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val rainbow = remember { (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) } }
    val update = { x: Float, w: Int -> onChange((x / w).coerceIn(0f, 1f) * 360f) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { update(it.x, size.width) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> update(change.position.x, size.width) } },
    ) {
        drawRect(Brush.horizontalGradient(rainbow))
        val x = (hue / 360f) * size.width
        drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(x.coerceIn(11.dp.toPx(), size.width - 11.dp.toPx()), size.height / 2), style = Stroke(3.dp.toPx()))
    }
}
