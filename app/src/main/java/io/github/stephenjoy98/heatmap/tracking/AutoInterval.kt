package io.github.stephenjoy98.heatmap.tracking

import com.google.android.gms.location.DetectedActivity

/**
 * Picks a recording interval from what the phone is doing: every second while moving fast (so routes trace
 * every turn of the road), a little less often on a bike or on foot, and sparse when sitting still to save
 * battery.
 */
object AutoInterval {
    const val VEHICLE_SEC = 1
    const val CYCLE_SEC = 2
    const val WALK_SEC = 3
    const val UNKNOWN_SEC = 5
    const val STILL_SEC = 15
    const val LONG_STILL_SEC = 60

    private const val STILL_AFTER_MS = 3 * 60_000L
    private const val LONG_STILL_AFTER_MS = 20 * 60_000L

    /**
     * [activity] is the latest activity-recognition result (-1 if unknown), [speedMps] the recent speed if
     * known, and [stillForMs] how long recent fixes have stayed within a small radius.
     */
    fun choose(activity: Int, speedMps: Float?, stillForMs: Long): Int {
        val speed = speedMps ?: -1f
        return when {
            activity == DetectedActivity.IN_VEHICLE || speed > 8f -> VEHICLE_SEC
            activity == DetectedActivity.ON_BICYCLE || speed > 3f -> CYCLE_SEC
            activity == DetectedActivity.WALKING || activity == DetectedActivity.RUNNING ||
                activity == DetectedActivity.ON_FOOT || speed > 0.7f -> WALK_SEC
            stillForMs > LONG_STILL_AFTER_MS -> LONG_STILL_SEC
            activity == DetectedActivity.STILL || stillForMs > STILL_AFTER_MS -> STILL_SEC
            else -> UNKNOWN_SEC
        }
    }
}
