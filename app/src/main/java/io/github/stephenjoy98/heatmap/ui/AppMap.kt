package io.github.stephenjoy98.heatmap.ui

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import io.github.stephenjoy98.heatmap.data.Route3d
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.ElevationPoint
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.geometry.LatLng
import io.github.stephenjoy98.heatmap.data.MapLayerType
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.map.TileBitmapSource
import kotlinx.coroutines.delay
import io.github.stephenjoy98.heatmap.map.TileOverlayView
import io.github.stephenjoy98.heatmap.map.VectorMaps
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.WeakHashMap
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

    /** Ground distance one screen pixel covers at the middle of the map, in metres (0 until known). */
    var metersPerPixel by mutableFloatStateOf(0f)
        internal set

    /** True while the map is being moved or zoomed, by a gesture or an animation. */
    var moving by mutableStateOf(false)
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
    onMapClick: ((lat: Double, lon: Double) -> Unit)? = null,
    cacheKey: String? = null,
    // Loads routes with heights for an area; with 3D buildings on, they're drawn as a 3D route. Runs off
    // the main thread.
    route3d: ((GeoBox) -> List<List<ElevationPoint>>)? = null,
) {
    val dark = isDarkTheme()
    val mapLayer by Prefs.mapLayer.collectAsStateWithLifecycle()
    val buildings by Prefs.buildings3d.collectAsStateWithLifecycle()
    val style = MapLook(dark, mapLayer == MapLayerType.TERRAIN, buildings)
    VectorAppMap(camera, layers, style, refreshKey, latest, onMapClick, cacheKey, route3d, modifier)
}

/** How the base map looks: theme, terrain shading and 3D buildings (which also tilt the camera). */
private data class MapLook(val dark: Boolean, val terrain: Boolean = false, val buildings: Boolean = false)

private const val TILT_3D = 45f

// Tilting further than this by hand switches on 3D buildings, and back below the second switches them off.
private const val AUTO_3D_TILT = 8f
private const val AUTO_FLAT_TILT = 2f

/** Owns a MapLibre MapView, follows the Compose lifecycle, and hands out the map once it's ready. */
private class VectorMapHolder(context: Context) {
    val mapView = MapView(context).apply { onCreate(null) }
    var map: MapLibreMap? = null
    var styleLook: Any? = null
    private val readyCallbacks = mutableListOf<(MapLibreMap) -> Unit>()

    // For full maps: the heat overlay, and what the map's listeners report to. Kept here so a cached map
    // keeps its tiles and doesn't gain a second set of listeners when it's shown again.
    val overlay by lazy { TileOverlayView(context) }
    var camera: MapCamera? = null
    var onClick: ((Double, Double) -> Unit)? = null
    var setUp = false

    // Whether the current camera move is the user's own gesture, and whether 3D was just switched on by one.
    var gesture = false
    var tiltedByGesture = false

    /** Goes up each time a style finishes loading, so extra layers can be added back. */
    var styleVersion by mutableIntStateOf(0)

    fun destroy() {
        overlay.release()
        mapView.onDestroy()
    }

    init {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = true
            // Tilting is always allowed; tilting by hand switches on 3D buildings.
            m.uiSettings.isTiltGesturesEnabled = true
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
            styleVersion++
        }
    }
}

/**
 * MapLibre maps kept alive per activity, one per key (e.g. each tab's map), so switching tabs shows the map
 * as it was instead of loading it again. They're destroyed with the activity.
 */
private object VectorMapCache {
    private val byActivity = WeakHashMap<Activity, HashMap<String, VectorMapHolder>>()

    fun get(context: Context, key: String): VectorMapHolder {
        val activity = context.findActivity()
            ?: return VectorMapHolder(context)
        val maps = byActivity.getOrPut(activity) {
            HashMap<String, VectorMapHolder>().also { maps ->
                (activity as? LifecycleOwner)?.lifecycle?.addObserver(LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        maps.values.forEach { it.destroy() }
                        maps.clear()
                        byActivity.remove(activity)
                    }
                })
            }
        }
        return maps.getOrPut(key) { VectorMapHolder(activity) }
    }
}

@Composable
private fun rememberVectorMap(cacheKey: String? = null): VectorMapHolder {
    val context = LocalContext.current
    val holder = remember(cacheKey) { if (cacheKey != null) VectorMapCache.get(context, cacheKey) else VectorMapHolder(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, holder) {
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
            // A cached map is only put to sleep; the cache destroys it with the activity.
            if (cacheKey == null) holder.destroy()
        }
    }
    return holder
}

/** Metres covered by one screen pixel across the middle of the map. */
private fun metersPerPixel(m: MapLibreMap, view: View): Float {
    val w = view.width
    if (w <= 0) return 0f
    val y = view.height / 2f
    val a = m.projection.fromScreenLocation(PointF(w / 2f - 50f, y))
    val b = m.projection.fromScreenLocation(PointF(w / 2f + 50f, y))
    return (a.distanceTo(b) / 100.0).toFloat()
}

// The app's zoom levels follow the usual web-map convention of 256 px tiles; MapLibre counts 512 px tiles,
// so it's one level lower.
private fun Float.toVectorZoom(): Double = (this - 1f).toDouble().coerceAtLeast(0.0)

@OptIn(FlowPreview::class)
@Composable
private fun VectorAppMap(
    camera: MapCamera,
    layers: List<MapLayer>,
    look: MapLook,
    refreshKey: Any?,
    latest: LatLng?,
    onMapClick: ((Double, Double) -> Unit)?,
    cacheKey: String?,
    route3d: ((GeoBox) -> List<List<ElevationPoint>>)?,
    modifier: Modifier,
) {
    val holder = rememberVectorMap(cacheKey)
    val loadRoute3d by rememberUpdatedState(route3d)
    val overlay = holder.overlay
    SideEffect { holder.onClick = onMapClick }

    DisposableEffect(camera) {
        holder.camera = camera
        holder.withMap { m ->
            // Listeners are added once per map and report to whichever camera is current.
            if (!holder.setUp) {
                holder.setUp = true
                m.moveCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        MLatLng(camera.initialLat, camera.initialLon), camera.initialZoom.toVectorZoom(),
                    )
                )
                overlay.attach(m)
                val report = {
                    holder.camera?.let {
                        it.bearing = m.cameraPosition.bearing.toFloat()
                        it.cameraTick++
                        it.metersPerPixel = metersPerPixel(m, holder.mapView)
                    }
                }
                m.addOnCameraMoveStartedListener { reason ->
                    holder.gesture = reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                    holder.camera?.moving = true
                }
                m.addOnCameraMoveListener {
                    report()
                    // Tilting by hand switches 3D buildings on; tilting back flat switches them off.
                    if (holder.gesture) {
                        val tilt = m.cameraPosition.tilt
                        if (tilt > AUTO_3D_TILT && !Prefs.buildings3d.value) {
                            holder.tiltedByGesture = true
                            Prefs.setBuildings3d(true)
                        } else if (tilt < AUTO_FLAT_TILT && Prefs.buildings3d.value) {
                            Prefs.setBuildings3d(false)
                        }
                    }
                }
                m.addOnCameraIdleListener {
                    report()
                    holder.gesture = false
                    holder.camera?.moving = false
                }
                // Returning false lets the map handle the tap as usual.
                m.addOnMapClickListener { p ->
                    holder.onClick?.invoke(p.latitude, p.longitude)
                    false
                }
            }
            camera.bearing = m.cameraPosition.bearing.toFloat()
            camera.projector = { lat, lon ->
                m.projection.toScreenLocation(MLatLng(lat, lon)).let { Offset(it.x, it.y) }
            }
        }
        onDispose {
            camera.projector = null
            if (holder.camera === camera) holder.camera = null
        }
    }

    LaunchedEffect(look) { holder.setLook(look) }

    LaunchedEffect(look.buildings) {
        holder.withMap { m ->
            // Switched on by tilting: keep the angle the user chose.
            if (look.buildings && holder.tiltedByGesture) {
                holder.tiltedByGesture = false
                return@withMap
            }
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

    // 3D route: while 3D buildings are on, load the routes in view whenever the camera settles (or the style
    // reloads, or new dots arrive) and draw them at their recorded heights.
    LaunchedEffect(holder, camera, look.buildings, route3d != null, refreshKey) {
        if (!look.buildings || route3d == null) {
            holder.withMap { m -> m.style?.let(VectorMaps::clearRoute3d) }
            return@LaunchedEffect
        }
        snapshotFlow { camera.cameraTick to holder.styleVersion }
            .debounce(400)
            .collectLatest {
                val m = holder.map ?: return@collectLatest
                val b = m.projection.visibleRegion.latLngBounds
                val box = GeoBox(b.latitudeNorth, b.latitudeSouth, b.longitudeEast, b.longitudeWest)
                val load = loadRoute3d ?: return@collectLatest
                val segments = withContext(Dispatchers.IO) { Route3d.build(load(box)) }
                m.style?.let { VectorMaps.showRoute3d(it, segments) }
            }
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
