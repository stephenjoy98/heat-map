package io.github.stephenjoy98.heatmap.tracking

import android.content.Context
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.widget.FollowWidget
import kotlinx.coroutines.flow.StateFlow

/**
 * Follow me, app-wide: while it's on, recording runs every second with GPS ([Prefs.followBoost]), whether the app
 * is open or not, and the map follows you whenever it's on screen. The map's location button (long-press) and the
 * home screen widget's button both turn it on and off, and arriving at a saved place turns it off. Turning it off
 * switches recording to automatic. Not saved: it ends if Android closes the app.
 */
object FollowMode {
    val on: StateFlow<Boolean> get() = Prefs.followBoost

    /**
     * Turns follow me on or off. With [startRecording] (the widget), turning it on also starts recording if it
     * was stopped; otherwise the recording interval only changes while recording.
     */
    fun set(context: Context, value: Boolean, startRecording: Boolean = false) {
        if (Prefs.followBoost.value == value) return
        Prefs.setFollowBoost(value)
        // Turning it off goes back to automatic recording, which adapts to how you're moving.
        if (!value) autoRecording()
        if (Prefs.tracking.value || (value && startRecording)) {
            runCatching { LocationTickerService.start(context) }
        }
        FollowWidget.requestUpdate(context, force = true)
    }

    /** Switches recording to automatic, if it wasn't (built-in battery profiles always are). */
    fun autoRecording() {
        if (!Prefs.autoInterval.value) Prefs.setAutoInterval(true)
    }
}
