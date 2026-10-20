package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.data.ModeColors
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode

/** Settings → Travel mode colours: the colour of each mode on the Timeline, Insights and the map filters. */
@Composable
fun ModeColorsSection() {
    var editing by remember { mutableStateOf<TravelMode?>(null) }
    Section("Travel mode colours") {
        (TravelMode.choosable + TravelMode.UNKNOWN).forEachIndexed { i, mode ->
            if (i > 0) ItemDivider()
            val custom = mode.color != mode.defaultColor
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { editing = mode }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).background(Color(mode.color), CircleShape))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (custom) "Custom" else "Default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    hexOf(mode.color),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (ModeColors.anyCustom) {
            ItemDivider()
            TextButton(onClick = Prefs::resetModeColors, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Reset all to default")
            }
        }
    }

    editing?.let { mode ->
        ColorPickerDialog(
            title = "${mode.label} colour",
            initial = mode.color,
            default = mode.defaultColor,
            onDismiss = { editing = null },
            onReset = {
                Prefs.setModeColor(mode, null)
                editing = null
            },
            onPick = {
                Prefs.setModeColor(mode, it)
                editing = null
            },
        )
    }
}
