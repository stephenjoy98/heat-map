package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Minus and plus buttons for the start and the end of a dot range, under its slider: each tap moves that
 * end by one dot, and holding a button keeps stepping, faster the longer it's held. For when the slider is
 * too coarse for a finger.
 */
@Composable
internal fun RangeSteppers(state: DotEditState, modifier: Modifier = Modifier) {
    val pts = state.points
    if (pts.size < 2) return
    val r = state.range
    // Seconds shown, since neighbouring dots are often in the same minute.
    val format = DateFormat.getTimeInstance(DateFormat.MEDIUM)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Stepper(
            label = "Start",
            time = format.format(Date(pts[r.first].time)),
            canDown = r.first > 0,
            canUp = r.first < r.last,
            onDown = { state.range.let { if (it.first > 0) state.range = (it.first - 1)..it.last } },
            onUp = { state.range.let { if (it.first < it.last) state.range = (it.first + 1)..it.last } },
        )
        Stepper(
            label = "End",
            time = format.format(Date(pts[r.last].time)),
            canDown = r.last > r.first,
            canUp = r.last < pts.size - 1,
            onDown = { state.range.let { if (it.last > it.first) state.range = it.first..(it.last - 1) } },
            onUp = { state.range.let { if (it.last < state.points.size - 1) state.range = it.first..(it.last + 1) } },
        )
    }
}

@Composable
private fun Stepper(label: String, time: String, canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(R.drawable.ic_remove, "$label one dot earlier", canDown, onDown)
            Text(
                time,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 76.dp).padding(horizontal = 4.dp),
            )
            StepButton(R.drawable.ic_add, "$label one dot later", canUp, onUp)
        }
    }
}

/** A round button that steps once when pressed and keeps stepping while held. */
@Composable
private fun StepButton(icon: Int, description: String, enabled: Boolean, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    val on by rememberUpdatedState(enabled)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                contentDescription = description
                role = Role.Button
                if (!enabled) disabled()
                onClick { step(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (on) {
                        step()
                        val repeat = scope.launch {
                            delay(400)
                            var gap = 150L
                            while (on) {
                                step()
                                delay(gap)
                                gap = (gap - 10).coerceAtLeast(40L)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) { MaterialIcon(icon, size = 20) }
    }
}
