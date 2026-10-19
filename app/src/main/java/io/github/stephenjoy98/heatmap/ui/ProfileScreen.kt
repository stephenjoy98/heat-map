package io.github.stephenjoy98.heatmap.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
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
@Composable
fun ProfileScreen(
    resumeTick: Int,
    onBack: () -> Unit,
    onFriends: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onExplore: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val user by Account.user.collectAsStateWithLifecycle()
    val code by Account.friendCode.collectAsStateWithLifecycle()
    val changes by store.changes.collectAsStateWithLifecycle()
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    val stats by produceState<ProfileStats?>(null, year, changes, resumeTick, user?.uid) {
        val created = user?.metadata?.creationTimestamp
        value = withContext(Dispatchers.IO) { ProfileRepository.load(context, year, created) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
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
            HeroCard(s)
            TimeCapturedCard(s)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MostVisitedTile(s, Modifier.weight(1.15f).fillMaxHeight())
                LongestTripTile(s, Modifier.weight(1f).fillMaxHeight())
            }
            PlacesCard(s, onExplore)
            CarbonCard(s)
            StampsSection(s.stamps)
        }

        AccountCard(onFriends, onBackup, onSettings)
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
private fun HeroCard(s: ProfileStats) {
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
                        compact(s.totalKm) to "KM TRAVELLED",
                        compact(s.trips.toDouble()) to "TRIPS",
                        compact(s.areas.toDouble()) to "AREAS",
                    )
                )
            }
        }
    }
}

@Composable
private fun StatRow(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        items.forEachIndexed { i, (value, label) ->
            if (i > 0) VerticalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(label, style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TimeCapturedCard(s: ProfileStats) {
    Card {
        Column(Modifier.padding(24.dp)) {
            Text("TIME CAPTURED", style = capsLabel)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                listOf(s.hours to "HOURS", s.days to "DAYS", s.months to "MONTHS", s.yearsCaptured to "YEARS").forEachIndexed { i, (v, label) ->
                    if (i > 0) VerticalDivider(Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(compact(v.toDouble()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(label, style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Hours, days, months and years with at least one dot.",
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
            Spacer(Modifier.weight(1f))
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

@Composable
private fun LongestTripTile(s: ProfileStats, modifier: Modifier) {
    val trip = s.longestTrip
    Card(modifier, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp)) {
            Text("LONGEST TRIP", style = capsLabel, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                trip?.let { Insights.km(it.distanceM / 1000) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                trip?.let { "${it.mode.label} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.startTime))}" } ?: "No trips yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PlacesCard(s: ProfileStats, onExplore: () -> Unit) {
    val bg = MaterialTheme.colorScheme.inverseSurface
    val fg = MaterialTheme.colorScheme.inverseOnSurface
    Card(color = bg) {
        Column(Modifier.padding(20.dp)) {
            Surface(shape = RoundedCornerShape(50), color = fg) {
                Text(
                    "${s.countries.size} ${if (s.countries.size == 1) "country" else "countries"}",
                    color = bg,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf(s.cities to "CITIES", s.states to "STATES", s.countries.size to "COUNTRIES", s.continents.size to "CONTINENTS").forEach { (v, label) ->
                    Column(Modifier.weight(1f)) {
                        Text(v.toString(), color = fg, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(label, color = fg.copy(alpha = 0.7f), style = capsLabel.copy(fontSize = 10.sp, letterSpacing = 1.sp), maxLines = 1)
                    }
                }
            }
            if (s.countries.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    (s.continents.joinToString(" · ") + "\n" + s.countries.take(12).joinToString(", ")).trim(),
                    color = fg.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = onExplore,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
                ) {
                    Text("Explore")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
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

private fun StampIcon.emoji() = when (this) {
    StampIcon.DOTS -> "📍"
    StampIcon.DISTANCE -> "🛣️"
    StampIcon.WALK -> "🚶"
    StampIcon.CYCLE -> "🚴"
    StampIcon.CITY -> "🏙️"
    StampIcon.GLOBE -> "🌍"
    StampIcon.CONTINENT -> "🗺️"
    StampIcon.AREA -> "🧭"
    StampIcon.CALENDAR -> "📅"
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
                        ) { Text(stamp.icon.emoji(), fontSize = 22.sp) }
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

@Composable
private fun AccountCard(onFriends: () -> Unit, onBackup: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsStateWithLifecycle()
    val configured = remember { FirebaseGate.isConfigured(context) }
    var signingIn by remember { mutableStateOf(false) }
    Text("ACCOUNT", style = capsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    Card {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (user == null && configured) {
                Button(
                    enabled = !signingIn,
                    onClick = {
                        val activity = context.findActivity() ?: return@Button
                        signingIn = true
                        scope.launch {
                            runCatching { Account.signIn(activity) }.onSuccess { SyncWorker.runNow(context) }
                            signingIn = false
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                ) { Text(if (signingIn) "Signing in…" else "Sign in with Google") }
            }
            MenuRow(Icons.Filled.Share, "Friends", "Add friends and compare routes and explored areas", onFriends)
            MenuRow(Icons.Filled.Refresh, "Backup", "Back up or restore your location history", onBackup)
            MenuRow(Icons.Filled.Settings, "Settings", null, onSettings)
            if (user != null) {
                MenuRow(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", null) { scope.launch { Account.signOut(context) } }
            }
        }
    }
}
