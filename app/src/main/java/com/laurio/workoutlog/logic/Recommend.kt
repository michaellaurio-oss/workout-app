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
 * How due an area is. Each movement counts 1 credit for its main area and 0.5 for each area it also
 * works; an area-only log ("upper") counts as a full reset. Credits add up across days within the
 * area's every-N-days period ([Area.target]); the day they reach [Area.credits] resets the area, and
 * counting starts over from zero after it (so the same credits never count twice).
 * [days] = days since the last reset (null = never). [partialCredits] = credits collected since then
 * that are still inside the period as of today; [partialDays] = days since the oldest of those.
 * Fixed rhythm (his call, 2026-11-25): finishing early doesn't pull the schedule forward. The next due date
 * counts from the old due date when he finished early, or from the day he finished when late (so a late
 * period never makes him catch up). [scheduled] = days since that anchor (negative after an early finish);
 * it drives [ratio] and [dueIn], while [days] stays the real days since he last finished the area.
 */
data class AreaStatus(
    val area: Area,
    val days: Int?,
    val partialDays: Int?,
    val partialCredits: Double,
    val ratio: Double,
    val status: Status,
    /** Today is a rest day: it doesn't count, so deadlines move out a day. */
    val restToday: Boolean = false,
    val scheduled: Int? = days,
) {
    /** Credits still missing for the next reset. */
    val creditsShort: Double get() = (area.credits - partialCredits).coerceAtLeast(0.0)

    /** Days left (today included) to add the missing credits before the oldest open credit drops out. */
    val daysLeft: Int? get() = partialDays?.let { area.target.coerceAtLeast(1) - it }

    /** Days until the area is due (0 = today, negative = overdue); null = never done. */
    val dueIn: Int? get() = scheduled?.let { area.target.coerceAtLeast(1) - it }
}

data class Movement(val exercise: Exercise, val last: LastDone?)

const val MAIN_CREDIT = 1.0
const val ALSO_CREDIT = 0.5

/** Everything the screens derive from the log: last time per movement and how due each area is. */
class Stats(val data: AppData, val today: LocalDate = LocalDate.now()) {
    val exById: Map<String, Exercise> = data.exercises.associateBy { it.id }
    val lastEx = HashMap<String, LastDone>()
    val countEx = HashMap<String, Int>()
    val areas: List<AreaStatus>

    private val needed: Map<String, Double> = data.areas.associate { it.id to it.credits }

    /** Rest days (epoch days), sorted. They don't count on the schedule: every area's clock pauses. */
    private val restEpochs: List<Long> = data.restDays.mapNotNull { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }.sorted()

    fun isRestDay(epochDay: Long): Boolean = restEpochs.binarySearch(epochDay) >= 0

    /** Day number on the schedule: the calendar day minus rest days up to and including it. */
    fun scheduleDay(epochDay: Long): Long {
        val i = restEpochs.binarySearch(epochDay)
        val upTo = if (i >= 0) i + 1 else -i - 1
        return epochDay - upTo
    }

    val restToday: Boolean get() = isRestDay(today.toEpochDay())

    /** Credits per area per day (epoch day), oldest day first. */
    private val byArea = HashMap<String, MutableList<Pair<Long, Double>>>()

    init {
        for (s in data.sessions.sortedBy { it.date }) {
            for (item in s.items) {
                countEx[item.ex] = (countEx[item.ex] ?: 0) + 1
                val prev = lastEx[item.ex]
                if (prev == null || prev.date < s.date) lastEx[item.ex] = LastDone(s.date, item.groups, item.note)
            }
            val day = LocalDate.parse(s.date).toEpochDay()
            // Workouts logged on a rest day are kept in History but give no credits.
            if (isRestDay(day)) continue
            for ((a, credit) in creditsFor(s)) if (credit > 0) byArea.getOrPut(a) { mutableListOf() }.add(day to credit)
        }
        val todayS = scheduleDay(today.toEpochDay())
        val isRest = isRestDay(today.toEpochDay())
        areas = data.areas.map { a ->
            val target = a.target.coerceAtLeast(1)
            val w = walk(a)
            val open = w.open
            val reset: Long? = w.resets.lastOrNull()
            // On a rest day, credits whose last chance was the day before can't be completed any more.
            val cutoff = todayS - target + if (isRest) 1 else 0
            while (open.isNotEmpty() && scheduleDay(open.first().first) <= cutoff) open.removeFirst()
            val days = reset?.let { (todayS - scheduleDay(it)).toInt() }
            val scheduled = w.anchors.lastOrNull()?.let { (todayS - it).toInt() }
            val partialCredits = open.sumOf { it.second }
            val partialDays = open.firstOrNull()?.let { (todayS - scheduleDay(it.first)).toInt() }
            val ratio = if (scheduled == null) 9.0 else scheduled.toDouble() / target
            val status = when {
                days == null || ratio >= 2 -> Status.OVERDUE
                ratio >= 1 -> Status.DUE
                ratio >= 0.75 -> Status.SOON
                else -> Status.FRESH
            }
            AreaStatus(a, days, partialDays, partialCredits, ratio, status, isRest, scheduled)
        }
    }

    /**
     * Walks an area's credits oldest day first. Returns every day its credits reached the credits
     * needed (its clock reset), oldest first, and the credits still open after the last day.
     */
    private class Walk(val resets: List<Long>, val anchors: List<Long>, val open: ArrayDeque<Pair<Long, Double>>)

    private fun walk(a: Area): Walk {
        val target = a.target.coerceAtLeast(1)
        val need = a.credits - 1e-9
        val resets = mutableListOf<Long>()
        val anchors = mutableListOf<Long>()
        var anchor: Long? = null
        val open = ArrayDeque<Pair<Long, Double>>()
        for ((day, credit) in byArea[a.id].orEmpty()) {
            val dayS = scheduleDay(day)
            while (open.isNotEmpty() && scheduleDay(open.first().first) <= dayS - target) open.removeFirst()
            open.addLast(day to credit)
            if (open.sumOf { it.second } >= need) {
                // Fixed rhythm: early keeps the old due date as the start of the next period; on time or late starts it today.
                val due = anchor?.plus(target)
                anchor = if (due != null && dayS < due) due else dayS
                resets.add(day)
                anchors.add(anchor)
                open.clear()
            }
        }
        return Walk(resets, anchors, open)
    }

    /** Credits [areaId] got per day (epoch day, rest days left out), oldest first. Used by Insights' stoplight. */
    fun creditDays(areaId: String): List<Pair<Long, Double>> = byArea[areaId].orEmpty()

    /** Every day [a] counted as worked (its clock reset), as epoch days, oldest first. */
    fun resetDays(a: Area): List<Long> = walk(a).resets

    /**
     * For Insights: each reset (epoch day) with the schedule day its next period counts from (fixed rhythm),
     * so a day is on track while it's no more than the target past that anchor.
     */
    fun resetAnchors(a: Area): List<Pair<Long, Long>> = walk(a).let { w -> w.resets.zip(w.anchors) }

    /** Credits per area for one day. The same movement logged twice that day counts once. */
    fun creditsFor(s: com.laurio.workoutlog.data.Session): Map<String, Double> {
        val credits = HashMap<String, Double>()
        for (id in s.items.map { it.ex }.distinct()) {
            val e = exById[id] ?: continue
            e.areas.distinct().forEachIndexed { i, a ->
                credits[a] = (credits[a] ?: 0.0) + if (i == 0) MAIN_CREDIT else ALSO_CREDIT
            }
        }
        for (a in s.areas) credits[a] = maxOf(credits[a] ?: 0.0, needed[a] ?: Area.DEFAULT_CREDITS)
        return credits
    }

    fun daysAgo(date: String): Int = ChronoUnit.DAYS.between(LocalDate.parse(date), today).toInt()

    /**
     * What to work on: up to 3 strength areas at or past their target (skipping any worked
     * yesterday or today), topped up to 2 with ones coming due, plus conditioning/mobility when due.
     */
    fun focus(): List<AreaStatus> {
        val rested = { s: AreaStatus -> (s.days ?: 99) >= 2 }
        val strength = active().filter { it.area.kind == "strength" }.sortedByDescending { it.ratio }
        val picked = strength.filter { it.ratio >= 1 && rested(it) }.take(3).toMutableList()
        for (s in strength) if (picked.size < 2 && s !in picked && s.ratio >= 0.75 && rested(s)) picked.add(s)
        val extras = active().filter { it.area.kind != "strength" && it.ratio >= 1 }.sortedByDescending { it.ratio }
        // Which areas is decided by how due they are; the cards are shown soonest deadline first.
        return byDeadline(picked) + byDeadline(extras)
    }

    /**
     * Soonest deadline first, the one each card shows: the day open credits run out (daysLeft), else days until
     * due (dueIn; negative = overdue); never-done areas first. Ties: the more overdue (ratio) first.
     */
    fun byDeadline(list: List<AreaStatus>): List<AreaStatus> =
        list.sortedWith(compareBy<AreaStatus> { deadlineDays(it) }.thenByDescending { it.ratio })

    private fun deadlineDays(s: AreaStatus): Int {
        val left = s.daysLeft
        if (left != null && s.partialCredits > 0) return left
        return s.dueIn ?: Int.MIN_VALUE
    }

    /** Areas he isn't resting today. */
    fun active(): List<AreaStatus> = areas.filter { !it.area.isResting(today) }

    /** Areas he moved down (resting through [Area.restUntil]); shown last on Next. */
    fun resting(): List<AreaStatus> = areas.filter { it.area.isResting(today) }.sortedBy { it.area.restUntil }

    /**
     * When nothing is due, every area is offered, longest since worked first, so the area he
     * did most recently ends up at the bottom.
     */
    fun allByRest(): List<AreaStatus> = active().sortedWith(
        compareByDescending<AreaStatus> { it.days ?: Int.MAX_VALUE }
            .thenBy { deadlineDays(it) } // done the same day: the one due sooner first (his call, 2026-11-27)
            .thenByDescending { it.ratio },
    )

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
