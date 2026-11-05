package com.laurio.workoutlog.logic

import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.data.Area
import com.laurio.workoutlog.data.Exercise
import com.laurio.workoutlog.data.SetGroup
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Status(val label: String) { FRESH("Fresh"), SOON("Soon"), DUE("Due"), OVERDUE("Overdue") }

data class LastDone(val date: String, val groups: List<SetGroup>, val note: String)

/**
 * How due an area is. [direct] = days since a movement whose main area this is (or an area-only log),
 * [secondary] = days since a movement that also works it. [days] is the effective figure:
 * secondary work counts as if it were half a target interval older.
 */
data class AreaStatus(
    val area: Area,
    val days: Int?,
    val direct: Int?,
    val secondary: Int?,
    val ratio: Double,
    val status: Status,
)

data class Movement(val exercise: Exercise, val last: LastDone?)

/** Everything the screens derive from the log: last time per movement and how due each area is. */
class Stats(val data: AppData, val today: LocalDate = LocalDate.now()) {
    val exById: Map<String, Exercise> = data.exercises.associateBy { it.id }
    val lastEx = HashMap<String, LastDone>()
    val countEx = HashMap<String, Int>()
    val areas: List<AreaStatus>

    init {
        val lastDirect = HashMap<String, String>()
        val lastSecondary = HashMap<String, String>()
        for (s in data.sessions) {
            for (item in s.items) {
                countEx[item.ex] = (countEx[item.ex] ?: 0) + 1
                val prev = lastEx[item.ex]
                if (prev == null || prev.date < s.date) lastEx[item.ex] = LastDone(s.date, item.groups, item.note)
                val e = exById[item.ex] ?: continue
                e.areas.forEachIndexed { i, a ->
                    val m = if (i == 0) lastDirect else lastSecondary
                    if ((m[a] ?: "") < s.date) m[a] = s.date
                }
            }
            for (a in s.areas) if ((lastDirect[a] ?: "") < s.date) lastDirect[a] = s.date
        }
        areas = data.areas.map { a ->
            val target = a.target.coerceAtLeast(1)
            val direct = lastDirect[a.id]?.let { daysAgo(it) }
            val secondary = lastSecondary[a.id]?.let { daysAgo(it) }
            val days = listOfNotNull(direct, secondary?.let { it + (target + 1) / 2 }).minOrNull()
            val ratio = if (days == null) 9.0 else days.toDouble() / target
            val status = when {
                days == null || ratio >= 2 -> Status.OVERDUE
                ratio >= 1 -> Status.DUE
                ratio >= 0.75 -> Status.SOON
                else -> Status.FRESH
            }
            AreaStatus(a, days, direct, secondary, ratio, status)
        }
    }

    fun daysAgo(date: String): Int = ChronoUnit.DAYS.between(LocalDate.parse(date), today).toInt()

    /**
     * What to work on: up to 3 strength areas at or past their target (skipping any worked
     * yesterday or today), topped up to 2 with ones coming due, plus conditioning/mobility when due.
     */
    fun focus(): List<AreaStatus> {
        val rested = { s: AreaStatus -> (s.days ?: 99) >= 2 }
        val strength = areas.filter { it.area.kind == "strength" }.sortedByDescending { it.ratio }
        val picked = strength.filter { it.ratio >= 1 && rested(it) }.take(3).toMutableList()
        for (s in strength) if (picked.size < 2 && s !in picked && s.ratio >= 0.75 && rested(s)) picked.add(s)
        val extras = areas.filter { it.area.kind != "strength" && it.ratio >= 1 }.sortedByDescending { it.ratio }
        return picked + extras
    }

    /** Movements whose main area is [areaId]: never-logged first, then the longest ago. */
    fun movements(areaId: String): List<Movement> = data.exercises
        .filter { !it.hidden && it.mainArea == areaId }
        .map { Movement(it, lastEx[it.id]) }
        .sortedWith(compareBy<Movement>({ it.last != null }, { it.last?.date ?: "" }, { it.exercise.name.lowercase() }))
}
