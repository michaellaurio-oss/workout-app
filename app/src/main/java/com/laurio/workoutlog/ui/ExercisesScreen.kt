package com.laurio.workoutlog.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.data.Area
import com.laurio.workoutlog.data.Exercise
import com.laurio.workoutlog.data.Measure
import com.laurio.workoutlog.logic.Stats
import com.laurio.workoutlog.logic.ago
import java.time.LocalDate

@Composable
fun ExercisesScreen(data: AppData, vm: AppViewModel) {
    val stats = remember(data) { Stats(data) }
    val areaNames = remember(data) { data.areas.associate { it.id to it.label } }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var adding by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importUri = uri
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column {
            ScreenTitle("Exercises")
            Text(
                "Each movement sits under its main area. \"Also\" areas get partial credit. Tap a movement to rename it, " +
                    "move it to another area, or delete it. \"Every N days\" sets how often an area comes up.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add movement")
            }
        } }
        items(data.areas, key = { it.id }) { a ->
            AreaCard(
                area = a,
                movements = data.exercises.filter { !it.hidden && it.mainArea == a.id }.sortedBy { it.name.lowercase() },
                stats = stats,
                areaNames = areaNames,
                onTarget = { vm.setTarget(a.id, it) },
                onEdit = { editing = it },
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("BACKUP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Your log lives on this phone. Save a copy to Drive or Downloads now and then, " +
                            "and use Restore on a new phone.",
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportLauncher.launch("workout-log-backup-${LocalDate.now()}.json") }) {
                            Text("Save backup")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        ExerciseDialog(
            areas = data.areas,
            original = editing,
            timesLogged = editing?.let { stats.countEx[it.id] } ?: 0,
            onDismiss = { adding = false; editing = null },
            onSave = { name, main, also, equip, measures ->
                vm.saveExercise(editing, name, main, also, equip, measures)
                adding = false; editing = null
            },
            onDelete = { e -> vm.removeExercise(e); editing = null },
        )
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything on this phone will be replaced by what's in the backup file.") },
            confirmButton = { TextButton(onClick = { vm.import(uri); importUri = null }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AreaCard(
    area: Area,
    movements: List<Exercise>,
    stats: Stats,
    areaNames: Map<String, String>,
    onTarget: (Int) -> Unit,
    onEdit: (Exercise) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    area.label.uppercase(), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { onTarget(area.target - 1) }, enabled = area.target > 1) { Text("−") }
                Text("every ${area.target} days", style = MaterialTheme.typography.bodySmall, color = Muted)
                TextButton(onClick = { onTarget(area.target + 1) }, enabled = area.target < 30) { Text("+") }
            }
            if (movements.isEmpty()) Text("No movements yet.", color = Muted, modifier = Modifier.padding(bottom = 8.dp))
            movements.forEach { e ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(e) }.padding(vertical = 8.dp).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge)
                        val sub = listOfNotNull(
                            e.equip.takeIf { it.isNotBlank() },
                            e.measures.joinToString(" / ") { Measure.name(it).lowercase() },
                            e.areas.drop(1).takeIf { it.isNotEmpty() }?.let { also -> "also " + also.joinToString { areaNames[it] ?: it } },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    val last = stats.lastEx[e.id]
                    Text(
                        if (last == null) "never" else "${ago(stats.daysAgo(last.date))} · ${stats.countEx[e.id] ?: 0}×",
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDialog(
    areas: List<Area>,
    original: Exercise?,
    timesLogged: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, main: String, also: List<String>, equip: String, measures: List<String>) -> Unit,
    onDelete: (Exercise) -> Unit,
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var main by remember { mutableStateOf(original?.mainArea ?: areas.first().id) }
    val also = remember { mutableStateListOf<String>().apply { original?.areas?.drop(1)?.let { addAll(it) } } }
    var equip by remember { mutableStateOf(original?.equip ?: "") }
    val measures = remember { mutableStateListOf<String>().apply { addAll(original?.measures ?: Measure.DEFAULT) } }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Add movement" else "Edit movement") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Main area", style = MaterialTheme.typography.labelLarge, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    areas.forEach { a -> Chip(a.label, selected = main == a.id) { main = a.id; also.remove(a.id) } }
                }
                Text("Also works (optional)", style = MaterialTheme.typography.labelLarge, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    areas.filter { it.id != main }.forEach { a ->
                        Chip(a.label, selected = a.id in also) { if (a.id in also) also.remove(a.id) else also.add(a.id) }
                    }
                }
                OutlinedTextField(
                    value = equip, onValueChange = { equip = it }, label = { Text("Equipment (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("What you log for it", style = MaterialTheme.typography.labelLarge, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Measure.ALL.forEach { m ->
                        Chip(Measure.name(m), selected = m in measures) {
                            if (m in measures) { if (measures.size > 1) measures.remove(m) } else measures.add(m)
                        }
                    }
                }
                if (original != null) {
                    if (!confirmDelete) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete this movement", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text(
                            if (timesLogged > 0) "It's in $timesLogged logged workouts. Those stay in History; it just leaves the lists."
                            else "It has never been logged.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { onDelete(original) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, main, also.toList(), equip, Measure.ALL.filter { it in measures }) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
