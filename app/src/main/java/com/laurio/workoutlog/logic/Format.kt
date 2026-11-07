package com.laurio.workoutlog.logic

import com.laurio.workoutlog.data.SetGroup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
private val dayYearFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

fun prettyDate(date: String): String {
    val d = LocalDate.parse(date)
    return d.format(if (d.year == LocalDate.now().year) dayFmt else dayYearFmt)
}

fun ago(days: Int): String = when {
    days <= 0 -> "today"
    days == 1 -> "yesterday"
    else -> "$days days ago"
}

/** 40.0 -> "40", 12.5 -> "12.5" */
fun formatWeight(w: Double): String = if (w % 1.0 == 0.0) w.toLong().toString() else w.toString()

/** 45 -> "45s", 90 -> "1:30", 1800 -> "30 min" */
fun formatTime(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "%d:%02d".format(sec / 60, sec % 60)
}

/** Seconds as typed into a time box: "1:30" is minutes:seconds; a plain number is minutes or seconds. */
fun parseTime(text: String, minutes: Boolean): Int? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    if (':' in t) {
        val (m, s) = t.split(':', limit = 2)
        val mm = m.toIntOrNull() ?: 0
        val ss = s.toIntOrNull() ?: return null
        return mm * 60 + ss
    }
    val n = t.toDoubleOrNull() ?: return null
    return if (minutes) Math.round(n * 60).toInt() else Math.round(n).toInt()
}

/** Seconds back into the box: minutes as "30" or "1:30", seconds as "45". */
fun timeForField(sec: Int, minutes: Boolean): String =
    if (!minutes) sec.toString() else if (sec % 60 == 0) (sec / 60).toString() else "%d:%02d".format(sec / 60, sec % 60)

private fun num(d: Double) = formatWeight(d)

val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** [6,7,8,9] -> "Jun–Sep", [12,1,2] -> "Dec–Feb", [1,3] -> "Jan, Mar"; empty -> "" */
fun formatMonths(months: List<Int>): String {
    val set = months.filter { it in 1..12 }.toSet()
    if (set.isEmpty() || set.size == 12) return ""
    // Start a run at a month whose previous month isn't included, so Dec-Feb wraps correctly.
    val starts = (1..12).filter { it in set && ((it + 10) % 12 + 1) !in set }
    return starts.joinToString(", ") { start ->
        var end = start
        while ((end % 12 + 1) in set && (end % 12 + 1) != start) end = end % 12 + 1
        if (end == start) MONTHS[start - 1] else "${MONTHS[start - 1]}–${MONTHS[end - 1]}"
    }
}

/** "4×12 @ 40 lb", "4 sets · 45s", "30 min · level 6", "3.5 mph · 4% incline · 30 min" */
fun formatGroup(g: SetGroup): String {
    val sr = when {
        g.sets != null && g.reps != null -> "${g.sets}×${g.reps}"
        g.sets != null -> "${g.sets} sets"
        g.reps != null -> "${g.reps} reps"
        else -> ""
    }
    val head = listOf(sr, g.weight?.let { "@ ${num(it)} lb" } ?: "").filter { it.isNotEmpty() }.joinToString(" ")
    return listOfNotNull(
        head.ifEmpty { null },
        g.speed?.let { "${num(it)} mph" },
        g.incline?.let { "${num(it)}% incline" },
        g.time?.let { formatTime(it) },
        g.resistance?.let { "level ${num(it)}" },
        g.band?.takeIf { it.isNotBlank() }?.let { "band: $it" },
    ).joinToString(" · ")
}

/** All lines plus the note: "2×10 @ 25 lb, 2×10 @ 35 lb · on Bosu" */
fun formatDetail(groups: List<SetGroup>, note: String): String =
    listOf(groups.filter { !it.isEmpty }.joinToString(", ") { formatGroup(it) }, note.trim())
        .filter { it.isNotEmpty() }.joinToString(" · ")
