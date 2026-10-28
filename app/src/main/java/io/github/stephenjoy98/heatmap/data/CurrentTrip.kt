package io.github.stephenjoy98.heatmap.data

/**
 * How far you've gone on the trip you're on now, for the follow me widget: the distance along the newest dots
 * since you last stayed in one place (within [STOP_RADIUS_M] for at least [STOP_MS]). Pure Kotlin, so it can be
 * tested off the phone.
 */
object CurrentTrip {
    const val STOP_RADIUS_M = 100.0
    const val STOP_MS = 5 * 60_000L
    // Less than this since the last stop is GPS wobble or a few steps, not a trip yet.
    const val MIN_TRIP_M = 150.0

    /**
     * Metres travelled along dots ([time], [lat], [lon], oldest first, one unbroken run) since the last stop, or
     * null while you're stopped or haven't gone [MIN_TRIP_M] yet.
     */
    fun meters(time: LongArray, lat: DoubleArray, lon: DoubleArray): Double? {
        if (time.size < 2) return null
        var start = 0
        var anchor = 0
        var stopped = false
        for (i in 1 until time.size) {
            if (TrackRules.distanceMeters(lat[anchor], lon[anchor], lat[i], lon[i]) > STOP_RADIUS_M) {
                // Left the spot: if you'd stayed there, the trip starts from the last dot that was still at it (the
                // dots on the way out, inside the radius, count as part of the trip).
                if (stopped) {
                    var j = i - 1
                    while (j > anchor && TrackRules.distanceMeters(lat[anchor], lon[anchor], lat[j], lon[j]) > STOP_RADIUS_M / 2) j--
                    start = j
                }
                anchor = i
                stopped = false
            } else if (time[i] - time[anchor] >= STOP_MS) {
                stopped = true
            }
        }
        if (stopped) return null
        var m = 0.0
        for (i in start + 1 until time.size) m += TrackRules.distanceMeters(lat[i - 1], lon[i - 1], lat[i], lon[i])
        return m.takeIf { it >= MIN_TRIP_M }
    }
}
