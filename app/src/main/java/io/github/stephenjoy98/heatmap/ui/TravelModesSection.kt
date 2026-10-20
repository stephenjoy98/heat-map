package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.data.ModeColors
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.roads.RoadSnapWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Travel modes: every mode with its colour (on the Timeline, Insights and the map filters). Tap a
 * mode to change its colour, or for modes you added, its name and kind too. Add your own modes, delete ones
 * you don't use (their trips become Other), and add deleted built-in modes back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TravelModesSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recolouring by remember { mutableStateOf<TravelMode?>(null) }
    var editing by remember { mutableStateOf<TravelMode?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TravelMode?>(null) }

    Section("Travel modes") {
        (TravelMode.choosable + TravelMode.UNKNOWN).forEachIndexed { i, mode ->
            if (i > 0) ItemDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { if (mode.builtIn) recolouring = mode else editing = mode }
                    .padding(start = 16.dp, end = if (mode.deletable) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).background(Color(mode.color), CircleShape))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            !mode.builtIn -> "Added · ${mode.kind.label}"
                            mode.color != mode.defaultColor -> "Custom colour"
                            else -> "Default colour"
                        },
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
                if (mode.deletable) {
                    Tip("Delete ${mode.label}") {
                        IconButton(onClick = { deleting = mode }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${mode.label}") }
                    }
                }
            }
        }
        ItemDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { adding = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Text("Add travel mode", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        val removed = TravelMode.removedBuiltIns
        if (removed.isNotEmpty()) {
            ItemDivider()
            Text(
                "Add back",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp),
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                removed.forEach { m ->
                    AssistChip(
                        onClick = { Prefs.restoreMode(m) },
                        label = { Text(m.label) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(m.color), CircleShape)) },
                    )
                }
            }
        }
        if (ModeColors.anyCustom) {
            ItemDivider()
            TextButton(onClick = Prefs::resetModeColors, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Reset colours to default")
            }
        }
    }

    recolouring?.let { mode ->
        ColorPickerDialog(
            title = "${mode.label} colour",
            initial = mode.color,
            default = mode.defaultColor,
            onDismiss = { recolouring = null },
            onReset = {
                Prefs.setModeColor(mode, null)
                recolouring = null
            },
            onPick = {
                Prefs.setModeColor(mode, it)
                recolouring = null
            },
        )
    }

    if (adding) {
        ModeDialog(mode = null, onDismiss = { adding = false }) { label, color, kind ->
            Prefs.addMode(label, color, kind)
            adding = false
        }
    }
    editing?.let { mode ->
        ModeDialog(mode = mode, onDismiss = { editing = null }) { label, color, kind ->
            val rematch = kind != mode.kind
            Prefs.editMode(mode, label, color, kind)
            editing = null
            if (rematch) {
                // Match its trips to footpaths, cycle routes, roads or bus routes again.
                scope.launch {
                    withContext(Dispatchers.IO) { PointStore.get(context).clearRoadMatches(mode) }
                    RoadSnapWorker.runNow(context)
                }
            }
        }
    }

    deleting?.let { mode ->
        val trips by produceState<Int?>(null, mode) {
            value = withContext(Dispatchers.IO) { PointStore.get(context).tripCountForMode(mode) }
        }
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete ${mode.label}?") },
            text = {
                val count = when (val n = trips) {
                    null -> "Counting trips…"
                    0 -> "No trips use it."
                    1 -> "1 trip travelled by ${mode.label} becomes Other, and Insights will ask how you travelled it."
                    else -> "$n trips travelled by ${mode.label} become Other, and Insights will ask how you travelled them."
                }
                Text(if (mode.builtIn) "$count You can add it back later." else count)
            },
            confirmButton = {
                TextButton(
                    enabled = trips != null,
                    onClick = {
                        deleting = null
                        scope.launch {
                            val days = withContext(Dispatchers.IO) { PointStore.get(context).replaceMode(mode) }
                            Prefs.deleteMode(mode)
                            Prefs.addDirtyBackupDays(days)
                            RoadSnapWorker.runNow(context)
                        }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Adds a travel mode ([mode] null) or edits one you added: its name, what it travels like and its colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeDialog(mode: TravelMode?, onDismiss: () -> Unit, onSave: (String, Long, TravelMode.Kind) -> Unit) {
    val suggested = remember {
        val used = TravelMode.entries.map { it.color }.toSet()
        SWATCHES.firstOrNull { it !in used } ?: SWATCHES.first()
    }
    var label by remember { mutableStateOf(mode?.label.orEmpty()) }
    var kind by remember { mutableStateOf(mode?.kind ?: TravelMode.Kind.CAR) }
    var color by remember { mutableStateOf(mode?.defaultColor ?: suggested) }
    var picking by remember { mutableStateOf(false) }

    val name = label.trim()
    val taken = TravelMode.entries.any { it != mode && it.label.equals(name, ignoreCase = true) } ||
        TravelMode.removedBuiltIns.any { it.label.equals(name, ignoreCase = true) }
    val error = when {
        taken -> "There's already a mode called $name"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == null) "Add travel mode" else "Edit ${mode.label}") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(24) },
                    label = { Text("Name") },
                    placeholder = { Text("Ferry, Scooter, Train…") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Travels like", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Used to match trips to footpaths, cycle routes, roads or bus routes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelMode.Kind.choosable.forEach { k ->
                        FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(32.dp).background(Color(color), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(hexOf(color), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = true }) { Text("Change") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color, kind) }, enabled = name.isNotEmpty() && error == null) {
                Text(if (mode == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        val start = mode?.defaultColor ?: suggested
        ColorPickerDialog(
            title = "Colour",
            initial = color,
            default = start,
            onDismiss = { picking = false },
            onReset = {
                color = start
                picking = false
            },
            onPick = {
                color = it
                picking = false
            },
        )
    }
}
