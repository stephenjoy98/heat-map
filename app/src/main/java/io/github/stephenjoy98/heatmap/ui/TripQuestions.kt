package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.ui.graphics.Color
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.RoadQuestion
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.Cells
import io.github.stephenjoy98.heatmap.data.LevelQuestion
import io.github.stephenjoy98.heatmap.data.Prefs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.data.Trip
import io.github.stephenjoy98.heatmap.data.Units
import io.github.stephenjoy98.heatmap.review.Insights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.text.DateFormat
import java.util.Date

/**
 * "How did you travel?" at the top of the Timeline: one trip the app couldn't classify at a time, on a map
 * you can pan and zoom (green is where it started, red where it ended), with every travel mode to pick from.
 * ‹ › step through the trips waiting; Show on Timeline opens that day.
 */
@Composable
fun TripQuestionsCard(
    questions: List<Trip>,
    onAnswer: (Trip, TravelMode) -> Unit,
    onShowDay: (Trip) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (questions.isEmpty()) return
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    val i = index.coerceIn(0, questions.size - 1)
    val trip = questions[i]
    val path by produceState<List<LatLng>>(emptyList(), trip.id) {
        value = withContext(Dispatchers.IO) {
            val p = PointStore.get(context).tripPath(trip.id)
            List(p.size / 2) { LatLng(p[it * 2], p[it * 2 + 1]) }
        }
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val lines = remember(path, lineColor) { listOf(PreviewLine(path, lineColor, 5f)) }
    val start = Date(trip.startTime)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)

    ElevatedCard(
        modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("How did you travel?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (questions.size == 1) "1 trip waiting for your answer" else "${i + 1} of ${questions.size} trips waiting for your answer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (questions.size > 1) {
                    Tip("Previous trip") {
                        IconButton(onClick = { index = i - 1 }, enabled = i > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous trip")
                        }
                    }
                    Tip("Next trip") {
                        IconButton(onClick = { index = i + 1 }, enabled = i < questions.size - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next trip")
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(start)} · ${time.format(start)}–${time.format(Date(trip.endTime))}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${Insights.km(trip.distanceM / 1000)} · average ${Units.speed(trip.avgSpeedKmh)} · best guess: ${trip.inferredMode.fullLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (path.size >= 2) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.runtime.key(trip.id) {
                    TripPreviewLinesMap(
                        lines,
                        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                        interactive = true,
                        endpoints = true,
                    )
                }
                Text(
                    "Pinch or use + and − to zoom · green is the start, red the end",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            ModeChips(selected = null, suggested = trip.inferredMode, onPick = { onAnswer(trip, it) })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onShowDay(trip) }) { Text("Show on Timeline") }
            }
        }
    }
}

private val optionColors = listOf(Color(0xFFFF7A1A), Color(0xFF3D9BFF), Color(0xFF66BB6A), Color(0xFFAB47BC))
private val recordedColor = Color(0xFF9E9E9E)

/**
 * "Which road did you take?": stretches where a trip's trace strayed from the matched road, one at a time,
 * with each candidate road in its own colour (grey is the line as recorded) on a map you can zoom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoadQuestionsCard(
    questions: List<Pair<RoadQuestion, Trip?>>,
    onAnswer: (RoadQuestion, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (questions.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    val i = index.coerceIn(0, questions.size - 1)
    val (question, trip) = questions[i]
    val colors = remember(question.id) {
        var road = 0
        question.options.map { if (it.recorded) recordedColor else optionColors[road++ % optionColors.size] }
    }
    val lines = remember(question.id) {
        question.options.mapIndexed { n, o ->
            val pts = Polyline.decode(o.poly)
            PreviewLine(List(pts.size / 2) { LatLng(pts[it * 2], pts[it * 2 + 1]) }, colors[n], if (o.recorded) 2.5f else 4.5f)
        }.sortedByDescending { it.widthDp } // Recorded line on top, thin.
    }
    ElevatedCard(
        modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Which road did you take?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (questions.size == 1) "1 stretch to check" else "${i + 1} of ${questions.size} stretches to check",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (questions.size > 1) {
                    Tip("Previous") {
                        IconButton(onClick = { index = i - 1 }, enabled = i > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                        }
                    }
                    Tip("Next") {
                        IconButton(onClick = { index = i + 1 }, enabled = i < questions.size - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                        }
                    }
                }
            }
            trip?.let {
                val start = Date(it.startTime)
                Text(
                    "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(start)} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(start)} · ${it.mode.fullLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.runtime.key(question.id) {
                TripPreviewLinesMap(lines, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)), interactive = true)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                question.options.forEachIndexed { n, option ->
                    AssistChip(
                        onClick = { onAnswer(question, n) },
                        label = { Text(option.label) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(colors[n], CircleShape)) },
                    )
                }
            }
        }
    }
}

/**
 * "Different heights here?": a place where trips were recorded at two clearly different heights. It might be a
 * bridge over a road, or a different floor of a building (at home, say), or just GPS heights wandering. The
 * answer decides whether 3D routes keep the levels apart there or draw one height.
 */
@Composable
fun LevelQuestionsCard(
    questions: List<LevelQuestion>,
    onAnswer: (LevelQuestion, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (questions.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    val i = index.coerceIn(0, questions.size - 1)
    val q = questions[i]
    val places by Prefs.savedPlaces.collectAsStateWithLifecycle()
    val place = places.filter { it.distanceTo(q.lat, q.lon) <= it.radiusM + 100 }.minByOrNull { it.distanceTo(q.lat, q.lon) }
    val outline = MaterialTheme.colorScheme.primary
    // The map square in question, outlined.
    val lines = remember(q.area, outline) {
        val row = Cells.rowOf(q.area)
        val col = Cells.colOf(q.area)
        val s = Cells.south(row)
        val w = Cells.west(col)
        val n = Cells.south(row + 1)
        val e = Cells.west(col + 1)
        listOf(PreviewLine(listOf(LatLng(s, w), LatLng(s, e), LatLng(n, e), LatLng(n, w), LatLng(s, w)), outline, 3f))
    }
    ElevatedCard(
        modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Different heights here?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (questions.size == 1) "1 place to check" else "${i + 1} of ${questions.size} places to check",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (questions.size > 1) {
                    Tip("Previous") {
                        IconButton(onClick = { index = i - 1 }, enabled = i > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                        }
                    }
                    Tip("Next") {
                        IconButton(onClick = { index = i + 1 }, enabled = i < questions.size - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                (place?.let { "At ${it.name}, your" } ?: "Your") + " trips through here were recorded at about " +
                    "${Units.length(q.low)} and about ${Units.length(q.high)} above sea level. " +
                    (if (place != null) "Were you on different floors, " else "Were you on different levels, like a bridge over a road or another floor, ") +
                    "or is it just GPS heights wandering?",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.runtime.key(q.area) {
                TripPreviewLinesMap(lines, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)), interactive = true)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onAnswer(q, false) }) { Text("Same level") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onAnswer(q, true) }) { Text(if (place != null) "Different floors" else "Different levels") }
            }
            Text(
                "Same level draws one height there; different levels keeps each trip at its own height in 3D.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
