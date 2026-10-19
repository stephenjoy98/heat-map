package io.github.stephenjoy98.heatmap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import io.github.stephenjoy98.heatmap.cloud.Friend
import io.github.stephenjoy98.heatmap.cloud.Friends
import io.github.stephenjoy98.heatmap.data.DisplayMode
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.CellTiles
import io.github.stephenjoy98.heatmap.map.FriendTracks
import io.github.stephenjoy98.heatmap.map.HeatRamps
import io.github.stephenjoy98.heatmap.map.HeatTiles
import io.github.stephenjoy98.heatmap.map.heatTilePx
import io.github.stephenjoy98.heatmap.map.OwnTracks
import io.github.stephenjoy98.heatmap.review.Insights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Highlight(val label: String) {
    BOTH("Both routes"),
    SHARED("Shared areas"),
    NEW_TO_ME("New to me"),
    NEW_TO_THEM("New to them"),
}

private class Comparison(
    val mine: Set<Long>,
    val theirs: Set<Long>,
    val shared: Set<Long>,
    val onlyMine: Set<Long>,
    val onlyTheirs: Set<Long>,
    val myKm: Map<TravelMode, Double>,
    val routes: Int,
    val center: LatLng?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(friend: Friend, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val dark = isDarkTheme()
    val tilePx = remember { heatTilePx(context.resources.displayMetrics.density) }
    var reload by remember { mutableIntStateOf(0) }
    var highlight by remember { mutableStateOf(Highlight.BOTH) }
    var error by remember { mutableStateOf<String?>(null) }

    val comparison by produceState<Comparison?>(null, friend.uid, reload) {
        value = null
        error = null
        value = withContext(Dispatchers.IO) {
            val routes = runCatching { Friends.downloadRoutes(context, friend.uid) }
                .onFailure { error = "Couldn't download ${friend.name}'s routes: ${it.message}" }
                .getOrDefault(0)
            val mine = store.allCells()
            val theirs = Friends.friendCells(context, friend.uid)
            val shared = mine.intersect(theirs)
            val latest = store.latest()
            Comparison(
                mine = mine,
                theirs = theirs,
                shared = shared,
                onlyMine = mine - theirs,
                onlyTheirs = theirs - mine,
                myKm = store.distanceByMode(0, Long.MAX_VALUE).mapValues { it.value / 1000.0 },
                routes = routes,
                center = latest?.let { LatLng(it.lat, it.lon) },
            )
        }
    }

    val camera = rememberMapCamera()
    var centered by remember { mutableStateOf(false) }
    LaunchedEffect(comparison) {
        val center = comparison?.center
        if (!centered && center != null) {
            camera.moveTo(center.latitude, center.longitude, 12f)
            centered = true
        }
    }

    val all = TravelMode.entries.toSet()
    val sharedColor = if (dark) Color(0xCCFFF3B0) else Color(0xCC7B2CBF)
    val mineHeat = remember(dark) {
        HeatTiles(OwnTracks(store, all), if (dark) HeatRamps.darkHeat else HeatRamps.lightHeat, DisplayMode.ROUTES, tilePx)
    }
    val theirHeat = remember(dark, friend.uid, comparison) {
        HeatTiles(FriendTracks(store, friend.uid, all), if (dark) HeatRamps.darkFriend else HeatRamps.lightFriend, DisplayMode.ROUTES, tilePx)
    }
    val layers = remember(highlight, comparison, mineHeat, theirHeat) {
        val faded = if (highlight == Highlight.BOTH) 1f else 0.4f
        val c = comparison
        val cells = when {
            c == null -> null
            highlight == Highlight.SHARED -> CellTiles(c.shared, sharedColor.toArgb())
            highlight == Highlight.NEW_TO_ME -> CellTiles(c.onlyTheirs, FriendBlue.copy(alpha = 0.75f).toArgb())
            highlight == Highlight.NEW_TO_THEM -> CellTiles(c.onlyMine, HeatOrange.copy(alpha = 0.75f).toArgb())
            else -> null
        }
        listOfNotNull(
            MapLayer("mine", mineHeat, alpha = faded, zIndex = 0f),
            MapLayer("theirs", theirHeat, alpha = faded, zIndex = 1f),
            cells?.let { MapLayer("cells-${highlight.name}", it, zIndex = 2f) },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("You and ${friend.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                },
            )

            Box(Modifier.fillMaxWidth().weight(1f)) {
                AppMap(camera = camera, layers = layers, modifier = Modifier.fillMaxSize(), refreshKey = comparison)
                MapControls(camera, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
                if (comparison == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                Row(
                    Modifier.align(Alignment.TopCenter).padding(8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Highlight.entries.forEach { h ->
                        FilterChip(selected = highlight == h, onClick = { highlight = h }, label = { Text(h.label) })
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Legend(HeatOrange, "You")
                    Spacer(Modifier.width(12.dp))
                    Legend(FriendBlue, friend.name)
                    if (highlight == Highlight.SHARED) {
                        Spacer(Modifier.width(12.dp))
                        Legend(sharedColor, "Both")
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                val c = comparison
                if (c != null) {
                    if (c.routes == 0 && error == null) {
                        Text(
                            "${friend.name} hasn't shared any routes yet (or has route sharing turned off).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    CompareRow("", "You", friend.name, header = true)
                    CompareRow("Distance", Insights.km(c.myKm.values.sum()), Insights.km(friend.totalKm))
                    for (m in TravelMode.choosable) {
                        val a = c.myKm[m] ?: 0.0
                        val b = friend.kmByMode[m] ?: 0.0
                        if (a > 0 || b > 0) CompareRow(m.label, Insights.km(a), Insights.km(b))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    CompareRow("Areas explored", formatCount(c.mine.size.toLong()), formatCount(c.theirs.size.toLong()))
                    CompareRow("Only explored by", formatCount(c.onlyMine.size.toLong()), formatCount(c.onlyTheirs.size.toLong()))
                    CompareRow("Shared areas", formatCount(c.shared.size.toLong()), "")
                    val overlap = if (c.mine.isEmpty()) 0 else c.shared.size * 100 / c.mine.size
                    Text(
                        "$overlap% of the places you've been, ${friend.name} has been too. " +
                            "${formatCount(c.onlyTheirs.size.toLong())} of their areas would be new to you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (friend.cities.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("${friend.name}'s cities: ${friend.cities.take(10).joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun CompareRow(label: String, mine: String, theirs: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1.3f), style = style)
        Text(mine, Modifier.weight(1f), style = style, color = if (header) HeatOrange else MaterialTheme.colorScheme.onSurface)
        Text(theirs, Modifier.weight(1f), style = style, color = if (header) FriendBlue else MaterialTheme.colorScheme.onSurface)
    }
}
