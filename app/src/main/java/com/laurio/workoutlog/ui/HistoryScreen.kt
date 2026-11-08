package com.laurio.workoutlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.logic.formatDetail
import com.laurio.workoutlog.logic.prettyDate
import com.laurio.workoutlog.logic.shortDate

private sealed interface Pending {
    data class Day(val date: String) : Pending
    data class Line(val date: String, val index: Int, val name: String) : Pending
}

@Composable
fun HistoryScreen(data: AppData, vm: AppViewModel) {
    val names = remember(data) { data.exercises.associate { it.id to it.name } }
    val areaNames = remember(data) { data.areas.associate { it.id to it.label } }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var progressId by remember { mutableStateOf<String?>(null) }
    val days = data.sessions.filter { it.items.isNotEmpty() || it.areas.isNotEmpty() || it.tag.isNotBlank() || it.note.isNotBlank() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("History", "${days.size} days logged") }
        if (days.isEmpty()) item { Text("No workouts yet. Log one on the Log tab.", color = Muted) }
        days.groupBy { it.date.take(4) }.forEach { (year, inYear) ->
            item(key = "year-$year") {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(year, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${inYear.size} day${if (inYear.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
            }
            items(inYear, key = { it.date }) { s ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                shortDate(s.date), Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            )
                            if (s.tag.isNotBlank()) Pill(s.tag, Muted, MaterialTheme.colorScheme.surfaceVariant)
                            TextButton(onClick = { pending = Pending.Day(s.date) }) { Text("Delete") }
                        }
                        s.items.forEachIndexed { i, item ->
                            val name = names[item.ex] ?: item.ex
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { progressId = item.ex }) {
                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                    val detail = formatDetail(item.groups, item.note)
                                    if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
                                }
                                IconButton(onClick = { pending = Pending.Line(s.date, i, name) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove $name", tint = Muted)
                                }
                            }
                        }
                        if (s.areas.isNotEmpty()) {
                            Text(
                                "Areas: " + s.areas.joinToString { areaNames[it] ?: it },
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                        if (s.raw.isNotBlank()) {
                            Text(
                                "Original: ${s.raw}", style = MaterialTheme.typography.labelSmall, color = Muted,
                                modifier = Modifier.padding(bottom = 8.dp, end = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    progressId?.let { id -> ProgressDialog(data, id) { progressId = null } }

    when (val p = pending) {
        is Pending.Day -> AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Delete ${prettyDate(p.date)}?") },
            text = { Text("This removes everything logged that day.") },
            confirmButton = { TextButton(onClick = { vm.deleteDay(p.date); pending = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Keep") } },
        )
        is Pending.Line -> AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("Removes it from ${prettyDate(p.date)} only.") },
            confirmButton = { TextButton(onClick = { vm.deleteItem(p.date, p.index); pending = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Keep") } },
        )
        null -> {}
    }
}
