package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.BatteryUse
import io.github.stephenjoy98.heatmap.data.PointStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Settings → Battery: how fast the battery drained while recording at each interval, over the last week or
 * month, as one bar per interval (percent per hour). Screen off by default, which leaves out most of what
 * other apps and the display use; "All" includes everything the phone did while recording.
 */
@Composable
fun BatterySection() {
    val context = LocalContext.current
    val store = remember(context) { PointStore.get(context) }
    val changes by store.changes.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(7) }
    var screenOffOnly by rememberSaveable { mutableStateOf(true) }
    val uses by produceState<List<BatteryUse>?>(null, days, screenOffOnly, changes) {
        val from = System.currentTimeMillis() - days * 86_400_000L
        // A few minutes at an interval says little; keep the ones with enough time behind them.
        value = withContext(Dispatchers.IO) { store.batteryByInterval(from, screenOffOnly).filter { it.hours >= 0.1 } }
    }

    Section("Battery") {
        Column(Modifier.padding(16.dp)) {
            Text("Battery used while recording", style = MaterialTheme.typography.labelLarge)
            Text(
                "Percent of the battery per hour at each recording interval, measured on battery power while recording.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SegmentedChoice(options = listOf(7 to "Last 7 days", 30 to "Last 30 days"), selected = days, onSelect = { days = it })
            Spacer(Modifier.height(8.dp))
            SegmentedChoice(options = listOf(true to "Screen off", false to "All"), selected = screenOffOnly, onSelect = { screenOffOnly = it })
            Spacer(Modifier.height(16.dp))
            val list = uses
            when {
                list == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                list.isEmpty() -> Text(
                    "Nothing measured yet. Battery use is noted while recording and shows here once there's an hour or " +
                        "so of it" + (if (screenOffOnly) " with the screen off." else "."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> BatteryBars(list)
            }
        }
    }
}

/** One horizontal bar per interval, longest = fastest drain, with the value and hours measured beside it. */
@Composable
private fun BatteryBars(uses: List<BatteryUse>) {
    val max = uses.maxOf { it.percentPerHour }.coerceAtLeast(0.01)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        uses.forEach { u ->
            val rate = String.format(Locale.US, if (u.percentPerHour < 10) "%.1f" else "%.0f", u.percentPerHour)
            val hours = if (u.hours < 10) String.format(Locale.US, "%.1f h", u.hours) else "${u.hours.toInt()} h"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Every ${formatInterval(u.intervalSec)}: $rate percent per hour over $hours"
                },
            ) {
                Text(
                    formatInterval(u.intervalSec),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(64.dp),
                )
                Box(Modifier.weight(1f).height(14.dp)) {
                    val share = (u.percentPerHour / max).toFloat().coerceIn(0.02f, 1f)
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(share)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    )
                }
                Column(Modifier.width(76.dp).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                    Text("$rate %/h", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                    Text(hours, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
