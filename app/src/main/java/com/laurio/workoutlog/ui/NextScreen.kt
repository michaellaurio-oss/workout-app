package com.laurio.workoutlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.logic.AreaStatus
import com.laurio.workoutlog.logic.Movement
import com.laurio.workoutlog.logic.Stats
import com.laurio.workoutlog.logic.ago
import com.laurio.workoutlog.logic.formatDetail
import com.laurio.workoutlog.logic.prettyDate
import java.time.LocalDate

@Composable
fun NextScreen(data: AppData, vm: AppViewModel) {
    val stats = remember(data) { Stats(data) }
    val focus = remember(stats) { stats.focus() }
    val allFresh = focus.isEmpty()
    val cards = remember(stats) { if (allFresh) stats.allByRest() else focus }
    val routine = remember(stats) { stats.everySession() }
    val expanded = remember { mutableStateListOf<String>() }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Column {
            ScreenTitle("Next workout", prettyDate(LocalDate.now().toString()))
            Spacer(Modifier.height(8.dp))
            Text(
                if (allFresh) "Everything is fresh. Pick anything, or rest."
                else "Work on: " + focus.joinToString { it.area.label },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                (if (allFresh) "All areas are below, longest since you worked them first; the one you did most recently is last. " else "") +
                    "Movements are listed oldest first, so the ones you haven't done in a while are on top. Tick what you do, then tap Log.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        } }
        if (routine.isNotEmpty()) {
            item(key = "every-session") { RoutineCard(routine, stats, vm) }
        }
        items(cards, key = { it.area.id }) { st ->
            FocusCard(st, stats, vm, open = st.area.id in expanded) {
                if (st.area.id in expanded) expanded.remove(st.area.id) else expanded.add(st.area.id)
            }
        }
        if (!allFresh) {
            item { SectionLabel("All areas · the tick is your target") }
            items(stats.areas.sortedByDescending { it.ratio }, key = { "all-" + it.area.id }) { AreaRow(it) }
        }
    }
}

@Composable
private fun FocusCard(st: AreaStatus, stats: Stats, vm: AppViewModel, open: Boolean, onToggleOpen: () -> Unit) {
    val list = stats.movements(st.area.id)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    st.area.label.uppercase(), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
                StatusPill(st.status)
            }
            Text(whyText(st), style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(6.dp))
            if (list.isEmpty()) {
                Text("No movements here yet. Add some on the Exercises tab.", color = Muted)
            }
            (if (open) list else list.take(4)).forEach { m ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                MovementRow(m, stats, checked = vm.isPicked(m.exercise.id)) { vm.togglePick(m.exercise.id) }
            }
            if (list.size > 4) {
                TextButton(onClick = onToggleOpen) { Text(if (open) "Show fewer" else "Show all ${list.size}") }
            }
        }
    }
}

/** His every-session routine, always at the top whatever is due. */
@Composable
private fun RoutineCard(list: List<Movement>, stats: Stats, vm: AppViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("EVERY SESSION", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Your routine. Mark movements \"Do every session\" on the Exercises tab to add them here.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Spacer(Modifier.height(6.dp))
            list.forEach { m ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                MovementRow(m, stats, checked = vm.isPicked(m.exercise.id)) { vm.togglePick(m.exercise.id) }
            }
        }
    }
}

@Composable
private fun MovementRow(m: Movement, stats: Stats, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(m.exercise.name, style = MaterialTheme.typography.bodyLarge)
            if (m.exercise.equip.isNotBlank()) {
                Text(m.exercise.equip, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val last = m.last
            if (last == null) {
                Text("never logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(ago(stats.daysAgo(last.date)), style = MaterialTheme.typography.bodySmall, color = Muted)
                val detail = formatDetail(last.groups, last.note)
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun AreaRow(st: AreaStatus) {
    val (fg, _) = statusColors(st.status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(st.area.label, Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        TargetBar(st.ratio, fg, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            st.days?.let { ago(it) } ?: "none yet", Modifier.width(92.dp),
            style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.End,
        )
    }
}

private fun whyText(s: AreaStatus): String {
    val parts = mutableListOf(
        if (s.days == null) "no full session yet" else "full session ${ago(s.days)}",
    )
    if (s.partialDays != null) {
        parts += "${credits(s.partialCredits)} of ${credits(s.area.credits)} ${ago(s.partialDays)}"
    }
    parts += "target every ${s.area.target} days"
    return parts.joinToString(" · ")
}

/** 1.0 -> "1", 1.5 -> "1.5" */
private fun credits(c: Double): String = if (c % 1.0 == 0.0) c.toLong().toString() else c.toString()
