package io.github.stephenjoy98.heatmap.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * The height of the ground above sea level, from the same keyless AWS Terrain Tiles (Terrarium) the terrain
 * shading uses, so 3D routes can be drawn at their height above the ground, the way the map's 3D buildings
 * are. Tiles are saved in the app's cache, so each one is downloaded once. Call off the main thread.
 */
class GroundHeights private constructor(private val dir: File) {
    // Decoded tiles (metres per pixel), about 256 KB each, and when tiles that couldn't be had may be tried again.
    private val tiles = LruCache<Long, Tile>(MEMORY_TILES)
    private val failed = HashMap<Long, Long>()

    private class Tile(val heights: FloatArray)

    /**
     * A lookup for one batch of routes. It downloads at most [MAX_DOWNLOADS] missing tiles, so a long history
     * never stalls the map; heights it can't find come back null.
     */
    fun session(): (Double, Double) -> Double? {
        var downloads = 0
        return { lat, lon ->
            val n = 1 shl ZOOM
            val x = (lon + 180.0) / 360.0 * n
            val latRad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
            val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
            val tx = floor(x).toInt()
            val ty = floor(y).toInt()
            val key = (tx.toLong() shl 32) or ty.toLong()
            val tile = tiles.get(key) ?: run {
                val cached = File(dir, "$ZOOM-$tx-$ty.png")
                val retryAt = synchronized(failed) { failed[key] } ?: 0L
                val bytes = when {
                    cached.exists() -> runCatching { cached.readBytes() }.getOrNull()
                    downloads < MAX_DOWNLOADS && System.currentTimeMillis() >= retryAt -> {
                        downloads++
                        download(tx, ty)?.also { b -> runCatching { dir.mkdirs(); cached.writeBytes(b) } }
                    }
                    else -> return@run null
                }
                val decoded = bytes?.let(::decode)
                if (decoded == null) {
                    cached.delete()
                    synchronized(failed) { failed[key] = System.currentTimeMillis() + RETRY_MS }
                    null
                } else {
                    Tile(decoded).also { tiles.put(key, it) }
                }
            }
            tile?.let { bilinear(it.heights, (x - tx) * SIZE - 0.5, (y - ty) * SIZE - 0.5) }
        }
    }

    private fun download(tx: Int, ty: Int): ByteArray? = runCatching {
        val conn = URL("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$ZOOM/$tx/$ty.png").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            if (conn.responseCode != 200) null else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Terrarium encoding: height = R × 256 + G + B / 256 − 32768 metres. */
    private fun decode(bytes: ByteArray): FloatArray? {
        val options = BitmapFactory.Options().apply { inPremultiplied = false }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        if (bitmap.width != SIZE || bitmap.height != SIZE) {
            bitmap.recycle()
            return null
        }
        val px = IntArray(SIZE * SIZE)
        bitmap.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        bitmap.recycle()
        return FloatArray(px.size) { i ->
            val c = px[i]
            ((c shr 16 and 0xFF) * 256 + (c shr 8 and 0xFF) + (c and 0xFF) / 256f) - 32768f
        }
    }

    private fun bilinear(h: FloatArray, fx: Double, fy: Double): Double {
        val x0 = floor(fx).toInt().coerceIn(0, SIZE - 1)
        val y0 = floor(fy).toInt().coerceIn(0, SIZE - 1)
        val x1 = (x0 + 1).coerceAtMost(SIZE - 1)
        val y1 = (y0 + 1).coerceAtMost(SIZE - 1)
        val ax = (fx - x0).coerceIn(0.0, 1.0)
        val ay = (fy - y0).coerceIn(0.0, 1.0)
        val top = h[y0 * SIZE + x0] * (1 - ax) + h[y0 * SIZE + x1] * ax
        val bottom = h[y1 * SIZE + x0] * (1 - ax) + h[y1 * SIZE + x1] * ax
        return top * (1 - ay) + bottom * ay
    }

    companion object {
        // About 12 m per pixel at Vancouver's latitude: plenty for ground height, and few tiles per city.
        private const val ZOOM = 13
        private const val SIZE = 256
        private const val MEMORY_TILES = 24
        private const val MAX_DOWNLOADS = 16
        private const val RETRY_MS = 10 * 60_000L

        @Volatile private var instance: GroundHeights? = null

        fun get(context: Context): GroundHeights = instance ?: synchronized(this) {
            instance ?: GroundHeights(File(context.applicationContext.cacheDir, "ground")).also { instance = it }
        }
    }
}
