package io.github.stephenjoy98.heatmap.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberTileOverlayState
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.data.MapProvider
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.map.GoogleTileAdapter
import io.github.stephenjoy98.heatmap.map.LatestPointOverlay
import io.github.stephenjoy98.heatmap.map.MapStyles
import io.github.stephenjoy98.heatmap.map.OsmTileLayer
import io.github.stephenjoy98.heatmap.map.OsmTiles
import io.github.stephenjoy98.heatmap.map.TileBitmapSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline as OsmPolyline

/** A tile layer drawn over the base map, independent of which map engine is used. */
class MapLayer(val id: String, val source: TileBitmapSource, val alpha: Float = 1f, val zIndex: Float = 0f)

/** Moves the camera of an [AppMap], whichever engine it uses. */
class MapCamera(val initialLat: Double, val initialLon: Double, val initialZoom: Float) {
    internal class Command(val lat: Double, val lon: Double, val zoom: Float, val animate: Boolean)

    // Replays the latest command so a map created afterwards still starts in the right place.
    internal val commands = MutableSharedFlow<Command>(replay = 1, extraBufferCapacity = 4)

    fun moveTo(lat: Double, lon: Double, zoom: Float) {
        commands.tryEmit(Command(lat, lon, zoom, animate = false))
    }

    fun animateTo(lat: Double, lon: Double, zoom: Float) {
        commands.tryEmit(Command(lat, lon, zoom, animate = true))
    }
}

@Composable
fun rememberMapCamera(lat: Double = 20.0, lon: Double = 0.0, zoom: Float = 2f) = remember { MapCamera(lat, lon, zoom) }

/** Google Maps when this build has an API key (and the user hasn't picked OpenStreetMap), otherwise OpenStreetMap. */
@Composable
fun useGoogleMaps(): Boolean {
    val provider by Prefs.mapProvider.collectAsStateWithLifecycle()
    return BuildConfig.HAS_MAPS_KEY && provider != MapProvider.OSM
}

/**
 * The app's map: a dark or light base map with heat (and other) tile layers on top.
 * [refreshKey] changes when the layers' data changes; tiles are re-rendered shortly after.
 */
@Composable
fun AppMap(
    camera: MapCamera,
    layers: List<MapLayer>,
    modifier: Modifier = Modifier,
    refreshKey: Any? = null,
    latest: LatLng? = null,
    showMyLocation: Boolean = false,
) {
    val dark = isDarkTheme()
    if (useGoogleMaps()) {
        GoogleAppMap(camera, layers, dark, refreshKey, showMyLocation, modifier)
    } else {
        OsmAppMap(camera, layers, dark, refreshKey, latest, modifier)
    }
}

@SuppressLint("MissingPermission") // showMyLocation is only true with location permission.
@Composable
private fun GoogleAppMap(
    camera: MapCamera,
    layers: List<MapLayer>,
    dark: Boolean,
    refreshKey: Any?,
    showMyLocation: Boolean,
    modifier: Modifier,
) {
    val state = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(camera.initialLat, camera.initialLon), camera.initialZoom)
    }
    LaunchedEffect(camera) {
        camera.commands.collect { c ->
            if (c.animate) {
                runCatching { state.animate(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), c.zoom), 600) }
            } else {
                state.position = CameraPosition.fromLatLngZoom(LatLng(c.lat, c.lon), c.zoom)
            }
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = state,
        properties = MapProperties(
            mapStyleOptions = MapStyleOptions(if (dark) MapStyles.dark else MapStyles.light),
            isMyLocationEnabled = showMyLocation,
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
        ),
    ) {
        for (layer in layers) {
            // Recreate the overlay when its source changes (filter, display mode, theme).
            key(layer.id, layer.source) {
                val tileState = rememberTileOverlayState()
                val provider = remember { GoogleTileAdapter(layer.source) }
                LaunchedEffect(refreshKey) {
                    delay(1500)
                    tileState.clearTileCache()
                }
                TileOverlay(
                    tileProvider = provider,
                    state = tileState,
                    fadeIn = false,
                    transparency = 1f - layer.alpha,
                    zIndex = layer.zIndex,
                )
            }
        }
    }
}

@Composable
private fun OsmAppMap(
    camera: MapCamera,
    layers: List<MapLayer>,
    dark: Boolean,
    refreshKey: Any?,
    latest: LatLng?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setTilesScaledToDpi(true)
            setHorizontalMapRepetitionEnabled(false)
            setVerticalMapRepetitionEnabled(false)
            val maxLat = MapView.getTileSystem().maxLatitude
            setScrollableAreaLimitLatitude(maxLat, -maxLat, 0)
            setMinZoomLevel(2.0)
            setMaxZoomLevel(20.0)
            controller.setZoom(camera.initialZoom.toDouble())
            controller.setCenter(GeoPoint(camera.initialLat, camera.initialLon))
        }
    }
    val tileLayers = remember { LinkedHashMap<String, OsmTileLayer>() }
    val dot = remember { LatestPointOverlay(density) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(dark) {
        mapView.setTileSource(if (dark) OsmTiles.dark else OsmTiles.light)
        val background = if (dark) Color(0xFF1B1B1B) else Color(0xFFF5F5F5)
        mapView.overlayManager.tilesOverlay.setLoadingBackgroundColor(background.toArgb())
        mapView.overlayManager.tilesOverlay.setLoadingLineColor(background.toArgb())
    }

    // Keep osmdroid's overlays in step with [layers], ordered by z-index, with the location dot on top.
    SideEffect {
        val wanted = layers.associateBy { it.id }
        tileLayers.keys.filter { it !in wanted }.forEach { id ->
            tileLayers.remove(id)?.let { mapView.overlays.remove(it); it.onDetach(mapView) }
        }
        for (layer in layers) {
            val existing = tileLayers[layer.id]
            if (existing == null) {
                tileLayers[layer.id] = OsmTileLayer(layer.source, layer.alpha)
            } else {
                if (existing.source !== layer.source) existing.source = layer.source
                existing.setAlpha(layer.alpha)
            }
        }
        mapView.overlays.removeAll { it is OsmTileLayer || it is LatestPointOverlay }
        layers.sortedBy { it.zIndex }.forEach { mapView.overlays.add(tileLayers.getValue(it.id)) }
        dot.point = latest?.let { GeoPoint(it.latitude, it.longitude) }
        mapView.overlays.add(dot)
        mapView.invalidate()
    }

    LaunchedEffect(refreshKey) {
        delay(1500)
        tileLayers.values.forEach { it.refresh() }
        mapView.invalidate()
    }

    LaunchedEffect(camera) {
        camera.commands.collect { c ->
            if (c.animate) {
                mapView.controller.animateTo(GeoPoint(c.lat, c.lon), c.zoom.toDouble(), 600L)
            } else {
                mapView.controller.setZoom(c.zoom.toDouble())
                mapView.controller.setCenter(GeoPoint(c.lat, c.lon))
            }
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Text(
            OsmTiles.ATTRIBUTION,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        )
    }
}

/** A small, non-interactive map showing one trip. */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun TripPreviewMap(path: List<LatLng>, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    val lineColor = MaterialTheme.colorScheme.primary
    if (useGoogleMaps()) {
        val camera = rememberCameraPositionState()
        val bounds = remember(path) { LatLngBounds.builder().apply { path.forEach { include(it) } }.build() }
        GoogleMap(
            modifier = modifier,
            cameraPositionState = camera,
            googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
            properties = MapProperties(mapStyleOptions = MapStyleOptions(if (dark) MapStyles.dark else MapStyles.light)),
            uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
            onMapLoaded = { runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, 48)) } },
        ) {
            Polyline(points = path, color = lineColor, width = 10f)
        }
    } else {
        val context = LocalContext.current
        val points = remember(path) { path.map { GeoPoint(it.latitude, it.longitude) } }
        AndroidView(
            modifier = modifier,
            factory = {
                MapView(context).apply {
                    setTileSource(if (dark) OsmTiles.dark else OsmTiles.light)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    setTilesScaledToDpi(true)
                    setOnTouchListener { _, _ -> true } // A preview; the trip is explored on the main map.
                    overlays.add(OsmPolyline(this).apply {
                        setPoints(points)
                        outlinePaint.color = lineColor.toArgb()
                        outlinePaint.strokeWidth = 4 * context.resources.displayMetrics.density
                    })
                    addOnFirstLayoutListener { _, _, _, _, _ ->
                        if (points.size >= 2) zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 48)
                    }
                }
            },
            onRelease = { it.onDetach() },
        )
    }
}
