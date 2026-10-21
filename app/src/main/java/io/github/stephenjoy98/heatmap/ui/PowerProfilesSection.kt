package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.PowerProfile
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService

/**
 * Settings › Battery › Profiles: User (your own Recording settings), Performance, Default and Battery saver,
 * each with what it records and how precisely, so you can pick one. Changing a Recording setting by hand
 * switches back to User.
 */
@Composable
internal fun PowerProfilesSection() {
    val context = LocalContext.current
    val current by Prefs.powerProfile.collectAsStateWithLifecycle()
    // Re-read when any Recording setting changes, so User's summary stays current.
    val interval by Prefs.intervalSec.collectAsStateWithLifecycle()
    val auto by Prefs.autoInterval.collectAsStateWithLifecycle()
    val high by Prefs.highAccuracy.collectAsStateWithLifecycle()

    Section("Profiles") {
        Text(
            "How often dots are recorded and how precisely. Saved places still slow recording down while you're " +
                "settled there, whichever profile is on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
        )
        PowerProfile.entries.forEachIndexed { i, p ->
            if (i > 0) ItemDivider()
            val details = if (p == PowerProfile.USER) {
                // Keyed on the settings above so it updates as they change.
                val mine = remember(interval, auto, high) { Prefs.userRecording }
                listOf(
                    if (mine.auto) "Automatic: ${pace(p)}" else "Every ${formatInterval(mine.intervalSec)}, whatever you're doing",
                    accuracy(mine.highAccuracy),
                    "Change these under Recording.",
                )
            } else {
                listOf(pace(p).replaceFirstChar { it.uppercase() }, accuracy(p.highAccuracy), p.battery)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = p == current, role = Role.RadioButton) {
                        Prefs.applyPowerProfile(p)
                        // A running recorder picks up the new pace when it's restarted.
                        if (Prefs.tracking.value) LocationTickerService.start(context)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(selected = p == current, onClick = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.bodyLarge)
                    details.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun pace(p: PowerProfile): String =
    "every ${formatInterval(p.vehicleSec)} in a vehicle, ${formatInterval(p.cycleSec)} cycling, " +
        "${formatInterval(p.walkSec)} walking; ${formatInterval(p.stillSec)} when still and " +
        "${formatInterval(p.longStillSec)} after a while"

private fun accuracy(high: Boolean): String =
    if (high) "Precise GPS (dots within 100 m kept)" else "Wi-Fi and mobile networks, GPS only when needed (dots within 500 m kept)"
