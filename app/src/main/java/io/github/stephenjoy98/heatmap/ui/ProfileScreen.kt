package io.github.stephenjoy98.heatmap.ui

import io.github.stephenjoy98.heatmap.R

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import io.github.stephenjoy98.heatmap.data.Units
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.cloud.Account
import io.github.stephenjoy98.heatmap.cloud.FirebaseGate
import io.github.stephenjoy98.heatmap.cloud.SyncWorker
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.profile.ProfileRepository
import io.github.stephenjoy98.heatmap.profile.ProfileStats
import io.github.stephenjoy98.heatmap.profile.Stamp
import io.github.stephenjoy98.heatmap.profile.StampIcon
import io.github.stephenjoy98.heatmap.review.Insights
import io.github.stephenjoy98.heatmap.review.Places
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Spaced-out capitals used for card labels. */
private val capsLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)

/** Your travel passport: who you are, how much you've captured and where it's taken you. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(resumeTick: Int, onBack: () -> Unit, onExplore: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val user by Account.user.collectAsStateWithLifecycle()
    val code by Account.friendCode.collectAsStateWithLifecycle()
    val changes by store.changes.collectAsStateWithLifecycle()
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAreas by remember { mutableStateOf(false) }
    val stats by produceState<ProfileStats?>(null, year, changes, resumeTick, user?.uid) {
        val created = user?.metadata?.creationTimestamp
        value = withContext(Dispatchers.IO) { ProfileRepository.load(context, year, created) }
    }

    // The large title collapses into the top bar as the page scrolls.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    BackButton(onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(inner)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(52)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    user?.displayName ?: "Your profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                val joined = stats?.joined?.let { "Joined " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMMM yyyy")) }
                val id = code?.let { "ID ${Account.formatCode(it)}" }
                listOfNotNull(joined, id).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val s = stats
        if (s == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            HomeBaseCard(s)
            YearChips(s.years, year) { year = it }
            HeroCard(s, onAreas = { showAreas = true })
            TimeCapturedCard(s)
            Card {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("BY TRAVEL MODE", style = capsLabel)
                    ModeBreakdownRows(s.kmByMode)
                }
            }
            MostVisitedTile(s, Modifier)
            PlacesCard(s, onExplore)
            CarbonCard(s)
            // Stamps are hidden for now; more will be added to this section later.
            if (showAreas) {
                ListDialog(
                    title = "${formatCount(s.areas)} areas",
                    explanation = "$AREA_EXPLANATION These are all the squares you've been to" +
                        (if (s.year != null) " in ${s.year}" else "") + ", by city:",
                    rows = s.areasByCity.map { (city, n) -> city to "$n ${if (n == 1) "area" else "areas"}" },
                    empty = "No areas yet.",
                    onDismiss = { showAreas = false },
                )
            }
        }
    }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerHigh, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(32.dp), color = color, modifier = modifier.fillMaxWidth()) { content() }
}

@Composable
private fun HomeBaseCard(s: ProfileStats) {
    Card {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("HOME BASE", style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.homeCity ?: "Not yet known",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text("AUTO", style = capsLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                Text(
                    if (s.homeCity != null) "Where you spend the most days${s.homeCountry?.let { ", $it" } ?: ""}"
                    else "Appears once a few places have been recognised",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun YearChips(years: List<Int>, selected: Int?, onSelect: (Int?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.surface,
        )
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All time") }, colors = colors, shape = RoundedCornerShape(14.dp))
        years.forEach { y ->
            FilterChip(selected = selected == y, onClick = { onSelect(y) }, label = { Text(y.toString()) }, colors = colors, shape = RoundedCornerShape(14.dp))
        }
    }
}

private fun compact(n: Double): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000)
    n >= 10_000 -> String.format(Locale.US, "%.1fk", n / 1_000)
    else -> "%,d".format(n.roundToInt())
}

@Composable
private fun HeroCard(s: ProfileStats, onAreas: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Card {
        Box {
            // Concentric rings, like ripples from a dropped pin.
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width * 0.82f, size.height * 0.3f)
                for (i in 1..4) drawCircle(ring, radius = 32.dp.toPx() * i, center = center, style = Stroke(1.dp.toPx()))
            }
            Column(Modifier.padding(24.dp)) {
                Text(if (s.year == null) "DOTS CAPTURED" else "DOTS IN ${s.year}", style = capsLabel)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(compact(s.dots.toDouble()), fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 64.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("dots", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                StatRow(
                    listOf(
                        compact(Units.fromKm(s.totalKm)) to "${Units.longUnit.uppercase()} TRAVELLED",
                        compact(s.trips.toDouble()) to "TRIPS",
                        compact(s.areas.toDouble()) to "AREAS ›",
                    ),
                    onClick = mapOf(2 to onAreas),
                )
            }
        }
    }
}

@Composable
private fun StatRow(items: List<Pair<String, String>>, onClick: Map<Int, () -> Unit> = emptyMap()) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        items.forEachIndexed { i, (value, label) ->
            if (i > 0) VerticalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val tap = onClick[i]
            Column(Modifier.weight(1f).then(if (tap != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = tap) else Modifier)) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(label, style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Splits a duration into years (365 days), months (30 days), days, hours and minutes. */
private fun breakdown(ms: Long): LongArray {
    var minutes = ms / 60_000
    val years = minutes / (365L * 24 * 60); minutes -= years * 365L * 24 * 60
    val months = minutes / (30L * 24 * 60); minutes -= months * 30L * 24 * 60
    val days = minutes / (24 * 60); minutes -= days * 24 * 60
    val hours = minutes / 60; minutes -= hours * 60
    return longArrayOf(years, months, days, hours, minutes)
}

/** "1 day 15 hours": the two largest non-zero units. */
private fun formatCaptured(ms: Long): String {
    val parts = breakdown(ms)
    val names = listOf("year" to "years", "month" to "months", "day" to "days", "hour" to "hours", "minute" to "minutes")
    val shown = parts.indices.filter { parts[it] > 0 }.take(2)
    if (shown.isEmpty()) return "0 minutes"
    return shown.joinToString(" ") { i -> "${parts[i]} ${if (parts[i] == 1L) names[i].first else names[i].second}" }
}

@Composable
private fun TimeCapturedCard(s: ProfileStats) {
    val parts = breakdown(s.capturedMs)
    Card {
        Column(Modifier.padding(24.dp)) {
            Text("TIME CAPTURED", style = capsLabel)
            Spacer(Modifier.height(6.dp))
            Text(formatCaptured(s.capturedMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                listOf(parts[0] to "YEARS", parts[1] to "MONTHS", parts[2] to "DAYS", parts[3] to "HOURS").forEachIndexed { i, (v, label) ->
                    if (i > 0) VerticalDivider(Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(label, style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "The time your recording covers: each dot counts for its interval, so pauses aren't included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MostVisitedTile(s: ProfileStats, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(20.dp)) {
            Text("MOST VISITED", style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.topCity ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(12.dp))
            if (s.topCityDays > 0) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.onSurface) {
                    Text(
                        "${s.topCityDays} ${if (s.topCityDays == 1) "day" else "days"}",
                        color = MaterialTheme.colorScheme.surface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private enum class PlaceKind(val title: String) { COUNTRIES("Countries"), CITIES("Cities"), CONTINENTS("Continents") }

private fun continentEmoji(continent: String) = when (continent) {
    "North America", "South America" -> "🌎"
    "Asia", "Oceania" -> "🌏"
    "Antarctica" -> "🐧"
    else -> "🌍"
}


@Composable
private fun PlacesCard(s: ProfileStats, onExplore: () -> Unit) {
    // Same surfaces as the other cards, so it follows the light, dark and dynamic themes.
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = MaterialTheme.colorScheme.onSurface
    var showing by remember { mutableStateOf<PlaceKind?>(null) }
    // Where each tile is, so its list can grow out of it.
    val tileBounds = remember { mutableStateMapOf<PlaceKind, Rect>() }
    Card(color = bg) {
        Column(Modifier.padding(20.dp)) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                Text(
                    "LOCATIONS EXPLORED",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = capsLabel,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(PlaceKind.COUNTRIES, s.countries.size, R.drawable.ic_flag),
                    Triple(PlaceKind.CITIES, s.cities.size, R.drawable.ic_location_city),
                    Triple(PlaceKind.CONTINENTS, s.continents.size, R.drawable.ic_public),
                ).forEach { (kind, count, icon) ->
                    Surface(
                        onClick = { showing = kind },
                        enabled = count > 0,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f).trackBounds { tileBounds[kind] = it },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            MaterialIcon(icon, 26, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text(count.toString(), color = fg, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(kind.title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = capsLabel.copy(fontSize = 10.sp, letterSpacing = 1.sp), maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialIcon(R.drawable.ic_explore, 26, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${compact(Units.fromKm(s.exploredKm))} ${Units.longUnit}",
                            color = fg,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${Units.longUnit.uppercase()} EXPLORED", color = MaterialTheme.colorScheme.onSurfaceVariant, style = capsLabel.copy(fontSize = 10.sp, letterSpacing = 1.sp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${Units.longUnit.replaceFirstChar { it.uppercase() }} explored counts new ground only, so repeat trips don't add to it; " +
                    "${Units.longUnit} travelled above includes them. " +
                    "Tap a number to see the list.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = onExplore,
                ) {
                    Text("Explore")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }

    showing?.let { kind -> PlacesDialog(kind, s, tileBounds[kind]) { showing = null } }
}

/** A popup listing every country, city or continent visited in the selected period. */
@Composable
private fun PlacesDialog(kind: PlaceKind, s: ProfileStats, source: Rect?, onDismiss: () -> Unit) {
    // Each row leads with its flag or globe in JoyPixels artwork: the one place the app still uses emoji.
    val rows: List<Triple<String, String, String?>> = when (kind) {
        PlaceKind.COUNTRIES -> s.countries.map { Triple(JoyPixels.flag(it.code), it.name, Places.continentOf(it.code)) }
        PlaceKind.CITIES -> s.cities.map { c ->
            Triple(
                c.countryCode?.let(JoyPixels::flag) ?: "🏙️",
                c.name,
                listOfNotNull(c.country, "${c.days} ${if (c.days == 1) "day" else "days"}", Insights.km(c.km)).joinToString(" · "),
            )
        }
        PlaceKind.CONTINENTS -> s.continents.map { continent ->
            val n = s.countries.count { Places.continentOf(it.code) == continent }
            Triple(continentEmoji(continent), continent, "$n ${if (n == 1) "country" else "countries"}")
        }
    }
    // Grows out of the tile that was tapped, and shrinks back into it.
    GrowFromSource(source = source, onDismiss = onDismiss, modifier = Modifier.padding(horizontal = 24.dp)) { close ->
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp)) {
                Text("${kind.title} · ${rows.size}", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(rows) { (emoji, name, detail) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            JoyEmoji(emoji, 28)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                detail?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = close) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun CarbonCard(s: ProfileStats) {
    val co2 = s.co2Kg
    Card {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("CO₂", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("CARBON FOOTPRINT", style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (co2 >= 1000) String.format(Locale.US, "%.1f", co2 / 1000) else co2.roundToInt().toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (co2 >= 1000) " t CO₂" else " kg CO₂",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                val trees = (co2 / 21.0).roundToInt() // A tree absorbs roughly 21 kg of CO₂ a year.
                Text(
                    "${s.savedKg.roundToInt()} kg saved by walking & cycling · $trees ${if (trees == 1) "tree" else "trees"} to offset",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun StampIcon.drawable() = when (this) {
    StampIcon.DOTS -> R.drawable.ic_place
    StampIcon.DISTANCE -> R.drawable.ic_route
    StampIcon.WALK -> R.drawable.ic_directions_walk
    StampIcon.CYCLE -> R.drawable.ic_directions_bike
    StampIcon.CITY -> R.drawable.ic_location_city
    StampIcon.GLOBE -> R.drawable.ic_public
    StampIcon.CONTINENT -> R.drawable.ic_map
    StampIcon.AREA -> R.drawable.ic_explore
    StampIcon.CALENDAR -> R.drawable.ic_calendar_month
}

@Composable
private fun StampsSection(stamps: List<Stamp>) {
    Column(Modifier.padding(top = 8.dp)) {
        Text("STAMPS", style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        val earned = stamps.count { it.earned }
        Text(
            "$earned earned · ${stamps.size - earned} in progress",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            stamps.forEach { stamp ->
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (stamp.earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.width(150.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Box(
                            Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { MaterialIcon(stamp.icon.drawable(), 26) }
                        Spacer(Modifier.height(14.dp))
                        Text(stamp.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(stamp.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        Spacer(Modifier.height(10.dp))
                        Text(if (stamp.earned) "Earned" else "${(stamp.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { stamp.progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
