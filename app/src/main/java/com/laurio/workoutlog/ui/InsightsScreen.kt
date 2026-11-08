package com.laurio.workoutlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.logic.AreaBalance
import com.laurio.workoutlog.logic.DayCell
import com.laurio.workoutlog.logic.DayKind
import com.laurio.workoutlog.logic.Insights
import com.laurio.workoutlog.logic.Progress
import com.laurio.workoutlog.logic.ProgressMetric
import com.laurio.workoutlog.logic.Status
import com.laurio.workoutlog.logic.ago
import com.laurio.workoutlog.logic.formatWeight
import com.laurio.workoutlog.logic.prettyDate
import com.laurio.workoutlog.logic.progressFor
import java.time.format.DateTimeFormatter

/** Bar-chart icon for the Insights tab (the core icon set has none). */
val InsightsIcon: ImageVector by lazy {
    ImageVector.Builder("Insights", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 9.2f); horizontalLineTo(8f); verticalLineTo(19f); horizontalLineTo(5f); close()
            moveTo(10.6f, 5f); horizontalLineTo(13.4f); verticalLineTo(19f); horizontalLineTo(10.6f); close()
            moveTo(16.2f, 13f); horizontalLineTo(19f); verticalLineTo(19f); horizontalLineTo(16.2f); close()
        }
    }.build()
}

/** Window choices for the area and movement sections: label to days (null = everything logged). */
private val WINDOWS = listOf("4 wk" to 28, "8 wk" to 56, "All" to null)

private const val HEATMAP_WEEKS = 17

@Composable
fun InsightsScreen(data: AppData) {
    val ins = remember(data) { Insights(data) }
    var windowIx by rememberSaveable { mutableStateOf(1) }
    var showNever by rememberSaveable { mutableStateOf(false) }
    var progressId by rememberSaveable { mutableStateOf<String?>(null) }
    val window = WINDOWS[windowIx].second
    val balance = remember(ins, window) { ins.balance(window) }
    val most = remember(ins, window) { ins.mostDone(window) }
    val stale = remember(ins) { ins.stale() }
    val never = remember(ins) { ins.neverDone() }
    val areaNames = remember(data) { data.areas.associate { it.id to it.label } }
    val cs = MaterialTheme.colorScheme

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle("Insights", ins.firstDay?.let { "Since ${prettyDate(it.toString())}" })
        }
        if (ins.firstDay == null) {
            item { Text("Nothing logged yet. Log a workout and this fills in.", color = Muted) }
            return@LazyColumn
        }

        // Consistency
        item {
            InsightCard {
                Row(Modifier.fillMaxWidth()) {
                    Stat("This week", ins.thisWeek.toString(), Modifier.weight(1f))
                    Stat("Avg / week", ins.avgPerWeek()?.let { "%.1f".format(it) } ?: "–", Modifier.weight(1f))
                    Stat("Streak", "${ins.streak()} wk", Modifier.weight(1f))
                }
                Text(
                    "Avg is the last 8 full weeks. Streak = weeks in a row with ${Insights.STREAK_MIN}+ workouts; " +
                        "injury weeks don't break it.",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
                Spacer(Modifier.height(4.dp))
                Heatmap(ins.weeks(HEATMAP_WEEKS))
            }
        }

        // Window picker
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WINDOWS.forEachIndexed { i, (label, _) -> Chip(label, selected = i == windowIx) { windowIx = i } }
            }
        }

        // Area balance
        item {
            Column {
                SectionLabel("Areas on schedule")
                InsightCard {
                    Text(
                        "Share of days each area wasn't past its every-N-days target. Lowest first.",
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                    balance.forEach { BalanceRow(it) }
                }
            }
        }

        // Most done
        item {
            Column {
                SectionLabel("Most done")
                InsightCard {
                    if (most.isEmpty()) Text("No movements logged in this window.", color = Muted)
                    val top = most.maxOfOrNull { it.days } ?: 1
                    most.forEach { m ->
                        Column(
                            Modifier.fillMaxWidth().clickable { progressId = m.exercise.id }.padding(vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text("${m.days} day${if (m.days == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                            }
                            Spacer(Modifier.height(4.dp))
                            FillBar(m.days.toFloat() / top, cs.primary)
                        }
                    }
                    Text("Tap a movement to see its progress.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }

        // Dropped off
        item {
            Column {
                SectionLabel("Not done in ${Insights.STALE_DAYS}+ days")
                InsightCard {
                    if (stale.isEmpty()) Text("Everything you've done has come up in the last ${Insights.STALE_DAYS} days.", color = Muted)
                    stale.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { progressId = s.exercise.id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.exercise.name, style = MaterialTheme.typography.bodyLarge)
                                Text(areaNames[s.exercise.mainArea] ?: "", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            Text(ago(s.daysAgo), style = MaterialTheme.typography.bodyMedium, color = statusColors(Status.SOON).first)
                        }
                    }
                    if (never.isNotEmpty()) {
                        TextButton(onClick = { showNever = !showNever }) {
                            Text(if (showNever) "Hide never-done movements" else "Show ${never.size} movements never done")
                        }
                        if (showNever) {
                            never.groupBy { it.mainArea }.forEach { (area, list) ->
                                Text(
                                    (areaNames[area] ?: area).uppercase(), style = MaterialTheme.typography.labelMedium,
                                    color = Muted, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp),
                                )
                                list.forEach { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }
    }

    progressId?.let { id -> ProgressDialog(data, id) { progressId = null } }
}

@Composable
private fun InsightCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 1.sp)
    }
}

/** A plain horizontal bar filled to [fraction] (0-1). */
@Composable
private fun FillBar(fraction: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
private fun BalanceRow(b: AreaBalance) {
    val color = when {
        b.onTimePct >= 80 -> statusColors(Status.FRESH).first
        b.onTimePct >= 50 -> statusColors(Status.SOON).first
        else -> statusColors(Status.OVERDUE).first
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.area.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${b.onTimePct}%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(4.dp))
        FillBar(b.onTimePct / 100f, color)
        Text(
            "Worked ${b.times}× · aim every ${b.area.target} days",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private val monthFmt = DateTimeFormatter.ofPattern("MMM")

/** Calendar heatmap: one column per week (oldest left), Monday at the top. */
@Composable
private fun Heatmap(weeks: List<List<DayCell>>) {
    val cs = MaterialTheme.colorScheme
    val injury = statusColors(Status.OVERDUE).second
    fun color(c: DayCell): Color = when (c.kind) {
        DayKind.WORKOUT -> cs.primary.copy(alpha = when (c.level) { 1 -> 0.35f; 2 -> 0.65f; else -> 1f })
        DayKind.INJURY -> injury
        DayKind.OTHER -> cs.outline
        DayKind.REST -> cs.surfaceVariant
        DayKind.FUTURE -> Color.Transparent
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // Month labels over the first week of each month.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(14.dp))
            weeks.forEachIndexed { i, week ->
                val first = week.first().date
                val newMonth = i == 0 || week.any { it.date.dayOfMonth == 1 }
                Box(Modifier.weight(1f)) {
                    if (newMonth) {
                        val label = week.firstOrNull { it.date.dayOfMonth == 1 }?.date ?: first
                        Text(
                            label.format(monthFmt), style = MaterialTheme.typography.labelSmall, color = Muted,
                            softWrap = false, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                        )
                    }
                }
            }
        }
        listOf("M", "", "W", "", "F", "", "S").forEachIndexed { dow, dayLabel ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel, Modifier.width(14.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
                weeks.forEach { week ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(color(week[dow])))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = Muted)
            listOf(0.35f, 0.65f, 1f).forEach { a ->
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary.copy(alpha = a)))
            }
            Text("More", style = MaterialTheme.typography.labelSmall, color = Muted)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(injury))
            Text("Injury", style = MaterialTheme.typography.labelSmall, color = Muted)
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(cs.outline))
            Text("Other", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

/** A movement's progress: chart of its best number per day, then every day it was logged. */
@Composable
fun ProgressDialog(data: AppData, exId: String, onDismiss: () -> Unit) {
    val p = remember(data, exId) { progressFor(data, exId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.metric == ProgressMetric.NONE) {
                    Text(
                        "Not enough numbers to chart yet. Log weight, reps or time on at least two days and a chart appears here.",
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                } else {
                    val best = p.best!!
                    Text(
                        "${p.metric.label} per day · best ${p.format(best.value)} on ${prettyDate(best.date.toString())}",
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                    ProgressChart(p)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (p.days.isEmpty()) Text("Never logged.", color = Muted)
                p.days.forEach { d ->
                    Column {
                        Text(prettyDate(d.date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(d.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ProgressChart(p: Progress) {
    val pts = p.points
    val line = MaterialTheme.colorScheme.primary
    val bestColor = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outline
    val lo0 = pts.minOf { it.value }
    val hi0 = pts.maxOf { it.value }
    val pad = if (hi0 == lo0) maxOf(1.0, hi0 * 0.1) else (hi0 - lo0) * 0.15
    val lo = maxOf(0.0, lo0 - pad)
    val hi = hi0 + pad
    val d0 = pts.first().date.toEpochDay()
    val d1 = pts.last().date.toEpochDay()
    val best = p.best
    Column {
        Row {
            Column(Modifier.height(140.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(axis(p, hi0), style = MaterialTheme.typography.labelSmall, color = Muted)
                Text(axis(p, lo0), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Canvas(Modifier.weight(1f).height(140.dp)) {
                val r = 4.dp.toPx()
                fun x(day: Long) = if (d1 == d0) size.width / 2 else r + (size.width - 2 * r) * (day - d0).toFloat() / (d1 - d0)
                fun y(v: Double) = (size.height - r) - (size.height - 2 * r) * ((v - lo) / (hi - lo)).toFloat()
                listOf(hi0, lo0).forEach { v ->
                    drawLine(grid, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
                }
                val path = Path()
                pts.forEachIndexed { i, pt ->
                    val o = Offset(x(pt.date.toEpochDay()), y(pt.value))
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
                pts.forEach { pt ->
                    val isBest = pt == best
                    drawCircle(if (isBest) bestColor else line, radius = if (isBest) r * 1.4f else r, center = Offset(x(pt.date.toEpochDay()), y(pt.value)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(prettyDate(pts.first().date.toString()), style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.weight(1f))
            if (pts.size > 1) Text(prettyDate(pts.last().date.toString()), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

/** Short axis label: "35", "12", "1:30". */
private fun axis(p: Progress, v: Double): String = when (p.metric) {
    ProgressMetric.TIME -> p.format(v)
    else -> formatWeight(v)
}
