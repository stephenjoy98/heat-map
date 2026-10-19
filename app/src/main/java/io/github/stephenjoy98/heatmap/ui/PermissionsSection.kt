package io.github.stephenjoy98.heatmap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import io.github.stephenjoy98.heatmap.review.HealthSteps
import io.github.stephenjoy98.heatmap.tracking.ActivityTracker
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService

// Health Connect's own settings screen (its permissions live there).
private const val HEALTH_CONNECT_SETTINGS = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"

/** The app's page in Android's settings, where any permission can be changed. */
private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** After a request comes back refused without Android having asked, the only place left is Settings. */
private fun settingsIfBlocked(context: Context, permission: String, granted: Boolean) {
    val activity = context.findActivity() ?: return
    if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) openAppSettings(context)
}

/**
 * Settings → Permissions: everything the app can be allowed to do, whether it's allowed, and a tap to change
 * it. Android's own dialog opens where one exists (to grant a permission, stop battery optimization or connect
 * Health Connect); otherwise, including to take a permission back, the app's page in Android's settings opens.
 */
@Composable
fun PermissionsSection(resumeTick: Int, onNeedLocationFirst: () -> Unit) {
    val context = LocalContext.current
    // Bumped after each request so statuses refresh straight away; resumes refresh them too.
    var tick by remember { mutableIntStateOf(0) }
    val refresh = resumeTick to tick

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val hasLocation = remember(refresh) { LocationTickerService.hasLocationPermission(context) }
    val hasPrecise = remember(refresh) { granted(Manifest.permission.ACCESS_FINE_LOCATION) }
    val hasBackground = remember(refresh) { LocationTickerService.hasBackgroundPermission(context) }
    val hasActivity = remember(refresh) { ActivityTracker.hasPermission(context) }
    val notificationsOn = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val ignoresBattery = remember(refresh) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val health = remember { HealthSteps.availability(context) }
    val healthGranted by produceState(false, refresh) {
        value = runCatching { HealthSteps.hasPermission(context) }.getOrDefault(false)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        tick++
        settingsIfBlocked(context, Manifest.permission.ACCESS_FINE_LOCATION, result.values.any { it })
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        settingsIfBlocked(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION, ok)
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        settingsIfBlocked(context, Manifest.permission.ACTIVITY_RECOGNITION, ok)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settingsIfBlocked(context, Manifest.permission.POST_NOTIFICATIONS, ok)
        }
    }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        tick++
    }

    Section("Permissions") {
        PermissionRow(
            title = "Location",
            subtitle = when {
                hasPrecise -> "Precise location, used to record your dots"
                hasLocation -> "Approximate only: allow precise location for accurate routes"
                else -> "Needed to record where you go"
            },
            allowed = hasPrecise,
        ) {
            if (hasPrecise) {
                openAppSettings(context)
            } else {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ItemDivider()
            PermissionRow(
                title = "Location all the time",
                subtitle = "Lets recording carry on, and resume after a restart, while the app is closed",
                allowed = hasBackground,
            ) {
                when {
                    hasBackground -> openAppSettings(context)
                    // Android only offers "all the time" once location is allowed while using the app.
                    !hasLocation -> onNeedLocationFirst()
                    else -> backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            ItemDivider()
            PermissionRow(
                title = "Physical activity",
                subtitle = "Motion sensors tell walking, cycling and vehicles apart, and set the automatic interval",
                allowed = hasActivity,
            ) {
                if (hasActivity) openAppSettings(context) else activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        ItemDivider()
        PermissionRow(
            title = "Notifications",
            subtitle = "The recording notification, and questions about your trips",
            allowed = notificationsOn,
        ) {
            if (!notificationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Notification settings have their own page.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { openAppSettings(context) }
            }
        }
        ItemDivider()
        PermissionRow(
            title = "Unrestricted battery",
            subtitle = "Stops Android from pausing recording to save battery",
            allowed = ignoresBattery,
        ) {
            if (ignoresBattery) {
                openAppSettings(context)
            } else {
                @SuppressLint("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
            }
        }
        ItemDivider()
        PermissionRow(
            title = "Health Connect",
            subtitle = when (health) {
                HealthSteps.Availability.UNSUPPORTED -> "Not available on this phone"
                HealthSteps.Availability.NEEDS_INSTALL -> "Install or update Health Connect to show your steps"
                HealthSteps.Availability.AVAILABLE -> "Reads your daily steps for Insights"
            },
            allowed = if (health == HealthSteps.Availability.UNSUPPORTED) null else healthGranted,
        ) {
            when {
                health == HealthSteps.Availability.NEEDS_INSTALL -> runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                health != HealthSteps.Availability.AVAILABLE -> Unit
                // Health Connect manages its own permissions.
                healthGranted -> runCatching {
                    context.startActivity(Intent(HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { openAppSettings(context) }
                else -> healthLauncher.launch(HealthSteps.permissions)
            }
        }
    }
}

/** A permission: what it's for, whether it's allowed, and a tap to change it. [allowed] null means unavailable. */
@Composable
private fun PermissionRow(title: String, subtitle: String, allowed: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = allowed != null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            when (allowed) {
                true -> "Allowed"
                false -> "Not allowed"
                null -> "Unavailable"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when (allowed) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
