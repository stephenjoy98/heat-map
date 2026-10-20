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
import androidx.compose.material.icons.filled.Check
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
