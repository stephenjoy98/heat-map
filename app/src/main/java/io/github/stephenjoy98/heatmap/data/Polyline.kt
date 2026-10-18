package io.github.stephenjoy98.heatmap.data

import kotlin.math.roundToLong

/** Google's encoded polyline format (1e-5 degree precision, about 1 m), used to share routes compactly. */
object Polyline {
    fun encode(lat: DoubleArray, lon: DoubleArray, from: Int = 0, to: Int = lat.size): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for (i in from until to) {
            val la = (lat[i] * 1e5).roundToLong()
            val lo = (lon[i] * 1e5).roundToLong()
            write(sb, la - prevLat)
            write(sb, lo - prevLon)
            prevLat = la
            prevLon = lo
        }
        return sb.toString()
    }

    /** Returns interleaved [lat0, lon0, lat1, lon1, ...]. */
    fun decode(encoded: String): DoubleArray {
        val out = ArrayList<Double>(encoded.length / 2)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1

            if (index >= encoded.length) break
            result = 0L
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1

            out += lat / 1e5
            out += lon / 1e5
        }
        return out.toDoubleArray()
    }

    private fun write(sb: StringBuilder, value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }
}
