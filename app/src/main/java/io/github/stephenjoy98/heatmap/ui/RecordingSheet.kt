package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService

/**
 * Opened from the recording tile on the map: what kind of trip you're taking right now (new dots are tagged
 * with it, so the trip is filed under it without asking), and how often to record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tracking by Prefs.tracking.collectAsStateWithLifecycle()
    val currentId by Prefs.currentMode.collectAsStateWithLifecycle()
    val auto by Prefs.autoInterval.collectAsStateWithLifecycle()
    val intervalSec by Prefs.intervalSec.collectAsStateWithLifecycle()
    val current = currentId?.let { TravelMode.fromId(it) }
    // Restart the recorder so a new interval takes effect straight away.
    fun apply() {
        if (tracking) LocationTickerService.start(context)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("What kind of trip is this?", style = MaterialTheme.typography.titleLarge)
            Text(
                if (tracking) {
                    "New dots are filed under the mode you pick, so the trip is sorted without asking. " +
                        "It stays picked until you change it or stop recording."
                } else {
                    "Pick a mode before you set off: dots recorded from now on are filed under it until you stop recording."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilterChip(
                selected = current == null,
                onClick = { Prefs.setCurrentMode(null) },
                label = { Text("Detect automatically") },
            )
            ModeChips(selected = current, onPick = { Prefs.setCurrentMode(if (it == current) null else it) })

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic interval", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (auto) "Every second in a vehicle, 2 s cycling, 3 s walking, slower when still"
                        else "Record a dot at the interval you set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = auto, onCheckedChange = {
                    Prefs.setAutoInterval(it)
                    apply()
                })
            }
            if (!auto) {
                Spacer(Modifier.height(12.dp))
                Text("Record a dot every", style = MaterialTheme.typography.labelLarge)
                IntervalSlider(intervalSec) { seconds ->
                    if (seconds != intervalSec) {
                        Prefs.setIntervalSec(seconds)
                        apply()
                    }
                }
            }
        }
    }
}
