package io.github.stephenjoy98.heatmap.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Follows Android's activity recognition (in vehicle, on bicycle, walking, running, still) while recording,
 * so each stored fix can be tagged with what the phone thought you were doing.
 */
object ActivityTracker {
    /** A [DetectedActivity] type, or -1 when unknown. */
    @Volatile
    var current: Int = -1
        private set

    private val tracked = listOf(
        DetectedActivity.IN_VEHICLE,
        DetectedActivity.ON_BICYCLE,
        DetectedActivity.WALKING,
        DetectedActivity.RUNNING,
        DetectedActivity.STILL,
    )

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Checked by hasPermission.
    fun start(context: Context) {
        if (!hasPermission(context)) return
        val transitions = tracked.flatMap { type ->
            listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                ActivityTransition.Builder().setActivityType(type).setActivityTransition(it).build()
            }
        }
        runCatching {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(context))
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        current = -1
        if (!hasPermission(context)) return
        runCatching { ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent(context)) }
    }

    internal fun onResult(result: ActivityTransitionResult) {
        for (event in result.transitionEvents) {
            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                current = event.activityType
            } else if (event.activityType == current) {
                current = -1
            }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        // Mutable: Play services adds the transition result to this intent.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 7, Intent(context, ActivityReceiver::class.java), flags)
    }
}

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult.extractResult(intent)?.let(ActivityTracker::onResult)
        }
    }
}
