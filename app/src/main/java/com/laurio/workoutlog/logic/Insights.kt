package com.laurio.workoutlog.logic

import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.data.Area
import com.laurio.workoutlog.data.Exercise
import com.laurio.workoutlog.data.SetGroup
import com.laurio.workoutlog.data.Session
import java.time.DayOfWeek
import java.time.LocalDate

/** What a day looks like on the calendar heatmap. */
enum class DayKind { REST, WORKOUT, INJURY, OTHER, FUTURE }

/** [level] = 1 (1-2 movements), 2 (3-5, or an area-only day), 3 (6+); 0 when not a workout. */
data class DayCell(val date: LocalDate, val kind: DayKind, val level: Int)

/**
 * How well an area kept to its every-N-days target over a window. [onTimePct] = share of days it
 * wasn't past its target (injury days left out). [times] = days it counted as worked in the window.
 */
data class AreaBalance(val area: Area, val onTimePct: Int, val times: Int)

/** A movement and how many days it was done in the window. */
data class MovementCount(val exercise: Exercise, val days: Int)

/** A movement not done lately: [daysAgo] since the last time. */
data class StaleMovement(val exercise: Exercise, val daysAgo: Int)

/**
 * The Insights tab: consistency, how well each area kept to its target, which movements get done
 * most and which have dropped off. Built from the same credit rules as the Next tab ([Stats]).
 */
class Insights(val data: AppData, val today: LocalDate = LocalDate.now()) {
    val stats = Stats(data, today)
    private val byDate: Map<String, Session> = data.sessions.associateBy { it.date }
    private val todayDay = today.toEpochDay()

    /** First day anything was logged, or null for an empty log. */
    val firstDay: LocalDate? = data.sessions.minOfOrNull { it.date }?.let { LocalDate.parse(it) }

    private fun isWorkout(s: Session?) = s != null && (s.items.isNotEmpty() || s.areas.isNotEmpty())
    private fun isInjury(s: Session?) = s != null && !isWorkout(s) && s.tag.equals("injury", ignoreCase = true)

    fun cell(date: LocalDate): DayCell {
        if (date.isAfter(today)) return DayCell(date, DayKind.FUTURE, 0)
        val s = byDate[date.toString()] ?: return DayCell(date, DayKind.REST, 0)
        return when {
            isWorkout(s) -> {
                val n = s.items.map { it.ex }.distinct().size
                val level = when {
                    n == 0 -> 2
                    n <= 2 -> 1
                    n <= 5 -> 2
                    else -> 3
                }
                DayCell(date, DayKind.WORKOUT, level)
            }
            isInjury(s) -> DayCell(date, DayKind.INJURY, 0)
            s.tag.isNotBlank() || s.note.isNotBlank() -> DayCell(date, DayKind.OTHER, 0)
            else -> DayCell(date, DayKind.REST, 0)
        }
    }

    val thisMonday: LocalDate = today.with(DayOfWeek.MONDAY)

    /** The last [count] weeks (Monday first), oldest first, ending with this week. */
    fun weeks(count: Int): List<List<DayCell>> = (count - 1 downTo 0).map { w ->
        val monday = thisMonday.minusWeeks(w.toLong())
        (0L..6L).map { cell(monday.plusDays(it)) }
    }

    fun workoutsInWeek(monday: LocalDate): Int = (0L..6L).count { isWorkout(byDate[monday.plusDays(it).toString()]) }
    private fun injuryInWeek(monday: LocalDate): Boolean = (0L..6L).any { isInjury(byDate[monday.plusDays(it).toString()]) }

    val thisWeek: Int get() = workoutsInWeek(thisMonday)

    /** Average workouts per full week over the last [weeks] weeks (not counting this one), or null if none yet. */
    fun avgPerWeek(weeks: Int = 8): Double? {
        val first = firstDay?.with(DayOfWeek.MONDAY) ?: return null
        val mondays = (1..weeks).map { thisMonday.minusWeeks(it.toLong()) }.filter { !it.isBefore(first) }
        if (mondays.isEmpty()) return null
        return mondays.sumOf { workoutsInWeek(it) }.toDouble() / mondays.size
    }

    /**
     * Weeks in a row with at least [min] workouts. This week counts once it gets there and doesn't
     * break the streak before then; a week with injury days is skipped rather than breaking it.
     */
    fun streak(min: Int = STREAK_MIN): Int {
        val first = firstDay?.with(DayOfWeek.MONDAY) ?: return 0
        var n = if (workoutsInWeek(thisMonday) >= min) 1 else 0
        var monday = thisMonday.minusWeeks(1)
        while (!monday.isBefore(first)) {
            when {
                workoutsInWeek(monday) >= min -> n++
                injuryInWeek(monday) -> {}
                else -> break
            }
            monday = monday.minusWeeks(1)
        }
        return n
    }

    /** First day of a window of [days] days ending today (null = since the first log). */
    fun windowStart(days: Int?): LocalDate? {
        val first = firstDay ?: return null
        if (days == null) return first
        val start = today.minusDays((days - 1).toLong())
        return if (start.isBefore(first)) first else start
    }

    /** Each area's share of days on schedule in the window, lowest first. */
    fun balance(windowDays: Int?): List<AreaBalance> {
        val start = windowStart(windowDays)?.toEpochDay() ?: return emptyList()
        val first = firstDay!!.toEpochDay()
        val injury = data.sessions.filter { isInjury(it) }.map { LocalDate.parse(it.date).toEpochDay() }.toSet()
        return data.areas.map { a ->
            val target = a.target.coerceAtLeast(1)
            val resets = stats.resetDays(a)
            var i = 0
            var last: Long? = null
            var counted = 0
            var onTime = 0
            for (d in start..todayDay) {
                while (i < resets.size && resets[i] <= d) last = resets[i++]
                if (d in injury) continue
                counted++
                val since = d - (last ?: first)
                if (since <= target) onTime++
            }
            val pct = if (counted == 0) 0 else Math.round(onTime * 100.0 / counted).toInt()
            AreaBalance(a, pct, resets.count { it in start..todayDay })
        }.sortedWith(compareBy<AreaBalance> { it.onTimePct }.thenBy { it.area.label })
    }

    /** Movements done on the most days in the window (every-session routine left out). */
    fun mostDone(windowDays: Int?, limit: Int = 8): List<MovementCount> {
        val start = windowStart(windowDays)?.toString() ?: return emptyList()
        val counts = HashMap<String, Int>()
        for (s in data.sessions) {
            if (s.date < start || s.date > today.toString()) continue
            for (id in s.items.map { it.ex }.distinct()) counts[id] = (counts[id] ?: 0) + 1
        }
        return counts.mapNotNull { (id, n) ->
            stats.exById[id]?.takeIf { !it.everySession }?.let { MovementCount(it, n) }
        }.sortedWith(compareByDescending<MovementCount> { it.days }.thenBy { it.exercise.name.lowercase() }).take(limit)
    }

    private fun offered(e: Exercise) = !e.hidden && !e.everySession && e.inSeason(today.monthValue)

    /** Movements done before but not in the last [days] days, longest ago first. */
    fun stale(days: Int = STALE_DAYS): List<StaleMovement> = data.exercises.filter { offered(it) }
        .mapNotNull { e -> stats.lastEx[e.id]?.let { StaleMovement(e, stats.daysAgo(it.date)) } }
        .filter { it.daysAgo >= days }
        .sortedByDescending { it.daysAgo }

    /** Movements on the list that have never been logged, by area then name. */
    fun neverDone(): List<Exercise> {
        val order = data.areas.mapIndexed { i, a -> a.id to i }.toMap()
        return data.exercises.filter { offered(it) && stats.lastEx[it.id] == null }
            .sortedWith(compareBy<Exercise>({ order[it.mainArea] ?: 99 }, { it.name.lowercase() }))
    }

    companion object {
        const val STREAK_MIN = 3
        const val STALE_DAYS = 30
    }
}

/** What a movement's progress chart plots. */
enum class ProgressMetric(val label: String) {
    WEIGHT("Heaviest weight"), REPS("Most reps"), TIME("Longest time"), NONE("")
}

/** One day on the chart. */
data class ProgressPoint(val date: LocalDate, val value: Double)

/** One day in the movement's history list: date and what was done ("4×12 @ 35 lb"). */
data class ProgressDay(val date: String, val detail: String)

data class Progress(
    val exercise: Exercise?,
    val name: String,
    val metric: ProgressMetric,
    val points: List<ProgressPoint>,
    val days: List<ProgressDay>,
) {
    val best: ProgressPoint? get() = points.maxByOrNull { it.value }

    fun format(v: Double): String = when (metric) {
        ProgressMetric.WEIGHT -> "${formatWeight(v)} lb"
        ProgressMetric.REPS -> "${v.toInt()} reps"
        ProgressMetric.TIME -> formatTime(v.toInt())
        ProgressMetric.NONE -> ""
    }
}

/**
 * Everything logged for one movement. The chart plots the heaviest weight per day when at least
 * two days have a weight; otherwise most reps, then longest time. Days with only a note are listed
 * but not plotted.
 */
fun progressFor(data: AppData, exId: String): Progress {
    val ex = data.exercises.firstOrNull { it.id == exId }
    val perDay = data.sessions.sortedBy { it.date }.mapNotNull { s ->
        val items = s.items.filter { it.ex == exId }
        if (items.isEmpty()) null
        else Triple(s.date, items.flatMap { it.groups }.filter { !it.isEmpty }, items.map { it.note.trim() }.filter { it.isNotEmpty() }.joinToString(" · "))
    }
    fun series(pick: (SetGroup) -> Double?): List<ProgressPoint> = perDay.mapNotNull { (date, groups, _) ->
        groups.mapNotNull(pick).maxOrNull()?.let { ProgressPoint(LocalDate.parse(date), it) }
    }
    val weight = series { g -> g.weight?.takeIf { it > 0 } }
    val reps = series { g -> g.reps?.takeIf { it > 0 }?.toDouble() }
    val time = series { g -> g.time?.takeIf { it > 0 }?.toDouble() }
    val (metric, points) = when {
        weight.size >= 2 -> ProgressMetric.WEIGHT to weight
        reps.size >= 2 -> ProgressMetric.REPS to reps
        time.size >= 2 -> ProgressMetric.TIME to time
        else -> ProgressMetric.NONE to emptyList<ProgressPoint>()
    }
    val days = perDay.reversed().map { (date, groups, note) ->
        ProgressDay(date, formatDetail(groups, note).ifEmpty { "No details logged" })
    }
    return Progress(ex, ex?.name ?: exId, metric, points, days)
}
