package io.github.stephenjoy98.heatmap.ui

import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.clickable
import io.github.stephenjoy98.heatmap.data.Units
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
import org.maplibre.android.geometry.LatLng
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Satellites
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    BackButton(onBack)
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

            // Trip questions ("How did you travel?") are answered at the top of the Timeline tab.

            item { StatGrid(d) }
            item { ModeBreakdown(d) }
            item { StepsCard(d, resumeTick) { refresh++ } }
            if (d.cities.isNotEmpty()) item { CitiesCard(d) }
            item { SatellitesCard(d) }
            item { InsightsCard(d) }
        }
    }
}

@Composable
private fun StatGrid(d: ReviewData) {
    var showAreas by remember { mutableStateOf(false) }
    var showCities by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Insights.km(d.totalKm), "travelled", Modifier.weight(1f))
            StatTile(formatCount(d.newAreas), "new areas explored ›", Modifier.weight(1f)) { showAreas = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(d.cities.size.toString(), (if (d.cities.size == 1) "city" else "cities") + " ›", Modifier.weight(1f)) { showCities = true }
            StatTile(d.steps?.let { formatCount(it.perDay) } ?: "–", "steps a day", Modifier.weight(1f))
        }
    }
    if (showAreas) {
        val pct = if (d.areasVisited > 0) d.newAreas * 100 / d.areasVisited else 0
        ListDialog(
            title = "New areas explored",
            explanation = "$AREA_EXPLANATION A new area is one you'd never been to before this period. " +
                "You were in ${formatCount(d.areasVisited)} areas this period, and ${formatCount(d.newAreas)} of them ($pct%) were new. " +
                "Where you found them:",
            rows = d.newAreasByCity.map { (city, n) -> city to "$n ${if (n == 1) "area" else "areas"}" },
            empty = "No new ground this period.",
            onDismiss = { showAreas = false },
        )
    }
    if (showCities) {
        val total = d.cities.sumOf { it.points }.coerceAtLeast(1)
        ListDialog(
            title = if (d.cities.size == 1) "1 city" else "${d.cities.size} cities",
            explanation = "Cities you were in this period, with the distance you travelled in each and the share of your time.",
            rows = d.cities.map { c -> c.city to "${Insights.km(c.km)} · ${c.points * 100 / total}%" },
            empty = "No cities found yet.",
            onDismiss = { showCities = false },
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    ElevatedCard(modifier.then(if (onClick != null) Modifier.clip(CardDefaults.elevatedShape).clickable(onClick = onClick) else Modifier)) {
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
            ModeBreakdownRows(d.kmByMode)
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

/** Colour for each satellite system's bar. */
private fun systemColor(constellation: Int): Color = when (constellation) {
    Satellites.GPS -> ChartColors.Blue
    Satellites.GALILEO -> ChartColors.Amber
    Satellites.GLONASS -> ChartColors.Red
    Satellites.BEIDOU -> ChartColors.Magenta
    Satellites.QZSS -> ChartColors.Green
    Satellites.NAVIC -> ChartColors.Orange
    else -> ChartColors.Grey
}

/**
 * Which satellites fixed your position in the period: a bar per system (GPS, Galileo, GLONASS, BeiDou…) with
 * how many of its satellites were used, and every satellite with its count under "See all satellites".
 */
@Composable
private fun SatellitesCard(d: ReviewData) {
    val s = d.satellites
    var showing by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Satellites", style = MaterialTheme.typography.titleMedium)
            if (s.satellites.isEmpty()) {
                Text(
                    "No satellite information for this period yet. Since version 2.31.0 the app notes which satellites " +
                        "each dot was fixed with; dots from Wi-Fi or mobile networks (at a saved place, say) use none.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val systems = s.bySystem
                Text(
                    "${s.satellites.size} satellites from ${systems.size} ${if (systems.size == 1) "system" else "systems"} " +
                        "fixed ${formatCount(s.fixes)} of your dots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val max = systems.maxOf { it.third }.coerceAtLeast(1)
                systems.forEach { (c, count, fixes) ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Satellites.system(c), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                            Box(
                                Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Box(
                                    Modifier.fillMaxHeight().fillMaxWidth((fixes.toFloat() / max).coerceIn(0.02f, 1f))
                                        .background(systemColor(c)),
                                )
                            }
                            Text(
                                "$count sats",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(72.dp).padding(start = 8.dp),
                            )
                        }
                        Text(
                            "${Satellites.operator(c)} · used ${formatCount(fixes)} times",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { showing = true }) { Text("See all satellites") }
            }
        }
    }
    if (showing) {
        ListDialog(
            title = "Satellites · ${s.satellites.size}",
            explanation = "Each satellite your phone used to fix a dot in this period, and how many dots it helped " +
                "with. A fix usually uses several satellites at once. G is GPS, E Galileo, R GLONASS, C BeiDou, " +
                "J QZSS, I NavIC and S the correction (SBAS) satellites.",
            rows = s.satellites.map { u ->
                "${Satellites.name(u.constellation, u.svid)} · ${Satellites.system(u.constellation)}" to "${formatCount(u.fixes)} times"
            },
            empty = "No satellites yet.",
            onDismiss = { showing = false },
        )
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
                    Text("${Insights.km(c.km)} · ${c.points * 100 / total}% of time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


