package io.github.stephenjoy98.heatmap.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * How a stretch of the history was travelled: one of the built-in modes, or one added in Settings → Travel
 * modes. Ids are stored in the database; never reuse one. Modes are equal when their ids are.
 *
 * The lists of modes are Compose state, so screens update as soon as a mode is added, renamed or deleted.
 */
class TravelMode private constructor(val id: Int, val name: String, label: String, defaultColor: Long, kind: Kind) {

    /** What a mode travels like: decides road matching and whether it counts as a vehicle. */
    enum class Kind(val label: String) {
        FOOT("On foot"),
        BIKE("Bike"),
        CAR("Car"),
        PUBLIC("Public transport"),
        OTHER("Other");

        companion object {
            /** The kinds a mode you add can have. */
            val choosable = listOf(FOOT, BIKE, CAR, PUBLIC)
        }
    }

    var label by mutableStateOf(label)
        private set

    /** The colour the mode has unless one is picked in Settings; for modes you add, the colour you gave it. */
    var defaultColor by mutableLongStateOf(defaultColor)
        private set

    var kind by mutableStateOf(kind)
        private set

    /** The colour chosen in Settings, or the default. */
    val color: Long get() = ModeColors.custom[this] ?: defaultColor

    /** True for the modes the app comes with; false for ones added in Settings. */
    val builtIn: Boolean get() = id < FIRST_CUSTOM_ID

    /** Walking, cycling and driving are what trips are detected as, and Other is the fallback: they always stay. */
    val deletable: Boolean get() = id !in REQUIRED

    override fun equals(other: Any?) = other is TravelMode && other.id == id
    override fun hashCode() = id
    override fun toString() = name

    companion object {
        const val FIRST_CUSTOM_ID = 100

        val UNKNOWN = TravelMode(0, "UNKNOWN", "Other", 0xFF9E9E9E, Kind.OTHER)
        val WALK = TravelMode(1, "WALK", "Walking", 0xFF66BB6A, Kind.FOOT)
        val CYCLE = TravelMode(2, "CYCLE", "Cycling", 0xFF29B6F6, Kind.BIKE)
        val DRIVE = TravelMode(3, "DRIVE", "Driving", 0xFFFF7A1A, Kind.CAR)
        val RIDESHARE = TravelMode(4, "RIDESHARE", "Car share", 0xFFAB47BC, Kind.CAR)
        val TRANSIT = TravelMode(5, "TRANSIT", "Transit", 0xFFFFCA28, Kind.PUBLIC)

        private val REQUIRED = setOf(UNKNOWN.id, WALK.id, CYCLE.id, DRIVE.id)
        private val builtIns = listOf(UNKNOWN, WALK, CYCLE, DRIVE, RIDESHARE, TRANSIT)

        // Modes added in Settings, and built-in modes that were deleted (they can be added back).
        internal val added = mutableStateListOf<TravelMode>()
        internal val deletedBuiltIns = mutableStateListOf<Int>()
        internal var nextId by mutableIntStateOf(FIRST_CUSTOM_ID)

        /** Every mode in use: the built-in ones that weren't deleted, then the ones added, Other first. */
        val entries: List<TravelMode> get() = builtIns.filter { it.id !in deletedBuiltIns } + added

        /** The modes a user can pick for a trip or filter the map by (all but Other). */
        val choosable: List<TravelMode> get() = entries.filter { it != UNKNOWN }

        val vehicles: Set<TravelMode> get() = entries.filter { it.kind == Kind.CAR || it.kind == Kind.PUBLIC }.toSet()

        /** Built-in modes that were deleted and can be added back. */
        val removedBuiltIns: List<TravelMode> get() = builtIns.filter { it.id in deletedBuiltIns }

        /** The mode with this id, or Other if it no longer exists. */
        fun fromId(id: Int): TravelMode =
            builtIns.firstOrNull { it.id == id }?.takeIf { it.id !in deletedBuiltIns }
                ?: added.firstOrNull { it.id == id }
                ?: UNKNOWN

        internal fun create(label: String, color: Long, kind: Kind): TravelMode {
            val id = nextId++
            return TravelMode(id, "CUSTOM_$id", label, color, kind).also { added += it }
        }

        internal fun edit(mode: TravelMode, label: String, color: Long, kind: Kind) {
            if (mode.builtIn) return
            mode.label = label
            mode.defaultColor = color
            mode.kind = kind
        }

        /** "[{"id":100,"label":"Ferry","color":4280391411,"kind":"PUBLIC"}]" */
        internal fun encodeAdded(): String = JSONArray().apply {
            added.forEach { m ->
                put(JSONObject().put("id", m.id).put("label", m.label).put("color", m.defaultColor).put("kind", m.kind.name))
            }
        }.toString()

        /** Adds modes from a backup that this phone doesn't have yet. Returns true if anything changed. */
        internal fun merge(json: String?, next: Int): Boolean {
            val before = added.size
            runCatching {
                val a = JSONArray(json ?: "[]")
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val id = o.getInt("id")
                    if (added.any { it.id == id }) continue
                    val kind = Kind.entries.firstOrNull { it.name == o.optString("kind") } ?: Kind.CAR
                    added += TravelMode(id, "CUSTOM_$id", o.getString("label"), o.getLong("color"), kind)
                }
            }
            nextId = maxOf(nextId, next, (added.maxOfOrNull { it.id } ?: (FIRST_CUSTOM_ID - 1)) + 1)
            return before != added.size
        }

        internal fun loadAdded(json: String?, deleted: Collection<Int>, next: Int) {
            added.clear()
            runCatching {
                val a = JSONArray(json ?: "[]")
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val id = o.getInt("id")
                    val kind = Kind.entries.firstOrNull { it.name == o.optString("kind") } ?: Kind.CAR
                    added += TravelMode(id, "CUSTOM_$id", o.getString("label"), o.getLong("color"), kind)
                }
            }
            deletedBuiltIns.clear()
            deletedBuiltIns.addAll(deleted.filter { it !in REQUIRED })
            nextId = maxOf(next, (added.maxOfOrNull { it.id } ?: (FIRST_CUSTOM_ID - 1)) + 1)
        }
    }
}

/**
 * Colours the user picked for the built-in travel modes. Held in Compose state, so everything on screen that
 * shows a mode's colour updates as soon as one changes; [version] changes too, for things drawn ahead of time
 * (it also changes when modes are added, edited or deleted).
 */
object ModeColors {
    internal val custom = mutableStateMapOf<TravelMode, Long>()

    var version by mutableIntStateOf(0)
        private set

    val anyCustom: Boolean get() = custom.isNotEmpty()

    internal fun bump() {
        version++
    }

    internal fun replace(colors: Map<TravelMode, Long>) {
        custom.clear()
        custom.putAll(colors)
        version++
    }

    /** "1=FF66BB6A,3=FF0000FF": mode ids to ARGB. */
    fun encode(colors: Map<TravelMode, Long>): String =
        colors.entries.joinToString(",") { (m, c) -> "${m.id}=${java.lang.Long.toHexString(c and 0xFFFFFFFFL)}" }

    fun decode(s: String?): Map<TravelMode, Long> = s.orEmpty().split(',').mapNotNull { part ->
        val (id, hex) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        val mode = id.toIntOrNull()?.let { TravelMode.fromId(it) }?.takeIf { it.builtIn } ?: return@mapNotNull null
        val color = hex.toLongOrNull(16) ?: return@mapNotNull null
        mode to (color or 0xFF000000L)
    }.toMap()
}
