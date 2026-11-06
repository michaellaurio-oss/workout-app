package com.laurio.workoutlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.data.Measure
import com.laurio.workoutlog.logic.Stats
import com.laurio.workoutlog.logic.ago
import com.laurio.workoutlog.logic.formatDetail
import com.laurio.workoutlog.logic.prettyDate
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(data: AppData, vm: AppViewModel, onSaved: () -> Unit) {
    val stats = remember(data) { Stats(data) }
    var search by rememberSaveable { mutableStateOf("") }
    val q = search.trim().lowercase()
    val hits = if (q.isEmpty()) emptyList() else data.exercises
        .filter { !it.hidden && it.name.lowercase().contains(q) && !vm.isPicked(it.id) }
        .take(8)
    val today = LocalDate.now()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Log workout") }

        // Date: step back for days he forgot to log.
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.logDate = vm.logDate.minusDays(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Day before")
                }
                Text(
                    prettyDate(vm.logDate.toString()), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                )
                IconButton(onClick = { vm.logDate = vm.logDate.plusDays(1) }, enabled = vm.logDate < today) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Day after")
                }
                TextButton(onClick = { vm.logDate = today }, enabled = vm.logDate != today) { Text("Today") }
            }
        }

        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Add a movement") }, placeholder = { Text("swing, curl, bosu…") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (q.isNotEmpty()) {
            if (hits.isEmpty()) {
                item { Text("No match. Add it on the Exercises tab.", color = Muted) }
            }
            items(hits, key = { "hit-" + it.id }) { e ->
                Text(
                    e.name,
                    Modifier.fillMaxWidth().clickable { vm.togglePick(e.id); search = "" }.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (vm.picked.isEmpty()) {
            item { Text("Nothing picked yet. Tick movements on the Next tab, or search above.", color = Muted) }
        }
        items(vm.picked.toList(), key = { "pick-" + it.ex }) { p ->
            val ex = stats.exById[p.ex]
            val last = stats.lastEx[p.ex]
            val measures = ex?.measures ?: Measure.DEFAULT
            val minutes = ex?.timeInMinutes ?: false
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ex?.name ?: p.ex, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (last == null) "First time"
                                else "Last: ${ago(stats.daysAgo(last.date))}" +
                                    formatDetail(last.groups, last.note).let { if (it.isEmpty()) "" else " · $it" },
                                style = MaterialTheme.typography.bodySmall, color = Muted,
                            )
                        }
                        IconButton(onClick = { vm.togglePick(p.ex) }) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
                    }
                    p.groups.forEachIndexed { i, g ->
                        Row(
                            Modifier.padding(end = 0.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            measures.forEach { m ->
                                val w = if (m == Measure.WEIGHT || m == Measure.SPEED || m == Measure.INCLINE) 1.3f else 1f
                                if (m == Measure.BAND) {
                                    OutlinedTextField(
                                        value = g.band, onValueChange = { vm.setGroup(p.ex, i, g.with(m, it)) },
                                        label = { Text("Band", maxLines = 1) }, singleLine = true, modifier = Modifier.weight(w),
                                    )
                                } else {
                                    NumberField(
                                        Measure.label(m, minutes), g.get(m), Modifier.weight(w),
                                        decimal = m != Measure.SETS && m != Measure.REPS,
                                        allowColon = m == Measure.TIME,
                                    ) { vm.setGroup(p.ex, i, g.with(m, it)) }
                                }
                            }
                            if (p.groups.size > 1) {
                                IconButton(onClick = { vm.removeGroup(p.ex, i) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove this line")
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.addGroup(p.ex) }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("Another weight")
                        }
                    }
                    OutlinedTextField(
                        value = p.note, onValueChange = { vm.setPickNote(p.ex, it) },
                        label = { Text("Note") }, placeholder = { Text("on Bosu, 30 min, felt easy…") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = vm.logNote, onValueChange = { vm.logNote = it },
                label = { Text("Workout notes") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = { vm.saveWorkout(); onSaved() },
                enabled = vm.canSave(), modifier = Modifier.fillMaxWidth(),
            ) { Text("Save workout") }
        }

        item { Column {
            SectionLabel("Didn't track movements? Log an area only")
            Text(
                "Tap an area to log it for the date above, like \"upper\" or \"lower\" in your old log.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        } }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.areas.forEach { a -> Chip(a.label) { vm.logArea(a.id) } }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier,
    decimal: Boolean = false,
    allowColon: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) || (allowColon && it == ':') }) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        // Time accepts "1:30"; the plain number pad has no colon, so time gets the full keyboard's numbers.
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                allowColon -> KeyboardType.Ascii
                decimal -> KeyboardType.Decimal
                else -> KeyboardType.Number
            },
        ),
        modifier = modifier,
    )
}
