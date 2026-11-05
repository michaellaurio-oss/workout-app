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

/** "4×12 @ 40 lb", "4 sets", "@ 25 lb" */
fun formatGroup(g: SetGroup): String {
    val sr = when {
        g.sets != null && g.reps != null -> "${g.sets}×${g.reps}"
        g.sets != null -> "${g.sets} sets"
        g.reps != null -> "${g.reps} reps"
        else -> ""
    }
    val w = g.weight?.let { "@ ${formatWeight(it)} lb" } ?: ""
    return listOf(sr, w).filter { it.isNotEmpty() }.joinToString(" ")
}

/** All lines plus the note: "2×10 @ 25 lb, 2×10 @ 35 lb · on Bosu" */
fun formatDetail(groups: List<SetGroup>, note: String): String =
    listOf(groups.filter { !it.isEmpty }.joinToString(", ") { formatGroup(it) }, note.trim())
        .filter { it.isNotEmpty() }.joinToString(" · ")
