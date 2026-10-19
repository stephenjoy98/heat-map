package io.github.stephenjoy98.heatmap.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberTileOverlayState
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.data.MapLayerType
import io.github.stephenjoy98.heatmap.data.MapProvider
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.map.GoogleTileAdapter
import io.github.stephenjoy98.heatmap.map.MapStyles
import io.github.stephenjoy98.heatmap.map.TileBitmapSource
import kotlinx.coroutines.delay
import io.github.stephenjoy98.heatmap.map.TileOverlayView
import io.github.stephenjoy98.heatmap.map.VectorMaps
import kotlinx.coroutines.flow.MutableSharedFlow
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MLatLng

/** A tile layer drawn over the base map, independent of which map engine is used. */
class MapLayer(val id: String, val source: TileBitmapSource, val alpha: Float = 1f, val zIndex: Float = 0f)

/** Moves the camera of an [AppMap], whichever engine it uses. */
class MapCamera(val initialLat: Double, val initialLon: Double, val initialZoom: Float) {
    internal class Command(val lat: Double, val lon: Double, val zoom: Float, val animate: Boolean)

    internal enum class Action { ZOOM_IN, ZOOM_OUT, NORTH_UP }

    // Replays the latest move so a map created afterwards still starts in the right place.
    internal val commands = MutableSharedFlow<Command>(replay = 1, extraBufferCapacity = 4)

    // One-off button actions; never replayed.
    internal val actions = MutableSharedFlow<Action>(extraBufferCapacity = 8)

    /** The map's current rotation in degrees clockwise from north, kept up to date by the engine. */
    var bearing by mutableFloatStateOf(0f)
        internal set

    /** Changes whenever the camera moves, so overlays that use [toScreen] know to redraw. */
    var cameraTick by mutableIntStateOf(0)
        internal set

    internal var projector: ((Double, Double) -> Offset?)? = null

    /** Where a coordinate is on the map, in pixels from its top-left corner; null until the map is ready. */
    fun toScreen(lat: Double, lon: Double): Offset? = projector?.invoke(lat, lon)

    fun zoomIn() {
        actions.tryEmit(Action.ZOOM_IN)
    }

    fun zoomOut() {
        actions.tryEmit(Action.ZOOM_OUT)
    }

    /** Turns the map back so north is up. */
    fun northUp() {
        actions.tryEmit(Action.NORTH_UP)
    }

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
    onMapClick: ((lat: Double, lon: Double) -> Unit)? = null,
) {
    val dark = isDarkTheme()
    val mapLayer by Prefs.mapLayer.collectAsStateWithLifecycle()
    val buildings by Prefs.buildings3d.collectAsStateWithLifecycle()
    val style = MapLook(dark, mapLayer == MapLayerType.TERRAIN, buildings)
    if (useGoogleMaps()) {
        GoogleAppMap(camera, layers, style, refreshKey, showMyLocation, onMapClick, modifier)
    } else {
        VectorAppMap(camera, layers, style, refreshKey, latest, onMapClick, modifier)
    }
}

/** How the base map looks: theme, terrain shading and 3D buildings (which also tilt the camera). */
private data class MapLook(val dark: Boolean, val terrain: Boolean = false, val buildings: Boolean = false)

private const val TILT_3D = 45f

@SuppressLint("MissingPermission") // showMyLocation is only true with location permission.
@Composable
private fun GoogleAppMap(
    camera: MapCamera,
    layers: List<MapLayer>,
    look: MapLook,
    refreshKey: Any?,
    showMyLocation: Boolean,
    onMapClick: ((Double, Double) -> Unit)?,
    modifier: Modifier,
) {
    val dark = look.dark
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
    LaunchedEffect(camera) {
        camera.actions.collect { a ->
            val update = when (a) {
                MapCamera.Action.ZOOM_IN -> CameraUpdateFactory.zoomIn()
                MapCamera.Action.ZOOM_OUT -> CameraUpdateFactory.zoomOut()
                MapCamera.Action.NORTH_UP ->
                    CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(state.position).bearing(0f).tilt(0f).build())
            }
            runCatching { state.animate(update, 300) }
        }
    }
    LaunchedEffect(camera, state) {
        snapshotFlow { state.position }.collect {
            camera.bearing = it.bearing
            camera.cameraTick++
        }
    }
    DisposableEffect(camera, state) {
        camera.projector = { lat, lon ->
            state.projection?.toScreenLocation(LatLng(lat, lon))?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
        }
        onDispose { camera.projector = null }
    }
    // Tilt the camera so 3D buildings stand out, and flatten it again when they're turned off.
    LaunchedEffect(look.buildings) {
        val tilt = if (look.buildings) TILT_3D else 0f
        if (state.position.tilt != tilt) {
            runCatching {
                state.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(state.position).tilt(tilt).build()), 600)
            }
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = state,
        properties = MapProperties(
            mapStyleOptions = MapStyleOptions(if (dark) MapStyles.dark else MapStyles.light),
            mapType = if (look.terrain) MapType.TERRAIN else MapType.NORMAL,
            isBuildingEnabled = look.buildings,
            isMyLocationEnabled = showMyLocation,
        ),
        onMapClick = { onMapClick?.invoke(it.latitude, it.longitude) },
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

/** Owns a MapLibre MapView, follows the Compose lifecycle, and hands out the map once it's ready. */
private class VectorMapHolder(context: Context) {
    val mapView = MapView(context).apply { onCreate(null) }
    var map: MapLibreMap? = null
    var styleLook: Any? = null
    private val readyCallbacks = mutableListOf<(MapLibreMap) -> Unit>()

    init {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = true
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
            // Keep the OpenStreetMap credit visible above the recording panel and the accuracy button.
            val dp = context.resources.displayMetrics.density
            m.uiSettings.setAttributionMargins((12 * dp).toInt(), 0, 0, (160 * dp).toInt())
            readyCallbacks.forEach { it(m) }
            readyCallbacks.clear()
        }
    }

    fun withMap(block: (MapLibreMap) -> Unit) {
        map?.let(block) ?: readyCallbacks.add(block)
    }

    /**
     * Loads the vector style for [look] (recoloured for dark, with terrain shading and 3D buildings if
     * asked for), then calls [onLoaded]. Does nothing if that look is already showing.
     */
    fun setLook(look: MapLook, onLoaded: (Style) -> Unit = {}) = withMap { m ->
        if (styleLook == look) return@withMap
        styleLook = look
        m.setStyle(Style.Builder().fromUri(VectorMaps.STYLE_URL)) { style ->
            if (look.dark) VectorMaps.applyDark(style)
            if (look.terrain) VectorMaps.addTerrain(style, look.dark)
            if (look.buildings) VectorMaps.addBuildings(style, look.dark)
            onLoaded(style)
        }
    }
}

@Composable
private fun rememberVectorMap(): VectorMapHolder {
    val context = LocalContext.current
    val holder = remember { VectorMapHolder(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val mapView = holder.mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // Adding the observer replays ON_START/ON_RESUME if the screen is already showing.
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return holder
}

// Google Maps zoom levels count 256 px tiles; MapLibre counts 512 px tiles, so it's one level lower.
private fun Float.toVectorZoom(): Double = (this - 1f).toDouble().coerceAtLeast(0.0)

@Composable
private fun VectorAppMap(
    camera: MapCamera,
    layers: List<MapLayer>,
    look: MapLook,
    refreshKey: Any?,
    latest: LatLng?,
    onMapClick: ((Double, Double) -> Unit)?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val holder = rememberVectorMap()
    val overlay = remember { TileOverlayView(context) }
    val clickHandler by rememberUpdatedState(onMapClick)

    DisposableEffect(Unit) {
        holder.withMap { m ->
            m.moveCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    MLatLng(camera.initialLat, camera.initialLon), camera.initialZoom.toVectorZoom(),
                )
            )
            overlay.attach(m)
            m.addOnCameraMoveListener {
                camera.bearing = m.cameraPosition.bearing.toFloat()
                camera.cameraTick++
            }
            m.addOnCameraIdleListener {
                camera.bearing = m.cameraPosition.bearing.toFloat()
                camera.cameraTick++
            }
            // Returning false lets the map handle the tap as usual.
            m.addOnMapClickListener { p ->
                clickHandler?.invoke(p.latitude, p.longitude)
                false
            }
            camera.projector = { lat, lon ->
                m.projection.toScreenLocation(MLatLng(lat, lon)).let { Offset(it.x, it.y) }
            }
        }
        onDispose {
            camera.projector = null
            overlay.release()
        }
    }

    LaunchedEffect(look) { holder.setLook(look) }

    LaunchedEffect(look.buildings) {
        holder.withMap { m ->
            m.uiSettings.isTiltGesturesEnabled = look.buildings
            val tilt = if (look.buildings) TILT_3D.toDouble() else 0.0
            if (m.cameraPosition.tilt != tilt) m.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.tiltTo(tilt), 600)
        }
    }

    SideEffect {
        overlay.setLayers(layers.map { TileOverlayView.LayerSpec(it.id, it.source, it.alpha, it.zIndex) })
        overlay.latest = latest?.let { MLatLng(it.latitude, it.longitude) }
    }

    LaunchedEffect(refreshKey) {
        delay(1500)
        overlay.refresh()
    }

    LaunchedEffect(camera) {
        camera.commands.collect { c ->
            holder.withMap { m ->
                val update = org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(MLatLng(c.lat, c.lon), c.zoom.toVectorZoom())
                if (c.animate) m.animateCamera(update, 600) else m.moveCamera(update)
            }
        }
    }

    LaunchedEffect(camera) {
        camera.actions.collect { a ->
            holder.withMap { m ->
                val update = when (a) {
                    MapCamera.Action.ZOOM_IN -> org.maplibre.android.camera.CameraUpdateFactory.zoomBy(1.0)
                    MapCamera.Action.ZOOM_OUT -> org.maplibre.android.camera.CameraUpdateFactory.zoomBy(-1.0)
                    MapCamera.Action.NORTH_UP -> org.maplibre.android.camera.CameraUpdateFactory.bearingTo(0.0)
                }
                m.animateCamera(update, 300)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            FrameLayout(it).apply {
                (holder.mapView.parent as? ViewGroup)?.removeView(holder.mapView)
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                addView(holder.mapView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        },
    )
}

/** One line on a [TripPreviewMap]. */
class PreviewLine(val points: List<LatLng>, val color: Color, val widthDp: Float = 4f)

/** A small, non-interactive map showing one trip. */
@Composable
fun TripPreviewMap(path: List<LatLng>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    TripPreviewLinesMap(remember(path, lineColor) { listOf(PreviewLine(path, lineColor)) }, modifier)
}

/** A small, non-interactive map showing one or more lines (e.g. the candidate roads for a stretch). */
@Composable
fun TripPreviewLinesMap(lines: List<PreviewLine>, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    val all = remember(lines) { lines.flatMap { it.points } }
    if (useGoogleMaps()) {
        val camera = rememberCameraPositionState()
        val density = LocalContext.current.resources.displayMetrics.density
        GoogleMap(
            modifier = modifier,
            cameraPositionState = camera,
            googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
            properties = MapProperties(mapStyleOptions = MapStyleOptions(if (dark) MapStyles.dark else MapStyles.light)),
            uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
            onMapLoaded = {
                if (all.size >= 2) {
                    val bounds = LatLngBounds.builder().apply { all.forEach { include(it) } }.build()
                    runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, 48)) }
                }
            },
        ) {
            for (line in lines) Polyline(points = line.points, color = line.color, width = line.widthDp * density)
        }
    } else {
        val holder = rememberVectorMap()
        LaunchedEffect(lines, dark) {
            holder.styleLook = null // Reload so the lines are added to a fresh style.
            holder.setLook(MapLook(dark)) { style ->
                lines.forEachIndexed { i, l ->
                    val geometry = LineString.fromLngLats(l.points.map { Point.fromLngLat(it.longitude, it.latitude) })
                    style.addSource(GeoJsonSource("preview-$i", Feature.fromGeometry(geometry)))
                    style.addLayer(
                        LineLayer("preview-line-$i", "preview-$i").withProperties(
                            PropertyFactory.lineColor(l.color.toArgb()),
                            PropertyFactory.lineWidth(l.widthDp),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                }
                holder.withMap { m ->
                    m.uiSettings.setAllGesturesEnabled(false)
                    m.uiSettings.isAttributionEnabled = false
                    if (all.size >= 2) {
                        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                            .includes(all.map { MLatLng(it.latitude, it.longitude) })
                            .build()
                        holder.mapView.post {
                            runCatching { m.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 48)) }
                        }
                    }
                }
            }
        }
        AndroidView(
            modifier = modifier,
            factory = {
                (holder.mapView.parent as? ViewGroup)?.removeView(holder.mapView)
                holder.mapView
            },
        )
    }
}
