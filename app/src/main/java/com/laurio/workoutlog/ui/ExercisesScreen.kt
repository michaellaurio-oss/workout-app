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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.laurio.workoutlog.logic.MONTHS
import com.laurio.workoutlog.logic.ago
import com.laurio.workoutlog.logic.formatCredits
import com.laurio.workoutlog.logic.formatMonths
import java.time.LocalDate

@Composable
fun ExercisesScreen(data: AppData, vm: AppViewModel) {
    val stats = remember(data) { Stats(data) }
    val areaNames = remember(data) { data.areas.associate { it.id to it.label } }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var adding by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val q = search.trim().lowercase()
    fun matches(e: Exercise) = q.isEmpty() || e.name.lowercase().contains(q) || e.equip.lowercase().contains(q)

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
                    "move it to another area, or delete it. How often each area comes up is set on the Areas tab.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add movement")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search movements or equipment") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                    }
                },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        } }
        val shownAreas = data.areas.filter { a -> q.isEmpty() || data.exercises.any { !it.hidden && it.mainArea == a.id && matches(it) } }
        if (shownAreas.isEmpty()) {
            item { Text("No movements match \"${search.trim()}\".", color = Muted) }
        }
        items(shownAreas, key = { it.id }) { a ->
            AreaCard(
                area = a,
                movements = data.exercises.filter { !it.hidden && it.mainArea == a.id && matches(it) }.sortedBy { it.name.lowercase() },
                stats = stats,
                areaNames = areaNames,
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
            onSave = { draft ->
                vm.saveExercise(editing, draft)
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
    onEdit: (Exercise) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    area.label.uppercase(), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                Text(
                    "every ${area.target} days · ${formatCredits(area.credits)} credits",
                    style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
                )
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
                            if (e.everySession) "every session" else null,
                            if (e.pinned) "always first" else null,
                            formatMonths(e.months).ifEmpty { null },
                            e.areas.drop(1).takeIf { it.isNotEmpty() }?.let { also -> "also " + also.joinToString { areaNames[it] ?: it } },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    VideoButton(e.video, e.name)
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
    onSave: (Exercise) -> Unit,
    onDelete: (Exercise) -> Unit,
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var main by remember { mutableStateOf(original?.mainArea ?: areas.first().id) }
    val also = remember { mutableStateListOf<String>().apply { original?.areas?.drop(1)?.let { addAll(it) } } }
    var equip by remember { mutableStateOf(original?.equip ?: "") }
    var video by remember { mutableStateOf(original?.video ?: "") }
    val measures = remember { mutableStateListOf<String>().apply { addAll(original?.measures ?: Measure.DEFAULT) } }
    var confirmDelete by remember { mutableStateOf(false) }
    var every by remember { mutableStateOf(original?.everySession ?: false) }
    var pinned by remember { mutableStateOf(original?.pinned ?: false) }
    val months = remember { mutableStateListOf<Int>().apply { addAll(original?.months ?: emptyList()) } }

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
                OutlinedTextField(
                    value = video, onValueChange = { video = it }, label = { Text("Video link (optional)") },
                    placeholder = { Text("https://youtu.be/…") },
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
                Text("Routine", style = MaterialTheme.typography.labelLarge, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Do every session", selected = every) { every = !every }
                    Chip("Always first in its area", selected = pinned) { pinned = !pinned }
                }
                Text(
                    if (months.isEmpty()) "Season: all year (tap months to limit it)" else "Season: ${formatMonths(months)}",
                    style = MaterialTheme.typography.labelLarge, color = Muted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MONTHS.forEachIndexed { i, label ->
                        val m = i + 1
                        Chip(label, selected = m in months) { if (m in months) months.remove(m) else months.add(m) }
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
                onClick = {
                    onSave(
                        Exercise(
                            id = original?.id ?: "",
                            name = name,
                            areas = listOf(main) + also.filter { it != main },
                            equip = equip,
                            video = video,
                            measures = Measure.ALL.filter { it in measures },
                            everySession = every,
                            pinned = pinned,
                            months = months.sorted(),
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
