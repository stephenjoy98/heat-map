package io.github.stephenjoy98.heatmap.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import io.github.stephenjoy98.heatmap.data.Route3d
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Keyless vector maps from OpenFreeMap (OpenStreetMap data), drawn by MapLibre at the screen's full
 * resolution. Light is OpenFreeMap's clean Positron style (close to an in-car navigation map); dark is
 * the same style recoloured to the near-black greys of Google's dark theme.
 */
object VectorMaps {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles · © OpenStreetMap contributors"

    /** The colours a map style is painted in. */
    private class Palette(
        val background: Int,
        val landuse: Int,
        val park: Int,
        val water: Int,
        val building: Int,
        val minorRoad: Int,
        val majorRoad: Int,
        val casing: Int,
        val rail: Int,
        val border: Int,
        val label: Int,
        val halo: Int,
    ) {
        fun blend(to: Palette, t: Float) = Palette(
            mix(background, to.background, t), mix(landuse, to.landuse, t), mix(park, to.park, t), mix(water, to.water, t),
            mix(building, to.building, t), mix(minorRoad, to.minorRoad, t), mix(majorRoad, to.majorRoad, t),
            mix(casing, to.casing, t), mix(rail, to.rail, t), mix(border, to.border, t), mix(label, to.label, t),
            mix(halo, to.halo, t),
        )
    }

    private fun c(hex: String) = Color.parseColor(hex)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * t).toInt().coerceIn(0, 255)
        return Color.rgb(ch(16), ch(8), ch(0))
    }

    // Google-style dark: near-black greys…
    private val darkMuted = Palette(
        c("#121212"), c("#161616"), c("#141A15"), c("#0A0E13"), c("#1C1C1C"), c("#262626"), c("#363636"),
        c("#121212"), c("#242424"), c("#3C3C3C"), c("#8E9196"), c("#121212"),
    )
    // …and the same with colour: blue water, green parks, lighter roads and buildings, brighter labels.
    private val darkVivid = Palette(
        c("#0F141A"), c("#171C22"), c("#123A22"), c("#0B3358"), c("#2C323A"), c("#3E4650"), c("#7C8794"),
        c("#0A0D11"), c("#5A4A6E"), c("#7A68A0"), c("#D2D6DC"), c("#0F141A"),
    )
    // OpenFreeMap's Positron, near enough…
    private val lightMuted = Palette(
        c("#F2F3F0"), c("#EAEAE6"), c("#E2EADB"), c("#C6D6E0"), c("#E3E1DD"), c("#FFFFFF"), c("#FFFFFF"),
        c("#D9D9D9"), c("#D6D6D6"), c("#C4C4C4"), c("#6B6B6B"), c("#FFFFFF"),
    )
    // …and with colour, like Google Maps: blue water, green parks, yellow main roads, warm buildings.
    private val lightVivid = Palette(
        c("#F3F1EC"), c("#ECE6DA"), c("#BFE3AE"), c("#8EC5F2"), c("#DCCDBE"), c("#FFFFFF"), c("#FFD873"),
        c("#B3B3B3"), c("#B39DCB"), c("#A47CC9"), c("#2E2E2E"), c("#FFFFFF"),
    )

    /** Recolours every layer of the light style for dark mode, matching on layer type and name. */
    fun applyDark(style: Style) = applyColours(style, dark = true, vivid = 0f)

    /**
     * Paints the map in its dark or light palette, [vivid] of the way (0–1) from the muted standard colours to
     * colourful ones with more contrast. Matches layers on their type and name.
     */
    fun applyColours(style: Style, dark: Boolean, vivid: Float) {
        if (!style.isFullyLoaded) return
        val p = if (dark) darkMuted.blend(darkVivid, vivid) else lightMuted.blend(lightVivid, vivid)
        for (layer in style.layers) {
            val id = layer.id.lowercase()
            if (id.startsWith("route-3d") || id.startsWith("map-tint") || id.startsWith("preview") || id.startsWith("terrain")) continue
            when (layer) {
                is FillExtrusionLayer -> if (id == BUILDINGS_LAYER) {
                    layer.setProperties(PropertyFactory.fillExtrusionColor(mix(if (dark) building3dDark else building3dLight, p.building, vivid)))
                }
                is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(p.background))
                is FillLayer -> layer.setProperties(
                    PropertyFactory.fillColor(
                        when {
                            "water" in id -> p.water
                            "building" in id -> p.building
                            listOf("park", "wood", "grass", "forest", "landcover", "green").any { it in id } -> p.park
                            else -> p.landuse
                        }
                    ),
                    PropertyFactory.fillOutlineColor(p.background),
                )
                is LineLayer -> layer.setProperties(
                    PropertyFactory.lineColor(
                        when {
                            "water" in id || "river" in id -> p.water
                            "casing" in id -> p.casing
                            "boundary" in id || "admin" in id -> p.border
                            "rail" in id -> p.rail
                            listOf("motorway", "trunk", "primary", "major").any { it in id } -> p.majorRoad
                            else -> p.minorRoad
                        }
                    )
                )
                is SymbolLayer -> {
                    layer.setProperties(PropertyFactory.textColor(p.label), PropertyFactory.textHaloColor(p.halo))
                    if (dark) layer.setProperties(PropertyFactory.iconOpacity(0.6f + 0.3f * vivid))
                }
            }
        }
    }

    /**
     * Adds terrain shading from the keyless AWS Terrain Tiles (Terrarium-encoded elevation), drawn under
     * roads and labels so they stay readable.
     */
    fun addTerrain(style: Style, dark: Boolean) {
        if (style.getSource(TERRAIN_SOURCE) != null) return
        style.addSource(terrainSource())
        val hillshade = hillshadeLayer(dark)
        val below = firstLayerAbove(style) { id, layer ->
            layer is SymbolLayer || listOf("building", "tunnel", "highway", "road", "bridge", "transportation").any { it in id }
        }
        if (below != null) style.addLayerBelow(hillshade, below) else style.addLayer(hillshade)
    }

    /**
     * The elevation source behind terrain shading. A new one is needed for each style. The tiles are 256 px,
     * but declaring them as 128 makes MapLibre fetch tiles two zoom levels more detailed than it otherwise
     * would (up to the data's maximum, zoom 15), so hills stay sharp on high-density screens.
     */
    fun terrainSource(): RasterDemSource {
        val tiles = TileSet("2.2.0", "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png").apply {
            encoding = "terrarium"
            setMaxZoom(15f)
        }
        return RasterDemSource(TERRAIN_SOURCE, tiles, TERRAIN_TILE_SIZE)
    }

    /** Hill shading from [terrainSource], softer on the dark map. */
    fun hillshadeLayer(dark: Boolean): HillshadeLayer =
        HillshadeLayer("terrain-hillshade", TERRAIN_SOURCE).withProperties(
            PropertyFactory.hillshadeExaggeration(if (dark) 0.45f else 0.6f),
            PropertyFactory.hillshadeShadowColor(if (dark) "#000000" else "#5F6B5A"),
            PropertyFactory.hillshadeHighlightColor(if (dark) "#2A2A2A" else "#FFFFFF"),
            PropertyFactory.hillshadeAccentColor(if (dark) "#0A0A0A" else "#7D8A72"),
        )

    /** Extrudes OpenMapTiles building footprints by their height, Google Maps-style. Seen when the map is tilted. */
    fun addBuildings(style: Style, dark: Boolean) {
        if (style.getLayer(BUILDINGS_LAYER) != null) return
        val flat = style.layers.firstOrNull { it is FillLayer && "building" in it.id.lowercase() } as? FillLayer
        val sourceId = flat?.sourceId ?: style.sources.firstOrNull { it is VectorSource }?.id ?: return
        val extrusion = FillExtrusionLayer(BUILDINGS_LAYER, sourceId).withSourceLayer("building").withProperties(
            PropertyFactory.fillExtrusionColor(if (dark) building3dDark else building3dLight),
            PropertyFactory.fillExtrusionHeight(Expression.coalesce(Expression.get("render_height"), Expression.literal(6))),
            PropertyFactory.fillExtrusionBase(Expression.coalesce(Expression.get("render_min_height"), Expression.literal(0))),
            PropertyFactory.fillExtrusionOpacity(0.9f),
        )
        extrusion.minZoom = 13f
        val below = firstLayerAbove(style) { _, layer -> layer is SymbolLayer }
        if (below != null) style.addLayerBelow(extrusion, below) else style.addLayer(extrusion)
    }

    /**
     * Shows routes in 3D: a translucent curtain from the ground up to each hop's height, topped by a solid
     * ribbon, so the route visibly climbs and descends among the 3D buildings. Replaces any previous route.
     */
    fun showRoute3d(style: Style, segments: List<Route3d.Segment>) {
        if (!style.isFullyLoaded) return
        val features = segments.map { seg ->
            val c = seg.corners
            val ring = listOf(
                Point.fromLngLat(c[1], c[0]), Point.fromLngLat(c[3], c[2]),
                Point.fromLngLat(c[5], c[4]), Point.fromLngLat(c[7], c[6]),
                Point.fromLngLat(c[1], c[0]),
            )
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                addNumberProperty("h", seg.heightM)
                addNumberProperty("t", seg.heightM + RIBBON_M)
                // The curtain stops just under the ribbon: touching faces flicker as the map moves.
                addNumberProperty("c", (seg.heightM - CURTAIN_GAP_M).coerceAtLeast(0.0))
            }
        }
        val data = FeatureCollection.fromFeatures(features)
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)
        if (source != null) {
            source.setGeoJson(data)
            return
        }
        style.addSource(GeoJsonSource(ROUTE_SOURCE, data))
        val curtain = FillExtrusionLayer(ROUTE_CURTAIN, ROUTE_SOURCE).withProperties(
            PropertyFactory.fillExtrusionColor(routeColor),
            PropertyFactory.fillExtrusionBase(0f),
            PropertyFactory.fillExtrusionHeight(Expression.get("c")),
            PropertyFactory.fillExtrusionOpacity(0.2f),
        )
        val ribbon = FillExtrusionLayer(ROUTE_RIBBON, ROUTE_SOURCE).withProperties(
            PropertyFactory.fillExtrusionColor(routeColor),
            PropertyFactory.fillExtrusionBase(Expression.get("h")),
            PropertyFactory.fillExtrusionHeight(Expression.get("t")),
            PropertyFactory.fillExtrusionOpacity(0.95f),
        )
        // Labels stay on top.
        val below = firstLayerAbove(style) { _, layer -> layer is SymbolLayer }
        for (layer in listOf(curtain, ribbon)) {
            if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
    }

    /**
     * Makes the base map lighter (positive [amount], up to 1) or darker (negative) by laying a sheet of white
     * or black over roads, land, water and buildings, under labels, routes and the heatmap (which is drawn
     * on top of the map separately). 0 removes it.
     */
    fun setTint(style: Style, amount: Float) {
        if (!style.isFullyLoaded) return
        val layer = style.getLayerAs<FillLayer>(TINT_LAYER)
        if (amount == 0f) {
            if (layer != null) style.removeLayer(TINT_LAYER)
            return
        }
        val color = if (amount > 0f) Color.WHITE else Color.BLACK
        val opacity = kotlin.math.abs(amount).coerceAtMost(1f)
        if (layer != null) {
            layer.setProperties(PropertyFactory.fillColor(color), PropertyFactory.fillOpacity(opacity))
            return
        }
        if (style.getSource(TINT_SOURCE) == null) {
            val world = Polygon.fromLngLats(
                listOf(
                    listOf(
                        Point.fromLngLat(-180.0, -85.0), Point.fromLngLat(180.0, -85.0), Point.fromLngLat(180.0, 85.0),
                        Point.fromLngLat(-180.0, 85.0), Point.fromLngLat(-180.0, -85.0),
                    ),
                ),
            )
            style.addSource(GeoJsonSource(TINT_SOURCE, Feature.fromGeometry(world)))
        }
        val tint = FillLayer(TINT_LAYER, TINT_SOURCE).withProperties(
            PropertyFactory.fillColor(color),
            PropertyFactory.fillOpacity(opacity),
            PropertyFactory.fillAntialias(false),
        )
        val below = firstLayerAbove(style) { id, l -> l is SymbolLayer || id.startsWith("route-3d") }
        if (below != null) style.addLayerBelow(tint, below) else style.addLayer(tint)
    }

    private const val TINT_SOURCE = "map-tint"
    private const val TINT_LAYER = "map-tint"

    fun clearRoute3d(style: Style) {
        if (!style.isFullyLoaded) return
        style.removeLayer(ROUTE_RIBBON)
        style.removeLayer(ROUTE_CURTAIN)
        style.removeSource(ROUTE_SOURCE)
    }

    private const val ROUTE_SOURCE = "route-3d"
    private const val ROUTE_CURTAIN = "route-3d-curtain"
    private const val ROUTE_RIBBON = "route-3d-ribbon"
    private const val RIBBON_M = 1.5
    private const val CURTAIN_GAP_M = 0.4
    private val routeColor = Color.parseColor("#FF7A1A")

    private fun firstLayerAbove(style: Style, match: (id: String, layer: Layer) -> Boolean): String? =
        style.layers.firstOrNull { match(it.id.lowercase(), it) }?.id

    private const val TERRAIN_SOURCE = "terrain-dem"
    private const val TERRAIN_TILE_SIZE = 128
    private const val BUILDINGS_LAYER = "buildings-3d"
    private val building3dLight = Color.parseColor("#DADCE0")
    private val building3dDark = Color.parseColor("#262626")
}

/**
 * Renders a [TileBitmapSource] for whatever map engine is on screen: tiles are rendered on background
 * threads and cached. After [refresh], existing tiles keep showing until replacements are ready, so new
 * points don't make the heatmap flicker.
 */
class TileLayerRenderer(source: TileBitmapSource, alpha: Float, private val onTileReady: () -> Unit) {
    private class Entry(val bitmap: Bitmap?, val generation: Int)

    var source: TileBitmapSource = source
        set(value) {
            field = value
            refresh()
        }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).toInt() }
    private val cache = object : LruCache<Long, Entry>(64 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Entry) = value.bitmap?.byteCount ?: 64
    }
    private val pending = HashSet<Long>()
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private val corners = FloatArray(8)
    private val src = FloatArray(8)
    private val matrix = Matrix()

    fun setAlpha(alpha: Float) {
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    }

    fun refresh() {
        generation++
        pending.clear()
    }

    /**
     * Draws the tiles at [maxZoom] (or coarser, when that would take too many) covering the given bounds. [place] writes a tile's four corners in screen
     * pixels (top-left, top-right, bottom-right, bottom-left) so tiles follow a rotated map.
     */
    fun draw(
        canvas: Canvas,
        maxZoom: Int,
        north: Double, south: Double, east: Double, west: Double,
        place: (north: Double, west: Double, south: Double, east: Double, out: FloatArray) -> Unit,
    ) {
        // A tilted map can show a lot of ground towards the horizon; use coarser tiles until it's manageable.
        var zoom = maxZoom
        var n: Int
        var x0: Int
        var x1: Int
        var y0: Int
        var y1: Int
        while (true) {
            n = 1 shl zoom
            x0 = tileX(west, n)
            x1 = tileX(east, n)
            y0 = tileY(north, n)
            y1 = tileY(south, n)
            if ((x1 - x0 + 1) * (y1 - y0 + 1) <= 64 || zoom == 0) break
            zoom--
        }

        for (x in x0..x1) for (y in y0..y1) {
            val key = (zoom.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()
            val entry = cache.get(key)
            if ((entry == null || entry.generation != generation) && key !in pending) request(key, x, y, zoom)
            val bitmap = entry?.bitmap ?: continue
            if (bitmap.isRecycled) continue
            val box = TileMath.box(x, y, zoom)
            place(box.north, box.west, box.south, box.east, corners)
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            src[2] = w; src[4] = w; src[5] = h; src[7] = h
            matrix.setPolyToPoly(src, 0, corners, 0, 4)
            canvas.drawBitmap(bitmap, matrix, paint)
        }
    }

    private fun request(key: Long, x: Int, y: Int, zoom: Int) {
        pending += key
        val gen = generation
        val src = source
        executor.execute {
            val bitmap = runCatching { src.render(x, y, zoom) }.getOrNull()
            main.post {
                if (gen != generation) return@post
                pending -= key
                cache.put(key, Entry(bitmap, gen))
                onTileReady()
            }
        }
    }

    fun release() {
        executor.shutdownNow()
        cache.evictAll()
    }

    private fun tileX(lon: Double, n: Int) = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)

    private fun tileY(lat: Double, n: Int): Int {
        val r = lat.coerceIn(-85.05, 85.05) * PI / 180
        return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt().coerceIn(0, n - 1)
    }
}

/**
 * A transparent view laid over a MapLibre map that draws heat (and other) tile layers plus a "latest
 * position" dot, following the camera, including rotation and tilt (tiles are drawn in perspective).
 */
@SuppressLint("ViewConstructor")
class TileOverlayView(context: Context) : View(context) {
    private var map: MapLibreMap? = null
    private val renderers = LinkedHashMap<String, Pair<Float, TileLayerRenderer>>() // id -> (zIndex, renderer)
    private val density = resources.displayMetrics.density
    // The location marker: a blue dot with a white ring and a soft halo, as in most map apps.
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 66, 133, 244) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(66, 133, 244) }

    var latest: LatLng? = null
        set(value) {
            field = value
            invalidate()
        }

    init {
        // Touches go through to the map underneath.
        isClickable = false
        isFocusable = false
    }

    fun attach(map: MapLibreMap) {
        this.map = map
        map.addOnCameraMoveListener { invalidate() }
        map.addOnCameraIdleListener { invalidate() }
        invalidate()
    }

    /** Adds, updates and removes layers to match; each entry is (id, source, alpha, zIndex). */
    fun setLayers(layers: List<LayerSpec>) {
        val wanted = layers.associateBy { it.id }
        renderers.keys.filter { it !in wanted }.forEach { renderers.remove(it)?.second?.release() }
        for (l in layers) {
            val existing = renderers[l.id]?.second
            if (existing == null) {
                renderers[l.id] = l.zIndex to TileLayerRenderer(l.source, l.alpha) { invalidate() }
            } else {
                if (existing.source !== l.source) existing.source = l.source
                existing.setAlpha(l.alpha)
                renderers[l.id] = l.zIndex to existing
            }
        }
        invalidate()
    }

    fun refresh() {
        renderers.values.forEach { it.second.refresh() }
        invalidate()
    }

    fun release() {
        renderers.values.forEach { it.second.release() }
        renderers.clear()
    }

    override fun onDraw(canvas: Canvas) {
        val map = map ?: return
        val projection = map.projection
        val bounds = projection.visibleRegion.latLngBounds
        // MapLibre zoom is in 512 px tiles; tile zoom z+1 puts one heat tile on ~256 dp of screen.
        val zoom = floor(map.cameraPosition.zoom + 1).toInt().coerceIn(0, 21)
        val place = { north: Double, west: Double, south: Double, east: Double, out: FloatArray ->
            val tl = projection.toScreenLocation(LatLng(north, west))
            val tr = projection.toScreenLocation(LatLng(north, east))
            val br = projection.toScreenLocation(LatLng(south, east))
            val bl = projection.toScreenLocation(LatLng(south, west))
            out[0] = tl.x; out[1] = tl.y
            out[2] = tr.x; out[3] = tr.y
            out[4] = br.x; out[5] = br.y
            out[6] = bl.x; out[7] = bl.y
        }
        for ((_, renderer) in renderers.values.sortedBy { it.first }) {
            renderer.draw(canvas, zoom, bounds.latitudeNorth, bounds.latitudeSouth, bounds.longitudeEast, bounds.longitudeWest, place)
        }
        latest?.let {
            val p = projection.toScreenLocation(it)
            canvas.drawCircle(p.x, p.y, 14 * density, halo)
            canvas.drawCircle(p.x, p.y, 7 * density, ring)
            canvas.drawCircle(p.x, p.y, 5 * density, dot)
        }
    }

    class LayerSpec(val id: String, val source: TileBitmapSource, val alpha: Float, val zIndex: Float)
}
