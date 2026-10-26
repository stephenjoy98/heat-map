package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.SemVer
import io.github.stephenjoy98.heatmap.update.Updater

/**
 * What's waiting to be looked at. Red dots stand for what a notification was about (a new version on
 * Settings › Updates, the changelog after an update) and lead from the account button to the place it's
 * waiting. "New" markers show on sections that gained or changed something since you last opened them.
 */
object Attention {
    const val PROFILE = "profile"
    const val FRIENDS = "friends"
    const val BACKUP = "backup"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val TIMELINE = "timeline"

    /** A Settings section's key, from its page name: "settings/updates" and so on. */
    fun settings(page: String) = "$SETTINGS/${page.lowercase()}"

    /**
     * The version that last added or changed something in each section. With each release, add or bump the
     * sections where you'd find what's new; their marker shows until the section is opened.
     */
    private val NEW_IN = mapOf(
        TIMELINE to "2.49.0",
        INSIGHTS to "2.31.0",
        settings("battery") to "2.32.0",
        settings("updates") to "2.33.0",
        settings("general") to "2.31.0",
    )

    // Goes up when something is marked seen, so markers drawn from it update.
    private var tick by mutableIntStateOf(0)

    /** Whether the section has a change you haven't opened it to see yet. */
    fun isNew(key: String): Boolean {
        tick
        val changed = SemVer.parse(NEW_IN[key]) ?: return false
        val current = SemVer.parse(BuildConfig.VERSION_NAME) ?: return false
        val seen = SemVer.parse(Prefs.sectionSeen(key) ?: Prefs.attentionBase) ?: return false
        return changed > seen && changed <= current
    }

    /** Whether the section, or any section inside it ("settings" covers "settings/updates"), has something new. */
    fun anyNew(prefix: String): Boolean = NEW_IN.keys.any { (it == prefix || it.startsWith("$prefix/")) && isNew(it) }

    /** Clears the section's marker; call when it's opened. */
    fun seen(key: String) {
        if (isNew(key)) {
            Prefs.setSectionSeen(key, BuildConfig.VERSION_NAME)
            tick++
        }
    }

    /** Clears the markers of every section inside [prefix] (for Settings shown as one list). */
    fun seenAll(prefix: String) = NEW_IN.keys.filter { it == prefix || it.startsWith("$prefix/") }.forEach(::seen)

    /** Whether the changelog of this version is still unread since the update. */
    fun changelogUnread(): Boolean {
        tick
        val read = SemVer.parse(Prefs.changelogRead) ?: return false
        val current = SemVer.parse(BuildConfig.VERSION_NAME) ?: return false
        return read < current
    }

    fun changelogSeen() {
        if (changelogUnread()) {
            Prefs.changelogRead = BuildConfig.VERSION_NAME
            tick++
        }
    }

    private fun Updater.Release.key() = "$label ${build.orEmpty()}"

    /** A new version that hasn't been looked at on Settings › Updates yet. */
    @Composable
    fun updateWaiting(): Boolean {
        val state by Updater.state.collectAsStateWithLifecycle()
        tick
        val release = when (val s = state) {
            is Updater.State.Available -> s.release
            is Updater.State.Ready -> s.release
            else -> null
        } ?: return false
        return Prefs.updateSeen != release.key()
    }

    /** Marks the version shown on Settings › Updates as looked at. */
    fun updateSeen() {
        val release = when (val s = Updater.state.value) {
            is Updater.State.Available -> s.release
            is Updater.State.Ready -> s.release
            else -> null
        } ?: return
        if (Prefs.updateSeen != release.key()) {
            Prefs.updateSeen = release.key()
            tick++
        }
    }

    /** Whether anything reached from the account button wants a look: a red dot on it. */
    @Composable
    fun accountWaiting(): Boolean {
        val update = updateWaiting()
        return update || changelogUnread() ||
            listOf(PROFILE, FRIENDS, BACKUP, INSIGHTS, SETTINGS, ABOUT).any { anyNew(it) }
    }
}

/** A small red dot: something a notification was about is waiting here. */
@Composable
fun AttentionDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape)
            .semantics { contentDescription = "Needs a look" },
    )
}

/** A "New" pill for a section that gained or changed something since you last opened it. */
@Composable
fun NewMarker(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))) {
        Text(
            "New",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
