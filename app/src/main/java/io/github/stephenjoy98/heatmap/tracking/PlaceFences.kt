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
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.github.stephenjoy98.heatmap.data.SavedPlace

/**
 * Android geofences around saved places, as a backup for noticing you've left one: while settled at a place
 * recording slows to minutes, and activity recognition can miss a quiet departure. Android reports the exit
 * even between fixes, and recording speeds up straight away. Needs location access "all the time".
 */
object PlaceFences {
    /** Called (on the main thread) when Android reports leaving a saved place. */
    @Volatile
    var onExit: (() -> Unit)? = null

    private fun allowed(context: Context): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    @SuppressLint("MissingPermission") // Checked by allowed().
    fun register(context: Context, places: List<SavedPlace>) {
        val client = LocationServices.getGeofencingClient(context)
        runCatching { client.removeGeofences(pendingIntent(context)) }
        if (places.isEmpty() || !allowed(context)) return
        val fences = places.take(MAX_FENCES).map { p ->
            Geofence.Builder()
                .setRequestId(p.id.toString())
                .setCircularRegion(p.lat, p.lon, p.radiusM.toFloat().coerceAtLeast(MIN_RADIUS_M))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // Only report leaving, not where you are when this is set up.
            .addGeofences(fences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent(context)) }
    }

    fun unregister(context: Context) {
        runCatching { LocationServices.getGeofencingClient(context).removeGeofences(pendingIntent(context)) }
    }

    internal fun onEvent(event: GeofencingEvent) {
        if (!event.hasError() && event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            ContextCompat.getMainExecutor(appContext ?: return).execute { onExit?.invoke() }
        }
    }

    @Volatile
    internal var appContext: Context? = null

    private fun pendingIntent(context: Context): PendingIntent {
        appContext = context.applicationContext
        // Mutable: Play services adds the geofence event to this intent.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 8, Intent(context, PlaceFenceReceiver::class.java), flags)
    }

    // Android allows 100 geofences per app; geofences under ~100 m are unreliable.
    private const val MAX_FENCES = 100
    private const val MIN_RADIUS_M = 100f
}

class PlaceFenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlaceFences.appContext = context.applicationContext
        GeofencingEvent.fromIntent(intent)?.let(PlaceFences::onEvent)
    }
}
