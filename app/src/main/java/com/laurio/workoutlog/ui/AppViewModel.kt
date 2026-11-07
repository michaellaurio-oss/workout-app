package com.laurio.workoutlog.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.data.Area
import com.laurio.workoutlog.data.Exercise
import com.laurio.workoutlog.data.Item
import com.laurio.workoutlog.data.Measure
import com.laurio.workoutlog.data.Session
import com.laurio.workoutlog.data.SetGroup
import com.laurio.workoutlog.data.Store
import com.laurio.workoutlog.logic.formatWeight
import com.laurio.workoutlog.logic.parseTime
import com.laurio.workoutlog.logic.timeForField
import com.laurio.workoutlog.logic.prettyDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** One line being typed in (sets, reps, weight, time...). Kept as text so the fields edit naturally. */
data class GroupDraft(
    val sets: String = "",
    val reps: String = "",
    val weight: String = "",
    val time: String = "",
    val band: String = "",
    val resistance: String = "",
    val speed: String = "",
    val incline: String = "",
) {
    fun get(m: String): String = when (m) {
        Measure.SETS -> sets
        Measure.REPS -> reps
        Measure.WEIGHT -> weight
        Measure.TIME -> time
        Measure.BAND -> band
        Measure.RESISTANCE -> resistance
        Measure.SPEED -> speed
        Measure.INCLINE -> incline
        else -> ""
    }

    fun with(m: String, v: String): GroupDraft = when (m) {
        Measure.SETS -> copy(sets = v)
        Measure.REPS -> copy(reps = v)
        Measure.WEIGHT -> copy(weight = v)
        Measure.TIME -> copy(time = v)
        Measure.BAND -> copy(band = v)
        Measure.RESISTANCE -> copy(resistance = v)
        Measure.SPEED -> copy(speed = v)
        Measure.INCLINE -> copy(incline = v)
        else -> this
    }

    /** Only the fields this movement measures are kept. */
    fun toGroup(measures: List<String>, minutes: Boolean): SetGroup {
        fun d(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
        fun has(m: String) = m in measures
        return SetGroup(
            sets = if (has(Measure.SETS)) sets.trim().toIntOrNull() else null,
            reps = if (has(Measure.REPS)) reps.trim().toIntOrNull() else null,
            weight = if (has(Measure.WEIGHT)) d(weight) else null,
            time = if (has(Measure.TIME)) parseTime(time, minutes) else null,
            band = if (has(Measure.BAND)) band.trim().ifEmpty { null } else null,
            resistance = if (has(Measure.RESISTANCE)) d(resistance) else null,
            speed = if (has(Measure.SPEED)) d(speed) else null,
            incline = if (has(Measure.INCLINE)) d(incline) else null,
        )
    }

    companion object {
        fun from(g: SetGroup, minutes: Boolean) = GroupDraft(
            sets = g.sets?.toString() ?: "",
            reps = g.reps?.toString() ?: "",
            weight = g.weight?.let { formatWeight(it) } ?: "",
            time = g.time?.let { timeForField(it, minutes) } ?: "",
            band = g.band ?: "",
            resistance = g.resistance?.let { formatWeight(it) } ?: "",
            speed = g.speed?.let { formatWeight(it) } ?: "",
            incline = g.incline?.let { formatWeight(it) } ?: "",
        )
    }
}

/** A movement picked for the workout being logged. */
data class Pick(val ex: String, val groups: List<GroupDraft> = listOf(GroupDraft()), val note: String = "")

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val saveLock = Mutex()

    private val _data = MutableStateFlow<AppData?>(null)
    val data: StateFlow<AppData?> = _data

    /** One-off messages for the snackbar. */
    val message = MutableStateFlow<String?>(null)

    // The workout being put together (survives switching tabs).
    val picked = mutableStateListOf<Pick>()
    var logDate by mutableStateOf(LocalDate.now())
    var logNote by mutableStateOf("")

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _data.value = try {
                store.load()
            } catch (e: Exception) {
                message.value = "Couldn't load your workouts: ${e.message}"
                null
            }
        }
    }

    /** Applies a change in memory right away, then writes the file in the background. */
    private fun change(msg: String? = null, f: (AppData) -> AppData) {
        val cur = _data.value ?: return
        _data.value = f(cur)
        if (msg != null) message.value = msg
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock {
                try {
                    _data.value?.let { store.save(it) }
                } catch (e: Exception) {
                    message.value = "Couldn't save: ${e.message}"
                }
            }
        }
    }

    // ---- picking movements ----

    fun isPicked(id: String) = picked.any { it.ex == id }

    /** Adds a movement, pre-filled with what he did last time (so he can just bump the numbers). */
    fun togglePick(id: String) {
        val i = picked.indexOfFirst { it.ex == id }
        if (i >= 0) {
            picked.removeAt(i)
            return
        }
        val last = _data.value?.sessions
            ?.firstOrNull { s -> s.items.any { it.ex == id && it.groups.any { g -> !g.isEmpty } } }
            ?.items?.last { it.ex == id }
        val minutes = _data.value?.exercises?.firstOrNull { it.id == id }?.timeInMinutes ?: false
        val groups = last?.groups?.filter { !it.isEmpty }?.map { GroupDraft.from(it, minutes) }
        picked.add(Pick(id, if (groups.isNullOrEmpty()) listOf(GroupDraft()) else groups))
    }

    private fun editPick(id: String, f: (Pick) -> Pick) {
        val i = picked.indexOfFirst { it.ex == id }
        if (i >= 0) picked[i] = f(picked[i])
    }

    fun setGroup(id: String, index: Int, g: GroupDraft) = editPick(id) { p ->
        p.copy(groups = p.groups.mapIndexed { i, old -> if (i == index) g else old })
    }

    fun addGroup(id: String) = editPick(id) { p -> p.copy(groups = p.groups + (p.groups.lastOrNull() ?: GroupDraft())) }

    fun removeGroup(id: String, index: Int) = editPick(id) { p ->
        val left = p.groups.filterIndexed { i, _ -> i != index }
        p.copy(groups = left.ifEmpty { listOf(GroupDraft()) })
    }

    fun setPickNote(id: String, note: String) = editPick(id) { it.copy(note = note) }

    fun canSave() = picked.isNotEmpty() || logNote.isNotBlank()

    fun saveWorkout() {
        val date = logDate.toString()
        val exById = _data.value?.exercises?.associateBy { it.id } ?: emptyMap()
        val items = picked.map { p ->
            val e = exById[p.ex]
            val measures = e?.measures ?: Measure.DEFAULT
            Item(p.ex, p.groups.map { it.toGroup(measures, e?.timeInMinutes ?: false) }.filter { !it.isEmpty }, p.note.trim())
        }
        val note = logNote.trim()
        if (items.isEmpty() && note.isEmpty()) return
        change("Saved ${prettyDate(date)}") { d -> d.copy(sessions = mergeDay(d.sessions, date, items, emptyList(), note)) }
        picked.clear()
        logNote = ""
        logDate = LocalDate.now()
    }

    /** Logs an area without specific movements, like "upper" in the old spreadsheet. */
    fun logArea(areaId: String) {
        val date = logDate.toString()
        val label = _data.value?.areas?.firstOrNull { it.id == areaId }?.label ?: areaId
        change("Logged $label for ${prettyDate(date)}") { d ->
            d.copy(sessions = mergeDay(d.sessions, date, emptyList(), listOf(areaId), ""))
        }
    }

    private fun mergeDay(list: List<Session>, date: String, items: List<Item>, areas: List<String>, note: String): List<Session> {
        val old = list.firstOrNull { it.date == date }
        val merged = Session(
            date = date,
            items = (old?.items ?: emptyList()) + items,
            areas = ((old?.areas ?: emptyList()) + areas).distinct(),
            raw = old?.raw ?: "",
            tag = old?.tag ?: "",
            note = listOf(old?.note ?: "", note).filter { it.isNotBlank() }.joinToString(" · "),
        )
        return (list.filter { it.date != date } + merged).sortedByDescending { it.date }
    }

    fun deleteDay(date: String) = change("Deleted ${prettyDate(date)}") { d ->
        d.copy(sessions = d.sessions.filter { it.date != date })
    }

    /** Removes one movement from a logged day (the day goes too if nothing is left). */
    fun deleteItem(date: String, index: Int) = change("Removed from ${prettyDate(date)}") { d ->
        d.copy(sessions = d.sessions.mapNotNull { s ->
            if (s.date != date) return@mapNotNull s
            val left = s.copy(items = s.items.filterIndexed { i, _ -> i != index })
            if (left.items.isEmpty() && left.areas.isEmpty() && left.note.isBlank() && left.raw.isBlank()) null else left
        })
    }

    // ---- exercise list and areas ----

    fun setTarget(areaId: String, days: Int) = change { d ->
        d.copy(areas = d.areas.map { if (it.id == areaId) it.copy(target = days.coerceIn(1, 30)) else it })
    }

    fun setCredits(areaId: String, credits: Double) = change { d ->
        val c = (Math.round(credits * 2) / 2.0).coerceIn(Area.MIN_CREDITS, Area.MAX_CREDITS)
        d.copy(areas = d.areas.map { if (it.id == areaId) it.copy(credits = c) else it })
    }

    /**
     * Adds a movement ([original] == null) or edits one, from the dialog's [draft] (its id is ignored).
     * History keeps pointing at the same id.
     */
    fun saveExercise(original: Exercise?, draft: Exercise) {
        val cleanName = draft.name.trim()
        if (cleanName.isEmpty()) return
        val clean = draft.copy(
            name = cleanName,
            areas = draft.areas.distinct(),
            equip = draft.equip.trim(),
            measures = Measure.ALL.filter { it in draft.measures }.ifEmpty { Measure.DEFAULT },
            months = draft.months.filter { it in 1..12 }.distinct().sorted().let { if (it.size == 12) emptyList() else it },
        )
        change(if (original == null) "Added $cleanName" else "Saved $cleanName") { d ->
            if (original == null) {
                var id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "movement" }
                while (d.exercises.any { it.id == id }) id += "-2"
                d.copy(exercises = d.exercises + clean.copy(id = id, hidden = false))
            } else {
                d.copy(exercises = d.exercises.map {
                    if (it.id == original.id) clean.copy(id = original.id, hidden = original.hidden) else it
                })
            }
        }
    }

    /** Deletes a movement. If history uses it, it's hidden instead so old workouts still show its name. */
    fun removeExercise(e: Exercise) {
        picked.removeAll { it.ex == e.id }
        change("Removed ${e.name}") { d ->
            val used = d.sessions.any { s -> s.items.any { it.ex == e.id } }
            d.copy(exercises = if (used) d.exercises.map { if (it.id == e.id) it.copy(hidden = true) else it }
            else d.exercises.filter { it.id != e.id })
        }
    }

    // ---- backup ----

    fun export(uri: Uri) {
        val d = _data.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            message.value = try {
                store.export(uri, d)
                "Backup saved"
            } catch (e: Exception) {
                "Backup failed: ${e.message}"
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val d = store.read(uri)
                change("Restored ${d.sessions.size} workout days") { d }
            } catch (e: Exception) {
                message.value = "That file isn't a Workout Log backup (${e.message})"
            }
        }
    }
}
