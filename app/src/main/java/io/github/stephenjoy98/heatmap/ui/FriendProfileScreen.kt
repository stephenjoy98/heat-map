package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.cloud.Friend
import io.github.stephenjoy98.heatmap.cloud.Friends
import io.github.stephenjoy98.heatmap.data.DisplayMode
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.FriendTracks
import io.github.stephenjoy98.heatmap.map.HeatRamps
import io.github.stephenjoy98.heatmap.map.HeatTiles
import io.github.stephenjoy98.heatmap.map.heatTilePx
import io.github.stephenjoy98.heatmap.review.Insights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A friend's profile: their totals, how they get around, the cities they've been to, and a heatmap of
 * everything they've shared, zoomed out to fit. The map can be panned, zoomed, rotated and tilted like the
 * main map. Their routes are refreshed when there's a connection; the last copy shows without one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfileScreen(friend: Friend, onBack: () -> Unit, onCompare: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val dark = isDarkTheme()
    val tilePx = remember { heatTilePx(context.resources.displayMetrics.density) }
    var offline by remember { mutableStateOf(false) }
    // Download their latest routes, then find the area they cover.
    val loaded by produceState<Pair<Int, GeoBox?>?>(null, friend.uid) {
        value = withContext(Dispatchers.IO) {
            val count = runCatching { Friends.downloadRoutes(context, friend.uid) }
                .onFailure { offline = true }
                .getOrDefault(-1)
            count to store.friendBounds(friend.uid)
        }
    }
    val camera = rememberMapCamera()
    LaunchedEffect(loaded) { loaded?.second?.let { frame(camera, it) } }
    val heat = remember(dark, friend.uid, loaded) {
        HeatTiles(
            FriendTracks(store, friend.uid, TravelMode.entries.toSet()),
            if (dark) HeatRamps.darkFriend else HeatRamps.lightFriend,
            DisplayMode.ROUTES,
            tilePx,
        )
    }
    val layers = remember(heat) { listOf(MapLayer("friend-${friend.uid}", heat)) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(friend.name) }, navigationIcon = { BackButton(onBack) })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(FriendBlue, CircleShape), contentAlignment = Alignment.Center) {
                        Text(friend.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${Insights.km(friend.totalKm)} travelled · ${formatCount(friend.cells)} areas explored",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Everywhere they've been, as a heatmap: drag, pinch, twist or tilt it.
                Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(20.dp))) {
                    AppMap(camera = camera, layers = layers, modifier = Modifier.fillMaxSize(), refreshKey = loaded)
                    MapControls(camera, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
                    ScaleBar(camera, Modifier.align(Alignment.BottomStart).padding(8.dp))
                    when {
                        loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        loaded?.second == null -> Text(
                            if (offline) "Couldn't download ${friend.name}'s routes. Check your connection." else "${friend.name} hasn't shared any routes yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        )
                    }
                }
                if (offline && loaded?.second != null) {
                    Text(
                        "Offline: showing the routes saved last time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(onClick = onCompare, modifier = Modifier.fillMaxWidth()) { Text("Compare with you") }

                if (friend.kmByMode.isNotEmpty()) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("How ${friend.name} gets around", style = MaterialTheme.typography.titleMedium)
                            val max = friend.kmByMode.values.maxOrNull()?.coerceAtLeast(0.01) ?: 1.0
                            friend.kmByMode.entries.sortedByDescending { it.value }.forEach { (mode, km) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(mode.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                                    Box(Modifier.weight(1f).height(12.dp)) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth((km / max).toFloat().coerceIn(0.02f, 1f))
                                                .background(Color(mode.color), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                                        )
                                    }
                                    Text(Insights.km(km), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }

                if (friend.cities.isNotEmpty()) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (friend.cities.size == 1) "1 city" else "${friend.cities.size} cities",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(friend.cities.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
