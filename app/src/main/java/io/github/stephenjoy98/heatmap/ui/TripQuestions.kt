package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

/** Which part of the Timeline is showing: its trips, or the questions waiting for an answer. */
internal object TimelineTabs {
    var questions by mutableStateOf(false)
}

private sealed class Question(val key: String) {
    class OfTrip(val trip: Trip) : Question("trip-${trip.id}")
    class OfRoad(val question: RoadQuestion, val trip: Trip?) : Question("road-${question.id}")
    class OfLevel(val question: LevelQuestion) : Question("level-${question.area}")
}

/**
 * The Timeline's Questions tab: every question waiting for an answer, one at a time in a queue. Answering one
 * brings up the next; Skip sends it to the back of the queue. The question's map fills the space left, so
 * nothing scrolls under another map.
 */
@Composable
fun QuestionsPane(
    trips: List<Trip>,
    roads: List<Pair<RoadQuestion, Trip?>>,
    levels: List<LevelQuestion>,
    onTrip: (Trip, TravelMode) -> Unit,
    onShowDay: (Trip) -> Unit,
    onRoad: (RoadQuestion, Int) -> Unit,
    onLevel: (LevelQuestion, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // Goes up when an answer is undone, so answered questions can come back.
    reset: Int = 0,
) {
    val all = remember(trips, roads, levels) {
        trips.map { Question.OfTrip(it) } + roads.map { Question.OfRoad(it.first, it.second) } + levels.map { Question.OfLevel(it) }
    }
    // Answered ones leave at once, without waiting for the list to reload.
    val answered = remember(reset) { mutableStateListOf<String>() }
    val skipped = remember { mutableStateListOf<String>() }
    val open = all.filter { it.key !in answered }
    val queue = open.filter { it.key !in skipped } + skipped.mapNotNull { k -> open.firstOrNull { it.key == k } }
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        val q = queue.firstOrNull()
        if (q == null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No questions right now", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "When the app can't tell how you travelled, which road you took or whether a place has different " +
                        "levels, it asks here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val left = if (queue.size == 1) "Last question" else "${queue.size} questions left"
            val skip: (() -> Unit)? = if (queue.size > 1) {
                {
                    skipped.remove(q.key)
                    skipped.add(q.key)
                }
            } else {
                null
            }
            val done = { answered.add(q.key); skipped.remove(q.key) }
            val fill = Modifier.fillMaxSize()
            androidx.compose.runtime.key(q.key) {
                when (q) {
                    is Question.OfTrip -> TripQuestionCard(q.trip, left, skip, { mode -> done(); onTrip(q.trip, mode) }, onShowDay, fill)
                    is Question.OfRoad -> RoadQuestionCard(q.question, q.trip, left, skip, { choice -> done(); onRoad(q.question, choice) }, fill)
                    is Question.OfLevel -> LevelQuestionCard(q.question, left, skip, { different -> done(); onLevel(q.question, different) }, fill)
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(title: String, left: String, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(left, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/**
 * "How did you travel?": a trip the app couldn't classify, on a map you can pan and zoom (green is where it
 * started, red where it ended), with every travel mode to pick from. Show on Timeline opens that day.
 */
@Composable
private fun TripQuestionCard(
    trip: Trip,
    left: String,
    onSkip: (() -> Unit)?,
    onAnswer: (TravelMode) -> Unit,
    onShowDay: (Trip) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val path by produceState<List<LatLng>>(emptyList(), trip.id) {
        value = withContext(Dispatchers.IO) {
            val p = PointStore.get(context).tripPath(trip.id)
            List(p.size / 2) { LatLng(p[it * 2], p[it * 2 + 1]) }
        }
    }
    val lineColor = MapAccent
    val lines = remember(path, lineColor) { listOf(PreviewLine(path, lineColor, 5f)) }
    val start = Date(trip.startTime)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    // Two steps: pick a mode (the best guess is picked to start with), then Save.
    var chosen by remember(trip.id) { mutableStateOf<TravelMode?>(null) }
    val answer = chosen ?: trip.inferredMode
    QuestionCard("How did you travel?", left, modifier) {
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
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))) {
            if (path.size >= 2) {
                TripPreviewLinesMap(lines, Modifier.fillMaxSize(), interactive = true, endpoints = true)
            }
        }
        Text(
            "Pinch or use + and − to zoom · green is the start, red the end",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        ModeChips(selected = chosen, suggested = trip.inferredMode, onPick = { chosen = it })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onShowDay(trip) }) { Text("Show on Timeline") }
            Spacer(Modifier.weight(1f))
            if (onSkip != null) TextButton(onClick = onSkip) { Text("Skip") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onAnswer(answer) }) { Text("Save") }
        }
    }
}

private val optionColors = listOf(Color(0xFFFF7A1A), Color(0xFF3D9BFF), Color(0xFF66BB6A), Color(0xFFAB47BC))
private val recordedColor = Color(0xFF9E9E9E)

/**
 * "Which road did you take?": a stretch where a trip's trace strayed from the matched road, with each
 * candidate road in its own colour (grey is the line as recorded) on a map you can zoom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoadQuestionCard(
    question: RoadQuestion,
    trip: Trip?,
    left: String,
    onSkip: (() -> Unit)?,
    onAnswer: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = remember(question.id) {
        var road = 0
        question.options.map { if (it.recorded) recordedColor else optionColors[road++ % optionColors.size] }
    }
    // Roads first, the recorded line last so it's drawn on top, thin.
    val order = remember(question.id) { question.options.indices.sortedBy { question.options[it].recorded } }
    val lines = remember(question.id) {
        order.map { n ->
            val o = question.options[n]
            val pts = Polyline.decode(o.poly)
            PreviewLine(List(pts.size / 2) { LatLng(pts[it * 2], pts[it * 2 + 1]) }, colors[n], if (o.recorded) 2.5f else 4.5f)
        }
    }
    // Two steps, like "How did you travel?": pick a road (the best match to start with; it's drawn bold), then Save.
    var chosen by remember(question.id) { mutableStateOf(question.options.indexOfFirst { !it.recorded }.coerceAtLeast(0)) }
    QuestionCard("Which road did you take?", left, modifier) {
        trip?.let {
            val start = Date(it.startTime)
            Text(
                "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(start)} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(start)} · ${it.mode.fullLabel}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        TripPreviewLinesMap(
            lines,
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)),
            interactive = true,
            emphasis = order.indexOf(chosen),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            question.options.forEachIndexed { n, option ->
                FilterChip(
                    selected = n == chosen,
                    onClick = { chosen = n },
                    label = { Text(option.label) },
                    leadingIcon = {
                        if (n == chosen) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        } else {
                            Box(Modifier.size(10.dp).background(colors[n], CircleShape))
                        }
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (onSkip != null) TextButton(onClick = onSkip) { Text("Skip") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onAnswer(chosen) }) { Text("Save") }
        }
    }
}

/**
 * "Different heights here?": a place where trips were recorded at two clearly different heights. It might be a
 * bridge over a road, or a different floor of a building (at home, say), or just GPS heights wandering. The
 * answer decides whether 3D routes keep the levels apart there or draw one height.
 */
@Composable
private fun LevelQuestionCard(
    q: LevelQuestion,
    left: String,
    onSkip: (() -> Unit)?,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val places by Prefs.savedPlaces.collectAsStateWithLifecycle()
    val place = places.filter { it.distanceTo(q.lat, q.lon) <= it.radiusM + 100 }.minByOrNull { it.distanceTo(q.lat, q.lon) }
    val outline = MapAccent
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
    QuestionCard("Different heights here?", left, modifier) {
        Spacer(Modifier.height(6.dp))
        Text(
            (place?.let { "At ${it.name}, your" } ?: "Your") + " trips through here were recorded at about " +
                "${Units.length(q.low)} and about ${Units.length(q.high)} above sea level. " +
                (if (place != null) "Were you on different floors, " else "Were you on different levels, like a bridge over a road or another floor, ") +
                "or is it just GPS heights wandering?",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        TripPreviewLinesMap(lines, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)), interactive = true)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onSkip != null) TextButton(onClick = onSkip) { Text("Skip") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onAnswer(false) }) { Text("Same level") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onAnswer(true) }) { Text(if (place != null) "Different floors" else "Different levels") }
        }
        Text(
            "Same level draws one height there; different levels keeps each trip at its own height in 3D.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
