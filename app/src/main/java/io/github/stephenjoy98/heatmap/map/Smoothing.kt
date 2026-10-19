package io.github.stephenjoy98.heatmap.map

/**
 * Turns the straight hops between GPS fixes into a smooth curve: a 3-point moving average removes jitter,
 * then two rounds of Chaikin corner cutting round off the corners. The ends stay where they were recorded.
 */
object Smoothing {
    fun smooth(lat: DoubleArray, lon: DoubleArray, from: Int, to: Int): Pair<DoubleArray, DoubleArray> {
        val n = to - from
        if (n < 3) return lat.copyOfRange(from, to) to lon.copyOfRange(from, to)

        var la = DoubleArray(n) { i ->
            val k = from + i
            if (i == 0 || i == n - 1) lat[k] else (lat[k - 1] + lat[k] + lat[k + 1]) / 3
        }
        var lo = DoubleArray(n) { i ->
            val k = from + i
            if (i == 0 || i == n - 1) lon[k] else (lon[k - 1] + lon[k] + lon[k + 1]) / 3
        }
        repeat(2) {
            val m = la.size
            val nla = DoubleArray((m - 1) * 2)
            val nlo = DoubleArray((m - 1) * 2)
            for (i in 0 until m - 1) {
                nla[2 * i] = 0.75 * la[i] + 0.25 * la[i + 1]
                nlo[2 * i] = 0.75 * lo[i] + 0.25 * lo[i + 1]
                nla[2 * i + 1] = 0.25 * la[i] + 0.75 * la[i + 1]
                nlo[2 * i + 1] = 0.25 * lo[i] + 0.75 * lo[i + 1]
            }
            // Keep the real start and end points.
            nla[0] = la[0]; nlo[0] = lo[0]
            nla[nla.size - 1] = la[m - 1]; nlo[nlo.size - 1] = lo[m - 1]
            la = nla
            lo = nlo
        }
        return la to lo
    }
}
