package io.github.stephenjoy98.heatmap.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.RoadQuestion
import io.github.stephenjoy98.heatmap.data.Trip
import io.github.stephenjoy98.heatmap.roads.RoadSnapWorker
import io.github.stephenjoy98.heatmap.roads.RoadSnapper
import io.github.stephenjoy98.heatmap.review.HealthSteps
import io.github.stephenjoy98.heatmap.review.InsightResult
import io.github.stephenjoy98.heatmap.review.Insights
import io.github.stephenjoy98.heatmap.review.ReviewData
import io.github.stephenjoy98.heatmap.review.ReviewPeriod
import io.github.stephenjoy98.heatmap.review.ReviewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(contentPadding: PaddingValues, resumeTick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    var period by rememberSaveable { mutableStateOf(ReviewPeriod.WEEK) }
    var refresh by remember { mutableIntStateOf(0) }
    val changes by store.changes.collectAsStateWithLifecycle()
    val data by produceState<ReviewData?>(null, period, changes, refresh, resumeTick) {
        value = withContext(Dispatchers.IO) { ReviewRepository.load(context, period) }
    }
    val roadQuestions by produceState(emptyList<Pair<RoadQuestion, Trip?>>(), changes, refresh, resumeTick) {
        value = withContext(Dispatchers.IO) { store.openRoadQuestions().take(10).map { it to store.trip(it.tripId) } }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ReviewPeriod.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = p == period,
                            onClick = { period = p },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ReviewPeriod.entries.size),
                        ) { Text(p.label, maxLines = 1) }
                    }
                }
            }

            val d = data
            if (d == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                return@LazyColumn
            }

            if (d.questions.isNotEmpty()) {
                item {
                    Text("How did you travel?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The app couldn't tell these trips apart. Your answer also teaches it your regular routes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(d.questions.take(10), key = { it.id }) { trip ->
                    QuestionCard(trip) { mode ->
                        store.setTripMode(trip.id, mode)
                        refresh++
                    }
                }
            }

            item { StatGrid(d) }
            item { ModeBreakdown(d) }
            item { StepsCard(d, resumeTick) { refresh++ } }
            if (d.cities.isNotEmpty()) item { CitiesCard(d) }
            item { InsightsCard(d) }
        }
    }
}

@Composable
private fun StatGrid(d: ReviewData) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Insights.km(d.totalKm), "travelled", Modifier.weight(1f))
            StatTile(formatCount(d.newAreas), "new areas explored", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(d.cities.size.toString(), if (d.cities.size == 1) "city" else "cities", Modifier.weight(1f))
            StatTile(d.steps?.let { formatCount(it.perDay) } ?: "–", "steps a day", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModeBreakdown(d: ReviewData) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("By travel mode", style = MaterialTheme.typography.titleMedium)
            val max = d.kmByMode.values.maxOrNull() ?: 0.0
            if (max <= 0) {
                Text("No trips yet in this period.", style = MaterialTheme.typography.bodySmall)
            }
            for (mode in TravelMode.choosable + TravelMode.UNKNOWN) {
                val km = d.kmByMode[mode] ?: 0.0
                if (km <= 0 && mode == TravelMode.UNKNOWN) continue
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mode.label, Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (max > 0 && km > 0) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((km / max).toFloat().coerceIn(0.02f, 1f)).background(Color(mode.color)))
                        }
                    }
                    Text(Insights.km(km), Modifier.width(72.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StepsCard(d: ReviewData, resumeTick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val availability = remember { HealthSteps.availability(context) }
    var tick by remember { mutableIntStateOf(0) }
    val granted by produceState(false, resumeTick, tick) {
        value = runCatching { HealthSteps.hasPermission(context) }.getOrDefault(false)
    }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        tick++
        onChanged()
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Steps", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            when {
                availability == HealthSteps.Availability.UNSUPPORTED ->
                    Text("Health Connect isn't available on this phone.", style = MaterialTheme.typography.bodySmall)
                availability == HealthSteps.Availability.NEEDS_INSTALL -> {
                    Text("Install or update Health Connect to see your steps here.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                        }
                    }) { Text("Get Health Connect") }
                }
                !granted -> {
                    Text("Connect Health Connect to see your daily steps next to your travels.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { launcher.launch(HealthSteps.permissions) }) { Text("Connect") }
                }
                d.steps == null || d.steps.total == 0L ->
                    Text("No steps recorded in this period.", style = MaterialTheme.typography.bodySmall)
                else -> {
                    val s = d.steps!!
                    Text("${formatCount(s.total)} steps · ${formatCount(s.perDay)} a day on average", style = MaterialTheme.typography.bodyLarge)
                    s.bestDay?.let {
                        Text("Best day: ${it.format(Insights.DAY)} with ${formatCount(s.bestDaySteps)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (d.period == ReviewPeriod.ALL) {
                        Text("Steps cover the last 12 months.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CitiesCard(d: ReviewData) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Cities visited", style = MaterialTheme.typography.titleMedium)
            if (d.countries.isNotEmpty()) {
                Text(
                    "${d.countries.size} ${if (d.countries.size == 1) "country" else "countries"}: ${d.countries.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            val total = d.cities.sumOf { it.points }.coerceAtLeast(1)
            d.cities.take(12).forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.city, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${c.points * 100 / total}% of time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(d: ReviewData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by Prefs.aiEngine.collectAsStateWithLifecycle()
    var question by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<InsightResult?>(null) }
    var showChoice by remember { mutableStateOf(false) }

    fun run(q: String?) {
        loading = true
        scope.launch {
            result = withContext(Dispatchers.Default) { Insights.generate(context, d, q) }
            loading = false
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Insights", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(onClick = { showChoice = true }, label = { Text(engineLabel(engine)) })
            }
            Text(
                "Generated only when you ask, from this period's numbers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { run(null) }, enabled = !loading) { Text("Get insights") }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask, e.g. \"Which city did I spend most time in?\"") },
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { run(question) }, enabled = !loading && question.isNotBlank()) { Text("Ask") }
            }
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            result?.let { r ->
                r.note?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                }
                Text(r.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("By ${engineLabel(r.engine)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showChoice) AiChoiceDialog(onDismiss = { showChoice = false })
}

@Composable
private fun QuestionCard(trip: Trip, onAnswer: suspend (TravelMode) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val path by produceState<List<LatLng>>(emptyList(), trip.id) {
        value = withContext(Dispatchers.IO) {
            val p = PointStore.get(context).tripPath(trip.id)
            List(p.size / 2) { LatLng(p[it * 2], p[it * 2 + 1]) }
        }
    }
    val start = Date(trip.startTime)
    val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(start)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("$day · ${time.format(start)}–${time.format(Date(trip.endTime))}", style = MaterialTheme.typography.titleSmall)
            Text(
                "${Insights.km(trip.distanceM / 1000)} · average ${trip.avgSpeedKmh.toInt()} km/h · best guess: ${trip.inferredMode.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (path.size >= 2) {
                Spacer(Modifier.height(8.dp))
                TripPreview(path)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (mode in TravelMode.choosable) {
                    AssistChip(
                        onClick = { scope.launch { withContext(Dispatchers.IO) { onAnswer(mode) } } },
                        label = { Text(mode.label) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(mode.color), CircleShape)) },
                        colors = if (mode == trip.inferredMode) {
                            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                    )
                }
            }
        }
    }
}

private val optionColors = listOf(Color(0xFFFF7A1A), Color(0xFF3D9BFF), Color(0xFF66BB6A), Color(0xFFAB47BC))
private val recordedColor = Color(0xFF9E9E9E)

@Composable
private fun RoadQuestionCard(question: RoadQuestion, trip: Trip?, onAnswer: suspend (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = remember(question.id) {
        var road = 0
        question.options.map { if (it.recorded) recordedColor else optionColors[road++ % optionColors.size] }
    }
    val lines = remember(question.id) {
        question.options.mapIndexed { i, o ->
            val pts = Polyline.decode(o.poly)
            PreviewLine(List(pts.size / 2) { LatLng(pts[it * 2], pts[it * 2 + 1]) }, colors[i], if (o.recorded) 2.5f else 4.5f)
        }.sortedByDescending { it.widthDp } // Recorded line on top, thin.
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            trip?.let {
                val start = Date(it.startTime)
                Text(
                    "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(start)} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(start)} · ${it.mode.label}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            TripPreviewLinesMap(lines, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                question.options.forEachIndexed { i, option ->
                    AssistChip(
                        onClick = { scope.launch { withContext(Dispatchers.IO) { onAnswer(i) } } },
                        label = { Text(option.label) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(colors[i], CircleShape)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TripPreview(path: List<LatLng>) {
    TripPreviewMap(path, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)))
}
