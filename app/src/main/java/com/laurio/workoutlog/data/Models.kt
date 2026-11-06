package com.laurio.workoutlog.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the app stores. Saved as one JSON file (see [Store]) and in backups.
 * When adding a field, give it a default and read it with a fallback, so older files keep loading.
 */

/** A body area such as Chest or Mobility. [target] = how many days between sessions he wants. */
data class Area(val id: String, val label: String, val target: Int, val kind: String) {
    fun toJson() = JSONObject().put("id", id).put("label", label).put("target", target).put("kind", kind)

    companion object {
        fun fromJson(o: JSONObject) = Area(
            id = o.getString("id"),
            label = o.optString("label", o.getString("id")),
            target = o.optInt("target", 4),
            kind = o.optString("kind", "strength"),
        )
    }
}

/**
 * A movement. [areas] lists the main area first, then areas it also works (partial credit).
 * [hidden] = removed from the lists but kept because history uses it.
 */
data class Exercise(
    val id: String,
    val name: String,
    val areas: List<String>,
    val equip: String = "",
    val hidden: Boolean = false,
    /** What gets logged for it, from [Measure.ALL], in that order. */
    val measures: List<String> = Measure.DEFAULT,
) {
    val mainArea: String get() = areas.firstOrNull() ?: ""

    /** Cardio-type time is entered in minutes; holds and hangs in seconds. */
    val timeInMinutes: Boolean
        get() = mainArea == "conditioning" || measures.any { it == Measure.SPEED || it == Measure.INCLINE || it == Measure.RESISTANCE }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("areas", JSONArray(areas)).put("equip", equip).put("measures", JSONArray(measures))
        .apply { if (hidden) put("hidden", true) }

    companion object {
        fun fromJson(o: JSONObject) = Exercise(
            id = o.getString("id"),
            name = o.optString("name", o.getString("id")),
            areas = o.optJSONArray("areas").strings(),
            equip = o.optString("equip", ""),
            hidden = o.optBoolean("hidden", false),
            measures = o.optJSONArray("measures").strings().filter { it in Measure.ALL }.ifEmpty { Measure.DEFAULT },
        )
    }
}

/** The things a movement can measure. Order here is the order the fields appear in. */
object Measure {
    const val SETS = "sets"
    const val REPS = "reps"
    const val WEIGHT = "weight"
    const val TIME = "time"
    const val BAND = "band"
    const val RESISTANCE = "resistance"
    const val SPEED = "speed"
    const val INCLINE = "incline"
    val ALL = listOf(SETS, REPS, WEIGHT, TIME, BAND, RESISTANCE, SPEED, INCLINE)
    val DEFAULT = listOf(SETS, REPS, WEIGHT)

    fun label(m: String, timeInMinutes: Boolean = false): String = when (m) {
        SETS -> "Sets"
        REPS -> "Reps"
        WEIGHT -> "Weight (lb)"
        TIME -> if (timeInMinutes) "Min" else "Sec"
        BAND -> "Band"
        RESISTANCE -> "Level"
        SPEED -> "Speed (mph)"
        INCLINE -> "Incline (%)"
        else -> m
    }

    /** Short name for the Exercises list and dialog. */
    fun name(m: String): String = when (m) {
        WEIGHT -> "Weight"
        TIME -> "Time"
        RESISTANCE -> "Resistance"
        SPEED -> "Speed"
        INCLINE -> "Incline"
        else -> label(m)
    }
}

/**
 * One line of a movement, e.g. 4 sets of 12 reps at 40 lb. Any part can be missing.
 * [time] is in seconds; [band] is free text (color or strength); [resistance] is the machine level.
 */
data class SetGroup(
    val sets: Int? = null,
    val reps: Int? = null,
    val weight: Double? = null,
    val time: Int? = null,
    val band: String? = null,
    val resistance: Double? = null,
    val speed: Double? = null,
    val incline: Double? = null,
) {
    val isEmpty: Boolean
        get() = sets == null && reps == null && weight == null && time == null &&
            band.isNullOrBlank() && resistance == null && speed == null && incline == null

    fun toJson(): JSONObject = JSONObject().apply {
        sets?.let { put("sets", it) }
        reps?.let { put("reps", it) }
        weight?.let { put("weight", it) }
        time?.let { put("time", it) }
        band?.takeIf { it.isNotBlank() }?.let { put("band", it) }
        resistance?.let { put("resistance", it) }
        speed?.let { put("speed", it) }
        incline?.let { put("incline", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = SetGroup(
            sets = if (o.has("sets")) o.optInt("sets") else null,
            reps = if (o.has("reps")) o.optInt("reps") else null,
            weight = if (o.has("weight")) o.optDouble("weight") else null,
            time = if (o.has("time")) o.optInt("time") else null,
            band = if (o.has("band")) o.optString("band", "") else null,
            resistance = if (o.has("resistance")) o.optDouble("resistance") else null,
            speed = if (o.has("speed")) o.optDouble("speed") else null,
            incline = if (o.has("incline")) o.optDouble("incline") else null,
        )
    }
}

/** A movement done on a day: its set/rep/weight lines plus a free note ("on Bosu", "30 min"). */
data class Item(val ex: String, val groups: List<SetGroup> = emptyList(), val note: String = "") {
    fun toJson(): JSONObject = JSONObject().put("ex", ex)
        .put("groups", JSONArray().apply { groups.forEach { put(it.toJson()) } })
        .put("note", note)

    companion object {
        fun fromJson(o: JSONObject): Item {
            val arr = o.optJSONArray("groups")
            val groups = if (arr == null) emptyList() else (0 until arr.length()).map { SetGroup.fromJson(arr.getJSONObject(it)) }
            // Older/web data kept everything as text in "detail".
            val note = o.optString("note", "").ifBlank { o.optString("detail", "") }
            return Item(o.getString("ex"), groups, note)
        }
    }
}

/**
 * One day of training ([date] = yyyy-MM-dd). [areas] = areas logged without specific movements
 * (old entries like "upper"). [raw] = the original spreadsheet text, [tag] = trainer/injury/activity.
 */
data class Session(
    val date: String,
    val items: List<Item> = emptyList(),
    val areas: List<String> = emptyList(),
    val raw: String = "",
    val tag: String = "",
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("date", date)
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        .put("areas", JSONArray(areas)).put("raw", raw).put("tag", tag).put("note", note)

    companion object {
        fun fromJson(o: JSONObject): Session {
            val arr = o.optJSONArray("items")
            return Session(
                date = o.getString("date"),
                items = if (arr == null) emptyList() else (0 until arr.length()).map { Item.fromJson(arr.getJSONObject(it)) },
                areas = o.optJSONArray("areas").strings(),
                raw = o.optString("raw", ""),
                tag = o.optString("tag", ""),
                note = o.optString("note", ""),
            )
        }
    }
}

/**
 * [catalogVersion] = which built-in exercise list this data has been updated to (see Store.upgrade).
 */
data class AppData(
    val areas: List<Area>,
    val exercises: List<Exercise>,
    val sessions: List<Session>,
    val catalogVersion: Int = 1,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("catalogVersion", catalogVersion)
        .put("areas", JSONArray().apply { areas.forEach { put(it.toJson()) } })
        .put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })
        .put("sessions", JSONArray().apply { sessions.sortedBy { it.date }.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): AppData {
            fun <T> list(key: String, f: (JSONObject) -> T): List<T> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { f(arr.getJSONObject(it)) }
            }
            val areas = list("areas") { Area.fromJson(it) }
            require(areas.isNotEmpty()) { "No body areas in this file" }
            return AppData(
                areas = areas,
                exercises = list("exercises") { Exercise.fromJson(it) },
                sessions = list("sessions") { Session.fromJson(it) }.sortedByDescending { it.date },
                catalogVersion = o.optInt("catalogVersion", 1),
            )
        }
    }
}

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }
