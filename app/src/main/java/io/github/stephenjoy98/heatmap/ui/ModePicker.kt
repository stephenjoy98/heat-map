package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.data.TravelMode

/** A travel mode's colour as a small dot. */
@Composable
fun ModeDot(mode: TravelMode, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(Color(mode.color), CircleShape))
}

/**
 * Every pickable travel mode as chips, each sub-mode right after its parent (PublicTransit, SkyTrain, Bus…).
 * [selected] is ticked; [suggested] (the app's best guess) is shown selected too until something is picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModeChips(
    selected: TravelMode?,
    onPick: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
    suggested: TravelMode? = null,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TravelMode.ordered.forEach { m ->
            FilterChip(
                selected = m == (selected ?: suggested),
                onClick = { onPick(m) },
                label = { Text(m.label) },
                leadingIcon = { ModeDot(m) },
                // Sub-modes sit a little apart from their parent's group.
                modifier = if (m.parentId != null) Modifier.padding(start = 2.dp) else Modifier,
            )
        }
    }
}

/** Menu items for every pickable mode, sub-modes indented under their parent, with a tick on [current]. */
@Composable
fun ColumnScope.ModeMenuItems(current: TravelMode?, onPick: (TravelMode) -> Unit) {
    TravelMode.ordered.forEach { m ->
        val sub = m.parentId != null
        DropdownMenuItem(
            text = {
                Text(
                    m.label,
                    style = if (sub) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                )
            },
            leadingIcon = { ModeDot(m, if (sub) 10.dp else 12.dp) },
            trailingIcon = { if (m == current) Icon(Icons.Filled.Check, contentDescription = "Current") },
            onClick = { onPick(m) },
            modifier = if (sub) Modifier.padding(start = 20.dp) else Modifier,
        )
    }
}

/**
 * The travel mode as one compact field that opens a menu of every mode, for the editing panels, where the map
 * needs the room more than a wall of chips does.
 */
@Composable
fun ModeDropdown(
    selected: TravelMode?,
    onPick: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "How did you travel?",
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) {
                    ModeDot(selected, 12.dp)
                    Spacer(Modifier.width(10.dp))
                }
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selected?.fullLabel ?: "Pick a travel mode", style = MaterialTheme.typography.bodyLarge)
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose")
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ModeMenuItems(current = selected) {
                onPick(it)
                open = false
            }
        }
    }
}
