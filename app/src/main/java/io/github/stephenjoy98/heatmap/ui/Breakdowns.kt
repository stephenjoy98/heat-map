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
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.Prefs
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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

/** One part of a pie chart: what it is, how much, its colour and the value as shown beside it. */
class ChartSlice(val label: String, val value: Double, val color: Color, val valueText: String)

/**
 * A card's title with a button on the right that switches its breakdown between bars and a pie chart. The choice
 * is shared by every such card and remembered.
 */
@Composable
fun BreakdownHeader(title: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val pie by Prefs.breakdownPie.collectAsStateWithLifecycle()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = style, modifier = Modifier.weight(1f))
        val label = if (pie) "Show as bars" else "Show as a pie chart"
        Tip(label) {
            IconButton(onClick = { Prefs.setBreakdownPie(!pie) }, modifier = Modifier.size(36.dp).semantics { contentDescription = label }) {
                // The button shows what you'd switch to.
                MaterialIcon(if (pie) R.drawable.ic_bar_chart else R.drawable.ic_pie_chart, size = 22)
            }
        }
    }
}

/** Bars or a pie chart, whichever the cards are set to, fading from one to the other. */
@Composable
fun BarsOrPie(slices: List<ChartSlice>, bars: @Composable () -> Unit) {
    val pie by Prefs.breakdownPie.collectAsStateWithLifecycle()
    Crossfade(targetState = pie && slices.any { it.value > 0 }, label = "breakdown") { asPie ->
        if (asPie) PieChart(slices) else bars()
    }
}

/** A ring chart of [slices] (largest first) beside a legend with each one's share and value. */
@Composable
fun PieChart(slices: List<ChartSlice>) {
    val shown = slices.filter { it.value > 0 }.sortedByDescending { it.value }
    val total = shown.sumOf { it.value }
    if (total <= 0) return
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(132.dp)) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            // A thin gap between slices so neighbours with similar colours stay apart.
            val gap = if (shown.size > 1) 1.5f else 0f
            var start = -90f
            for (s in shown) {
                val sweep = (s.value / total * 360).toFloat()
                if (sweep > gap) {
                    drawArc(s.color, start + gap / 2, sweep - gap, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                }
                start += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in shown) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(s.color, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        "${(s.value / total * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(
                    s.valueText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp),
                )
            }
        }
    }
}

/** Travel modes as pie slices: top-level modes with their sub-modes added in, like the bars. */
fun modeSlices(kmByMode: Map<TravelMode, Double>): List<ChartSlice> =
    (TravelMode.topLevel + TravelMode.UNKNOWN).map { m ->
        val km = m.withChildren.sumOf { kmByMode[it] ?: 0.0 }
        ChartSlice(m.label, km, Color(m.color), Insights.km(km))
    }

/** The travel mode bars, or the pie chart: for the "By travel mode" cards. */
@Composable
fun ModeBreakdown(kmByMode: Map<TravelMode, Double>) {
    BarsOrPie(modeSlices(kmByMode)) { ModeBreakdownRows(kmByMode) }
}
