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
 * modes. A mode can belong to another as a sub-mode (PublicTransit › Bus). Ids are stored in the database;
 * never reuse one. Modes are equal when their ids are.
 *
 * The lists of modes are Compose state, so screens update as soon as a mode is added, renamed or deleted.
 */
class TravelMode private constructor(
    val id: Int,
    val name: String,
    label: String,
    defaultColor: Long,
    kind: Kind,
    parentId: Int? = null,
) {

    /** What a mode travels like: decides road matching and whether it counts as a vehicle. */
    enum class Kind(val label: String) {
        FOOT("On foot"),
        BIKE("Bike"),
        CAR("Car"),
        PUBLIC("Bus"),
        TRACK("Train or boat"),
        OTHER("Other");

        companion object {
            /** The kinds a mode you add can have. */
            val choosable = listOf(FOOT, BIKE, CAR, PUBLIC, TRACK)
        }
    }

    var label by mutableStateOf(label)
        private set

    /** The colour the mode has unless one is picked in Settings; for modes you add, the colour you gave it. */
    var defaultColor by mutableLongStateOf(defaultColor)
        private set

    var kind by mutableStateOf(kind)
        private set

    /** The mode this is a sub-mode of, by id; null for a top-level mode. */
    var parentId by mutableStateOf(parentId)
        private set

    val parent: TravelMode? get() = parentId?.let { p -> entries.firstOrNull { it.id == p } }

    /** Sub-modes, in the order they were added. */
    val children: List<TravelMode> get() = entries.filter { it.parentId == id }

    /** This mode and its sub-modes. */
    val withChildren: List<TravelMode> get() = listOf(this) + children

    /** "PublicTransit · Bus" for a sub-mode, otherwise the label. */
    val fullLabel: String get() = parent?.let { "${it.label} · $label" } ?: label

    /** The colour chosen in Settings, or the default. */
    val color: Long get() = ModeColors.custom[this] ?: defaultColor

    /** True for the modes the app comes with; false for ones added in Settings. */
    val builtIn: Boolean get() = id < FIRST_CUSTOM_ID

    /** Walking, cycling and driving are what trips are detected as, and Other is the fallback: they always stay. */
    val deletable: Boolean get() = id !in REQUIRED

    val isVehicle: Boolean get() = kind == Kind.CAR || kind == Kind.PUBLIC || kind == Kind.TRACK

    override fun equals(other: Any?) = other is TravelMode && other.id == id
    override fun hashCode() = id
    override fun toString() = name

    companion object {
        const val FIRST_CUSTOM_ID = 100

        val UNKNOWN = TravelMode(0, "UNKNOWN", "Other", 0xFF9E9E9E, Kind.OTHER)
        val WALK = TravelMode(1, "WALK", "Walking", 0xFF66BB6A, Kind.FOOT)
        val CYCLE = TravelMode(2, "CYCLE", "Cycling", 0xFF29B6F6, Kind.BIKE)
        val DRIVE = TravelMode(3, "DRIVE", "Driving", 0xFFFF7A1A, Kind.CAR)
        val RIDESHARE = TravelMode(4, "RIDESHARE", "RideShare", 0xFFAB47BC, Kind.CAR)
        val TRANSIT = TravelMode(5, "TRANSIT", "PublicTransit", 0xFFFFCA28, Kind.PUBLIC)

        private val REQUIRED = setOf(UNKNOWN.id, WALK.id, CYCLE.id, DRIVE.id)
        private val builtIns = listOf(UNKNOWN, WALK, CYCLE, DRIVE, RIDESHARE, TRANSIT)
        private val builtInLabels = builtIns.associate { it.id to it.label }

        // Modes added in Settings, and built-in modes that were deleted (they can be added back).
        internal val added = mutableStateListOf<TravelMode>()
        internal val deletedBuiltIns = mutableStateListOf<Int>()
        internal var nextId by mutableIntStateOf(FIRST_CUSTOM_ID)

        /** Every mode in use: the built-in ones that weren't deleted, then the ones added, Other first. */
        val entries: List<TravelMode> get() = builtIns.filter { it.id !in deletedBuiltIns } + added

        /** The modes a user can pick for a trip or filter the map by (all but Other), sub-modes included. */
        val choosable: List<TravelMode> get() = entries.filter { it != UNKNOWN }

        /** Top-level modes other than Other, in order. */
        val topLevel: List<TravelMode> get() = entries.filter { it != UNKNOWN && it.parent == null }

        /** Every pickable mode with sub-modes right after their parent: for menus and lists. */
        val ordered: List<TravelMode> get() = topLevel.flatMap { it.withChildren }

        val vehicles: Set<TravelMode> get() = entries.filter { it.isVehicle }.toSet()

        /** Built-in modes that were deleted and can be added back. */
        val removedBuiltIns: List<TravelMode> get() = builtIns.filter { it.id in deletedBuiltIns }

        /** The mode with this id, or Other if it no longer exists. */
        fun fromId(id: Int): TravelMode =
            builtIns.firstOrNull { it.id == id }?.takeIf { it.id !in deletedBuiltIns }
                ?: added.firstOrNull { it.id == id }
                ?: UNKNOWN

        internal fun create(label: String, color: Long, kind: Kind, parentId: Int? = null): TravelMode {
            val id = nextId++
            return TravelMode(id, "CUSTOM_$id", label, color, kind, parentId).also { added += it }
        }

        /** Changes a mode: built-in modes can be renamed; modes you added can change everything. */
        internal fun edit(mode: TravelMode, label: String, color: Long, kind: Kind, parentId: Int?) {
            mode.label = label
            if (mode.builtIn) return
            mode.defaultColor = color
            mode.kind = kind
            // Only one level of sub-modes, and never under itself.
            mode.parentId = parentId?.takeIf { it != mode.id && fromId(it).parentId == null && mode.children.isEmpty() }
        }

        /** Built-in modes the user renamed: "4=Uber,5=Transit". */
        internal fun encodeLabels(): String =
            builtIns.filter { it.label != builtInLabels[it.id] }.joinToString(",") { "${it.id}=${it.label.replace(",", " ").replace("=", " ")}" }

        internal fun loadLabels(s: String?) {
            builtIns.forEach { it.label = builtInLabels.getValue(it.id) }
            s.orEmpty().split(',').forEach { part ->
                val (id, label) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
                builtIns.firstOrNull { it.id == id.toIntOrNull() }?.let { m -> if (label.isNotBlank()) m.label = label }
            }
        }

        /** "[{"id":100,"label":"Ferry","color":4280391411,"kind":"TRACK","parent":5}]" */
        internal fun encodeAdded(): String = JSONArray().apply {
            added.forEach { m ->
                put(
                    JSONObject().put("id", m.id).put("label", m.label).put("color", m.defaultColor).put("kind", m.kind.name)
                        .apply { m.parentId?.let { put("parent", it) } }
                )
            }
        }.toString()

        private fun decode(o: JSONObject): TravelMode {
            val id = o.getInt("id")
            val kind = Kind.entries.firstOrNull { it.name == o.optString("kind") } ?: Kind.CAR
            val parent = if (o.has("parent")) o.getInt("parent") else null
            return TravelMode(id, "CUSTOM_$id", o.getString("label"), o.getLong("color"), kind, parent)
        }

        /** Adds modes from a backup that this phone doesn't have yet. Returns true if anything changed. */
        internal fun merge(json: String?, next: Int): Boolean {
            val before = added.size
            runCatching {
                val a = JSONArray(json ?: "[]")
                for (i in 0 until a.length()) {
                    val m = decode(a.getJSONObject(i))
                    if (added.none { it.id == m.id }) added += m
                }
            }
            nextId = maxOf(nextId, next, (added.maxOfOrNull { it.id } ?: (FIRST_CUSTOM_ID - 1)) + 1)
            return before != added.size
        }

        internal fun loadAdded(json: String?, deleted: Collection<Int>, next: Int) {
            added.clear()
            runCatching {
                val a = JSONArray(json ?: "[]")
                for (i in 0 until a.length()) added += decode(a.getJSONObject(i))
            }
            deletedBuiltIns.clear()
            deletedBuiltIns.addAll(deleted.filter { it !in REQUIRED })
            nextId = maxOf(next, (added.maxOfOrNull { it.id } ?: (FIRST_CUSTOM_ID - 1)) + 1)
        }

        /**
         * The suggested modes, added once: CarPool, RideShare › Bike and Car, and PublicTransit › SkyTrain, Bus
         * and SeaBus. Modes with those names that already exist are kept as they are.
         */
        internal fun seedDefaults() {
            fun has(label: String) = entries.any { it.label.equals(label, ignoreCase = true) }
            if (!has("CarPool")) create("CarPool", 0xFF26A69A, Kind.CAR)
            if (RIDESHARE.id !in deletedBuiltIns) {
                if (!has("Bike")) create("Bike", 0xFFCE93D8, Kind.BIKE, RIDESHARE.id)
                if (RIDESHARE.children.none { it.label.equals("Car", ignoreCase = true) }) create("Car", 0xFF7B1FA2, Kind.CAR, RIDESHARE.id)
            }
            if (TRANSIT.id !in deletedBuiltIns) {
                if (!has("SkyTrain")) create("SkyTrain", 0xFF1E88E5, Kind.TRACK, TRANSIT.id)
                if (!has("Bus")) create("Bus", 0xFFFFA000, Kind.PUBLIC, TRANSIT.id)
                if (!has("SeaBus")) create("SeaBus", 0xFF00ACC1, Kind.TRACK, TRANSIT.id)
            }
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
