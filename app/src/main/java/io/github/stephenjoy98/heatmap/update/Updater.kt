package io.github.stephenjoy98.heatmap.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.SemVer
import io.github.stephenjoy98.heatmap.data.UpdateFrequency
import io.github.stephenjoy98.heatmap.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the app's GitHub releases: each push to main publishes a release (vX.Y.Z) with that version's
 * changelog and APK. A release is newer if its version is higher, or if it's the same version rebuilt from a
 * newer commit (the commit is noted in the release text). The APK is downloaded into the app's cache and
 * installed with Android's PackageInstaller. The first update needs a tap to confirm; once the app has
 * installed itself, Android 12 and later let later updates install without asking.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/stephenjoy98/heat-map/releases/latest"
    const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 7
    private const val DOWNLOAD_ATTEMPTS = 4
    private const val RETRY_DELAY_MS = 30_000L
    private val buildMarker = Regex("""<!--\s*build:\s*([0-9a-f]{7,40})\s*-->""")

    class Release(
        val version: SemVer,
        val build: String?,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
        val pageUrl: String,
    ) {
        val label: String get() = version.toString()
    }

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        class Available(val release: Release) : State
        class Downloading(val release: Release, val fraction: Float) : State
        /** Downloaded, waiting to be installed (for instance until you leave the app). */
        class Ready(val release: Release, val file: File) : State
        class Installing(val release: Release) : State
        class Failed(val message: String, val release: Release? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** When the last check finished, in ms (0 = never in this run of the app). */
    private val _lastChecked = MutableStateFlow(0L)
    val lastChecked: StateFlow<Long> = _lastChecked

    /** Set by MainActivity while it's on screen, so an update never closes the app under you. */
    @Volatile var inForeground = false

    private val lock = Mutex()

    val currentVersion: SemVer? get() = SemVer.parse(BuildConfig.VERSION_NAME)

    /**
     * Asks GitHub for the latest release; returns it if it's newer than this app (and, unless [manual], one the
     * chosen update frequency wants), else null.
     */
    suspend fun check(context: Context, manual: Boolean = false): Release? = withContext(Dispatchers.IO) {
        // Don't knock a finished download or an install in progress back to "available".
        val before = _state.value
        val busy = before is State.Downloading || before is State.Ready || before is State.Installing
        if (!busy) _state.value = State.Checking
        try {
            val release = fetchLatest()
            _lastChecked.value = System.currentTimeMillis()
            val newer = release?.takeIf { isNewer(it, followFrequency = !manual) }
            if (!busy) _state.value = if (newer != null) State.Available(newer) else State.UpToDate
            newer
        } catch (e: Exception) {
            if (!busy) _state.value = State.Failed("Couldn't check for updates: ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }

    private fun fetchLatest(): Release? {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "LocationTicker/${BuildConfig.VERSION_NAME}")
        try {
            if (conn.responseCode == 404) return null // No releases yet.
            if (conn.responseCode != 200) throw IllegalStateException("GitHub answered ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = SemVer.parse(json.optString("tag_name").removePrefix("v")) ?: return null
            val body = json.optString("body")
            // The release APK, or the debug one when this is a debug build (both are signed with the same key).
            val wanted = if (BuildConfig.DEBUG) "location-ticker-$version-debug.apk" else "location-ticker-$version.apk"
            val assets = json.optJSONArray("assets") ?: return null
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == wanted) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                }
            }
            return Release(
                version = version,
                build = buildMarker.find(body)?.groupValues?.get(1),
                // The changelog is wrapped at 110 characters; join each bullet's lines back up for the screen.
                notes = body.substringBefore("\n---").replace(buildMarker, "").trim().replace(Regex("""\r?\n {2,}"""), " "),
                apkUrl = url ?: return null,
                apkSize = size,
                pageUrl = json.optString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    fun isNewer(r: Release, followFrequency: Boolean = true): Boolean {
        val mine = currentVersion ?: return false
        if (followFrequency && Prefs.updateFrequency.value == UpdateFrequency.FEATURES) {
            // New features only: a higher minor or major version; fixes alone wait for the next one.
            return r.version.major > mine.major || (r.version.major == mine.major && r.version.minor > mine.minor)
        }
        return when {
            r.version > mine -> true
            r.version < mine -> false
            // Same version: newer only if it was rebuilt from a different commit than this app.
            else -> r.build != null && BuildConfig.BUILD_COMMIT.isNotEmpty() && !r.build.startsWith(BuildConfig.BUILD_COMMIT) &&
                !BuildConfig.BUILD_COMMIT.startsWith(r.build)
        }
    }

    /** Whether downloads should wait for Wi-Fi (or another unmetered network) right now. */
    fun onMeteredNetwork(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true

    private class NotFound : Exception("not found")

    /**
     * Downloads [r] (with progress in [state]) and leaves it ready to install. Each push to GitHub replaces the
     * release's APK, and while that happens the file briefly isn't there (404). So the latest release details
     * are fetched first, and a missing file is tried again a few times, a little later, with fresh details.
     */
    suspend fun download(context: Context, r: Release): File? = lock.withLock {
        withContext(Dispatchers.IO) {
            var release = freshen(r)
            repeat(DOWNLOAD_ATTEMPTS) { attempt ->
                try {
                    return@withContext fetchApk(context, release)
                } catch (e: NotFound) {
                    if (attempt == DOWNLOAD_ATTEMPTS - 1) {
                        _state.value = State.Failed(
                            "Version ${release.label} is still being published on GitHub. It'll be tried again soon, or tap Try again in a minute.",
                            release,
                        )
                        return@withContext null
                    }
                    _state.value = State.Downloading(release, 0f)
                    delay(RETRY_DELAY_MS)
                    release = freshen(release)
                } catch (e: Exception) {
                    _state.value = State.Failed("Couldn't download ${release.label}: ${e.message ?: e.javaClass.simpleName}", release)
                    return@withContext null
                }
            }
            null
        }
    }

    /** The latest details for [r]'s version (a new APK link and size after a rebuild), or [r] if they can't be had. */
    private fun freshen(r: Release): Release =
        runCatching { fetchLatest() }.getOrNull()?.takeIf { it.version >= r.version } ?: r

    private fun fetchApk(context: Context, r: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "location-ticker-${r.version}-${r.build ?: "release"}.apk")
        // Already downloaded (say, the install was cancelled): no need to fetch it again.
        if (r.apkSize > 0 && file.length() == r.apkSize) {
            _state.value = State.Ready(r, file)
            return file
        }
        dir.listFiles()?.forEach { if (it != file) it.delete() } // Older downloads.
        _state.value = State.Downloading(r, 0f)
        try {
            val conn = URL(r.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "LocationTicker/${BuildConfig.VERSION_NAME}")
            try {
                if (conn.responseCode == 404) throw NotFound()
                if (conn.responseCode != 200) throw IllegalStateException("GitHub answered ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.apkSize
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var shown = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else 0
                            if (pct != shown) {
                                shown = pct
                                _state.value = State.Downloading(r, pct / 100f)
                            }
                        }
                    }
                }
                // A different size means the APK was replaced mid-download: fetch it again.
                if (r.apkSize > 0 && file.length() != r.apkSize) throw NotFound()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        _state.value = State.Ready(r, file)
        return file
    }

    /** Hands the downloaded APK to Android's installer. The app closes and updates once it's installed. */
    suspend fun install(context: Context, r: Release, file: File) = withContext(Dispatchers.IO) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("location-ticker.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallReceiver::class.java).putExtra(InstallReceiver.EXTRA_VERSION, r.label)
                // The installer adds its result to the intent, so it has to stay mutable.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
            _state.value = State.Installing(r)
        } catch (e: Exception) {
            _state.value = State.Failed("Couldn't install ${r.label}: ${e.message ?: e.javaClass.simpleName}", r)
        }
    }

    /** Download and install [r]; with [whenAway], a finished download waits until the app is in the background. */
    suspend fun downloadAndInstall(context: Context, r: Release, whenAway: Boolean) {
        val file = download(context, r) ?: return
        if (whenAway && inForeground) return
        install(context, r, file)
    }

    /** Called when the app goes into the background: installs an update that was waiting for that. */
    suspend fun installIfWaiting(context: Context) {
        val ready = _state.value as? State.Ready ?: return
        if (ready.file.exists()) install(context, ready.release, ready.file)
    }

    internal fun failed(message: String) {
        val r = (_state.value as? State.Installing)?.release
        _state.value = State.Failed(message, r)
    }

    /** A notification about updates; tapping it opens [tap], or the app. */
    fun notify(context: Context, title: String, text: String, tap: PendingIntent? = null) {
        val open = tap ?: PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ticker)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n) }
    }
}
