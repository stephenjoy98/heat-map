package io.github.stephenjoy98.heatmap.data

/**
 * Battery profiles: how often dots are recorded for each activity (seconds) and how precise the fixes are.
 * [USER] is whatever you set yourself under Recording (a fixed interval or automatic, GPS accuracy on or off);
 * its automatic intervals are the app's standard ones. The others turn the automatic interval on with their
 * own pace. Saved places still slow recording down while you're settled, whichever profile is on.
 */
enum class PowerProfile(
    val label: String,
    val vehicleSec: Int,
    val cycleSec: Int,
    val walkSec: Int,
    val unknownSec: Int,
    val stillSec: Int,
    val longStillSec: Int,
    /** Precise GPS fixes (within 100 m kept) rather than Wi-Fi and mobile networks (within 500 m). */
    val highAccuracy: Boolean,
    val battery: String,
) {
    USER("User", 1, 2, 3, 5, 15, 60, true, "Depends on your settings"),
    PERFORMANCE("Performance", 1, 1, 2, 3, 10, 30, true, "Uses the most battery; the most detailed routes"),
    DEFAULT("Default", 3, 5, 8, 15, 60, 300, true, "A balance of detail and battery"),
    SAVER("Battery saver", 10, 15, 30, 60, 300, 900, false, "Uses the least battery; routes are rougher and can cut corners"),
}
