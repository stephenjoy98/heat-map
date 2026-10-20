package io.github.stephenjoy98.heatmap.ui

import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.review.Insights

/**
 * Distance by travel mode, one bar per top-level mode. A mode with sub-modes (PublicTransit: SkyTrain, Bus,
 * SeaBus) shows them all added together; tap it to open it up and see each one.
 */
@Composable
fun ModeBreakdownRows(kmByMode: Map<TravelMode, Double>) {
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    fun total(m: TravelMode) = m.withChildren.sumOf { kmByMode[it] ?: 0.0 }
    val top = TravelMode.topLevel + TravelMode.UNKNOWN
    val max = top.maxOfOrNull { total(it) } ?: 0.0
    if (max <= 0) {
        Text("No trips yet in this period.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (mode in top) {
            val km = total(mode)
            if (km <= 0 && mode == TravelMode.UNKNOWN) continue
            val kids = mode.children
            val open = expanded[mode.id] == true
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (kids.isNotEmpty()) Modifier.clickable { expanded[mode.id] = !open } else Modifier),
                ) {
                    ModeBar(mode.label, km, max, Color(mode.color))
                    if (kids.isNotEmpty()) {
                        Icon(
                            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (open) "Hide ${mode.label} modes" else "Show ${mode.label} modes",
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Spacer(Modifier.width(20.dp))
                    }
                }
                AnimatedVisibility(visible = open) {
                    Column(Modifier.padding(start = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The parent mode itself, for trips filed under it without a sub-mode.
                        (listOf(mode) + kids).forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ModeBar(if (m == mode) "Other ${mode.label}" else m.label, kmByMode[m] ?: 0.0, max, Color(m.color), small = true)
                                Spacer(Modifier.width(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ModeBar(label: String, km: Double, max: Double, color: Color, small: Boolean = false) {
    Text(
        label,
        Modifier.width(if (small) 104.dp else 120.dp),
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        maxLines = 1,
    )
    Box(Modifier.weight(1f).height(if (small) 8.dp else 10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (max > 0 && km > 0) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((km / max).toFloat().coerceIn(0.02f, 1f)).background(color))
        }
    }
    Text(Insights.km(km), Modifier.width(72.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
}

/** A list in a dialog: a short explanation, then rows of a name and a value. */
@Composable
fun ListDialog(title: String, explanation: String, rows: List<Pair<String, String>>, empty: String, onDismiss: () -> Unit) {
    AlertDialog(
        // As wide as the other full-width cards, rather than the platform's narrow dialog width.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (rows.isEmpty()) {
                    Text(empty, style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(rows) { (name, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** What a map square is, for the areas explanations. */
const val AREA_EXPLANATION =
    "The map is divided into squares about 220 m across. An area is one of those squares with at least one of " +
        "your dots in it."
