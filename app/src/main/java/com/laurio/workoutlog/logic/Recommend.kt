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
 * How due an area is. A day "resets" an area once its credits reach [RESET_CREDITS]: each movement
 * counts 1 for its main area and 0.5 for each area it also works; an area-only log ("upper") counts
 * as a full reset. [days] = days since the last reset (null = never). [partialDays]/[partialCredits]
 * describe a more recent day that didn't reach a reset, if there is one.
 */
data class AreaStatus(
    val area: Area,
    val days: Int?,
    val partialDays: Int?,
    val partialCredits: Double,
    val ratio: Double,
    val status: Status,
)

data class Movement(val exercise: Exercise, val last: LastDone?)

/** Credits a day needs in an area to reset its clock. */
const val RESET_CREDITS = 2.0
const val MAIN_CREDIT = 1.0
const val ALSO_CREDIT = 0.5

/** Everything the screens derive from the log: last time per movement and how due each area is. */
class Stats(val data: AppData, val today: LocalDate = LocalDate.now()) {
    val exById: Map<String, Exercise> = data.exercises.associateBy { it.id }
    val lastEx = HashMap<String, LastDone>()
    val countEx = HashMap<String, Int>()
    val areas: List<AreaStatus>

    init {
        val lastReset = HashMap<String, String>()
        val lastPartial = HashMap<String, Pair<String, Double>>()
        for (s in data.sessions) {
            for (item in s.items) {
                countEx[item.ex] = (countEx[item.ex] ?: 0) + 1
                val prev = lastEx[item.ex]
                if (prev == null || prev.date < s.date) lastEx[item.ex] = LastDone(s.date, item.groups, item.note)
            }
            for ((a, credit) in creditsFor(s)) {
                if (credit >= RESET_CREDITS) {
                    if ((lastReset[a] ?: "") < s.date) lastReset[a] = s.date
                } else if ((lastPartial[a]?.first ?: "") < s.date) {
                    lastPartial[a] = s.date to credit
                }
            }
        }
        areas = data.areas.map { a ->
            val target = a.target.coerceAtLeast(1)
            val reset = lastReset[a.id]
            val days = reset?.let { daysAgo(it) }
            val partial = lastPartial[a.id]?.takeIf { reset == null || it.first > reset }
            val ratio = if (days == null) 9.0 else days.toDouble() / target
            val status = when {
                days == null || ratio >= 2 -> Status.OVERDUE
                ratio >= 1 -> Status.DUE
                ratio >= 0.75 -> Status.SOON
                else -> Status.FRESH
            }
            AreaStatus(a, days, partial?.let { daysAgo(it.first) }, partial?.second ?: 0.0, ratio, status)
        }
    }

    /** Credits per area for one day. The same movement logged twice that day counts once. */
    fun creditsFor(s: com.laurio.workoutlog.data.Session): Map<String, Double> {
        val credits = HashMap<String, Double>()
        for (id in s.items.map { it.ex }.distinct()) {
            val e = exById[id] ?: continue
            e.areas.distinct().forEachIndexed { i, a ->
                credits[a] = (credits[a] ?: 0.0) + if (i == 0) MAIN_CREDIT else ALSO_CREDIT
            }
        }
        for (a in s.areas) credits[a] = maxOf(credits[a] ?: 0.0, RESET_CREDITS)
        return credits
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

    /**
     * When nothing is due, every area is offered, longest since worked first, so the area he
     * did most recently ends up at the bottom.
     */
    fun allByRest(): List<AreaStatus> =
        areas.sortedWith(compareByDescending<AreaStatus> { it.days ?: Int.MAX_VALUE }.thenByDescending { it.ratio })

    /** His every-session routine (warmup etc.), in list order. Shown on Next whatever is due. */
    fun everySession(): List<Movement> = data.exercises
        .filter { !it.hidden && it.everySession && it.inSeason(today.monthValue) }
        .map { Movement(it, lastEx[it.id]) }

    /**
     * Movements whose main area is [areaId]: never-logged first, then the longest ago.
     * Pinned ones (slam balls) always come first. Every-session movements are left out because they
     * have their own card, and out-of-season ones (paddleboarding outside Jun-Sep) aren't offered.
     */
    fun movements(areaId: String): List<Movement> = data.exercises
        .filter { !it.hidden && !it.everySession && it.mainArea == areaId && it.inSeason(today.monthValue) }
        .map { Movement(it, lastEx[it.id]) }
        .sortedWith(
            compareBy<Movement>(
                { !it.exercise.pinned }, { it.last != null }, { it.last?.date ?: "" }, { it.exercise.name.lowercase() },
            ),
        )
}
