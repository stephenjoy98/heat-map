package io.github.stephenjoy98.heatmap.data

import android.content.Context

/** A Semantic Versioning (https://semver.org) version number: MAJOR.MINOR.PATCH. */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""(\d+)\.(\d+)\.(\d+)(?:[-+].*)?""")

        /** Parses "2.11.0" (ignoring any pre-release or build suffix); null if it isn't a SemVer version. */
        fun parse(text: String?): SemVer? {
            val m = pattern.matchEntire(text?.trim() ?: return null) ?: return null
            val (major, minor, patch) = m.destructured
            return SemVer(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}

/** One version's entry in the changelog: its sections ("Added", "Fixed", …) and their items. */
class Release(val version: SemVer, val date: String?, val sections: List<Section>) {
    class Section(val title: String, val items: List<String>)
}

/**
 * Reads CHANGELOG.md, which the build bundles into the app's assets. The file follows Keep a Changelog:
 * `## [x.y.z] - date` per release, `### Added` style sections, and `- ` items.
 */
object Changelog {
    private const val ASSET = "CHANGELOG.md"
    private val releaseHeader = Regex("""^## \[([^\]]+)](?:\s*-\s*(.+))?$""")

    /** All released versions, newest first. */
    fun load(context: Context): List<Release> =
        runCatching { context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())

    fun parse(text: String): List<Release> {
        val releases = mutableListOf<Release>()
        var version: SemVer? = null
        var date: String? = null
        val sections = mutableListOf<Release.Section>()
        var title: String? = null
        val items = mutableListOf<String>()

        fun closeSection() {
            if (title != null && items.isNotEmpty()) sections += Release.Section(title!!, items.toList())
            title = null
            items.clear()
        }

        fun closeRelease() {
            closeSection()
            version?.let { releases += Release(it, date, sections.toList()) }
            version = null
            sections.clear()
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val header = releaseHeader.matchEntire(line)
            when {
                header != null -> {
                    closeRelease()
                    // "Unreleased" isn't a version, so its notes are skipped.
                    version = SemVer.parse(header.groupValues[1])
                    date = header.groupValues[2].ifBlank { null }
                }
                line.startsWith("## ") -> closeRelease()
                line.startsWith("### ") -> {
                    closeSection()
                    title = line.removePrefix("### ").trim()
                }
                line.startsWith("- ") && title != null -> items += clean(line.removePrefix("- "))
                // A wrapped item continues on an indented line.
                line.startsWith("  ") && line.isNotBlank() && items.isNotEmpty() ->
                    items[items.lastIndex] = items.last() + " " + clean(line.trim())
            }
        }
        closeRelease()
        return releases.sortedByDescending { it.version }
    }

    private fun clean(text: String) = text.replace("**", "").replace("`", "").trim()

    /**
     * The releases to show as "What's new" when updating from [lastSeen] to [current]: everything newer than
     * [lastSeen], up to [current]. With no known previous version (updating from before versions were
     * tracked), just the current release.
     */
    fun whatsNew(all: List<Release>, lastSeen: String?, current: String): List<Release> {
        val now = SemVer.parse(current) ?: return emptyList()
        val before = SemVer.parse(lastSeen)
        return when {
            before == null -> all.filter { it.version == now }
            before >= now -> emptyList()
            else -> all.filter { it.version > before && it.version <= now }
        }
    }
}
