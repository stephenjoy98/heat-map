package io.github.stephenjoy98.heatmap.ui

import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
    var editing by remember { mutableStateOf<TravelMode?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TravelMode?>(null) }

    Section("Travel modes") {
        (TravelMode.ordered + TravelMode.UNKNOWN).forEachIndexed { i, mode ->
            val sub = mode.parentId != null
            if (i > 0) ItemDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { editing = mode }
                    .padding(start = if (sub) 40.dp else 16.dp, end = if (mode.deletable) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(if (sub) 22.dp else 28.dp).background(Color(mode.color), CircleShape))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            sub -> "In ${mode.parent?.label} · ${mode.kind.label}"
                            !mode.builtIn -> "Added · ${mode.kind.label}"
                            mode.color != mode.defaultColor -> "Custom colour"
                            else -> "Built in"
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

    if (adding) {
        ModeDialog(mode = null, onDismiss = { adding = false }) { label, color, kind, parent ->
            Prefs.addMode(label, color, kind, parent)
            adding = false
        }
    }
    editing?.let { mode ->
        ModeDialog(mode = mode, onDismiss = { editing = null }) { label, color, kind, parent ->
            val rematch = !mode.builtIn && kind != mode.kind
            Prefs.editMode(mode, label, color, kind, parent)
            // Built-in modes keep their default colour; the one picked is saved as a custom colour.
            if (mode.builtIn) Prefs.setModeColor(mode, color)
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
        val subs = mode.children
        val parent = mode.parent
        val trips by produceState<Int?>(null, mode) {
            value = withContext(Dispatchers.IO) { mode.withChildren.sumOf { PointStore.get(context).tripCountForMode(it) } }
        }
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete ${mode.label}?") },
            text = {
                val becomes = parent?.label ?: "Other"
                val ask = if (parent == null) ", and you'll be asked how you travelled them" else ""
                val count = when (val n = trips) {
                    null -> "Counting trips…"
                    0 -> "No trips use it."
                    1 -> "1 trip becomes $becomes$ask."
                    else -> "$n trips become $becomes$ask."
                }
                val also = if (subs.isNotEmpty()) " Its sub-modes (${subs.joinToString { it.label }}) are deleted too." else ""
                Text(count + also + if (mode.builtIn) " You can add it back later." else "")
            },
            confirmButton = {
                TextButton(
                    enabled = trips != null,
                    onClick = {
                        deleting = null
                        scope.launch {
                            val store = PointStore.get(context)
                            // Sub-modes go first, then the mode: its trips go to its parent, or to Other.
                            for (m in subs + mode) {
                                val days = withContext(Dispatchers.IO) { store.replaceMode(m, if (m == mode) parent ?: TravelMode.UNKNOWN else TravelMode.UNKNOWN) }
                                Prefs.deleteMode(m)
                                Prefs.addDirtyBackupDays(days)
                            }
                            RoadSnapWorker.runNow(context)
                        }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Adds a travel mode ([mode] null) or edits one. Built-in modes can be renamed and recoloured; modes you
 * added can also change what they travel like and which mode they're part of.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeDialog(
    mode: TravelMode?,
    onDismiss: () -> Unit,
    onSave: (String, Long, TravelMode.Kind, TravelMode?) -> Unit,
) {
    val suggested = remember {
        val used = TravelMode.entries.map { it.color }.toSet()
        SWATCHES.firstOrNull { it !in used } ?: SWATCHES.first()
    }
    val builtIn = mode?.builtIn == true
    var label by remember { mutableStateOf(mode?.label.orEmpty()) }
    var kind by remember { mutableStateOf(mode?.kind ?: TravelMode.Kind.CAR) }
    var color by remember { mutableStateOf(mode?.color ?: suggested) }
    var parent by remember { mutableStateOf(mode?.parent) }
    var picking by remember { mutableStateOf(false) }
    // A mode with sub-modes can't become a sub-mode itself (one level only).
    val parents = TravelMode.topLevel.filter { it != mode }
    val canNest = !builtIn && mode?.children.isNullOrEmpty()

    val name = label.trim()
    val taken = TravelMode.entries.any { it != mode && it.parentId == parent?.id && it.label.equals(name, ignoreCase = true) } ||
        TravelMode.removedBuiltIns.any { it.label.equals(name, ignoreCase = true) }
    val error = if (taken) "There's already a mode called $name" else null

    AlertDialog(

        properties = DialogProperties(usePlatformDefaultWidth = false),

        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onDismissRequest = onDismiss,
        title = { Text(if (mode == null) "Add travel mode" else "Edit ${mode.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
                if (canNest) {
                    Spacer(Modifier.height(4.dp))
                    Text("Part of", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Make it a sub-mode, like Bus in PublicTransit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = parent == null, onClick = { parent = null }, label = { Text("Nothing") })
                        parents.forEach { p ->
                            FilterChip(
                                selected = p == parent,
                                onClick = {
                                    parent = p
                                    // A new sub-mode usually travels like its parent.
                                    if (mode == null) kind = p.kind
                                },
                                label = { Text(p.label) },
                                leadingIcon = { ModeDot(p) },
                            )
                        }
                    }
                }
                if (!builtIn) {
                    Spacer(Modifier.height(8.dp))
                    Text("Travels like", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Used to match trips to footpaths, cycle routes, roads or bus routes. Trains and boats aren't matched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TravelMode.Kind.choosable.forEach { k ->
                            FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) })
                        }
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
            TextButton(onClick = { onSave(name, color, kind, parent) }, enabled = name.isNotEmpty() && error == null) {
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
