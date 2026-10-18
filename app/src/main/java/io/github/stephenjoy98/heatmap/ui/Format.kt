package io.github.stephenjoy98.heatmap.ui

/** Slider stops for the recording interval, from the 15 s minimum to once a day. */
val INTERVAL_PRESETS = intArrayOf(
    15, 30, 60, 2 * 60, 5 * 60, 10 * 60, 15 * 60, 30 * 60,
    3600, 2 * 3600, 3 * 3600, 6 * 3600, 12 * 3600, 24 * 3600,
)

/** "15 s", "2 min", "1 h 30 min", "1 day". */
fun formatInterval(seconds: Int): String {
    if (seconds >= 86_400 && seconds % 86_400 == 0) {
        val d = seconds / 86_400
        return if (d == 1) "1 day" else "$d days"
    }
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return buildList {
        if (h > 0) add("$h h")
        if (m > 0) add("$m min")
        if (s > 0) add("$s s")
    }.joinToString(" ")
}

fun formatCount(n: Long): String = "%,d".format(n)
