package com.laurio.workoutlog.data

/**
 * Brings data already on the phone up to a newer built-in exercise list (catalog.json's
 * "catalogVersion"). Runs once per version bump, so edits he makes in the app afterwards stay.
 *
 * - Exercises in the new list replace the stored ones with the same id (name, areas, equipment,
 *   measures, hidden). New ones are added. Ones he added in the app are kept. The list carries
 *   dropped movements that history uses as hidden, so fresh installs still show their names.
 * - Ids listed in "retired" (dropped and never used) are hidden if a workout uses them, otherwise removed.
 * - "merged" maps an old id to the movement it was folded into: logged workouts move over and the
 *   old movement goes away.
 * - Areas keep his targets; labels and new areas come from the list.
 * - Days from the starter history that he hasn't changed (same original text and number of
 *   movements) are replaced by the starter version, which picks up better-parsed numbers.
 */
object CatalogUpgrade {
    fun apply(
        data: AppData,
        catalog: AppData,
        retired: Set<String>,
        version: Int,
        history: List<Session>,
        merged: Map<String, String> = emptyMap(),
    ): AppData {
        if (version <= data.catalogVersion) return data
        val remapped = data.sessions.map { s ->
            if (s.items.none { it.ex in merged }) s
            else s.copy(items = s.items.map { i -> merged[i.ex]?.let { i.copy(ex = it) } ?: i })
        }

        // "Every session" and "pinned" stay on if either the list or his own setting turns them on;
        // his season months stay unless the list sets some.
        val mine = data.exercises.associateBy { it.id }
        val listed = catalog.exercises.map { e ->
            val m = mine[e.id] ?: return@map e
            e.copy(
                everySession = e.everySession || m.everySession,
                pinned = e.pinned || m.pinned,
                months = e.months.ifEmpty { m.months },
            )
        }
        val listedIds = listed.map { it.id }.toSet()
        val used = remapped.flatMap { s -> s.items.map { it.ex } }.toSet()
        val extras = data.exercises.filter { it.id !in listedIds && it.id !in merged }.mapNotNull { e ->
            when {
                e.id !in retired -> e // he added it in the app
                e.id in used -> e.copy(hidden = true)
                else -> null
            }
        }
        val exercises = listed + extras

        val oldAreas = data.areas.associateBy { it.id }
        val areas = catalog.areas.map { a -> oldAreas[a.id]?.let { a.copy(target = it.target) } ?: a } +
            data.areas.filter { a -> catalog.areas.none { it.id == a.id } }

        val seedByDate = history.associateBy { it.date }
        val sessions = remapped.map { s ->
            val seed = seedByDate[s.date]
            if (seed != null && s.raw.isNotBlank() && s.raw == seed.raw && s.items.size == seed.items.size) {
                seed.copy(note = s.note)
            } else s
        }

        return data.copy(exercises = exercises, areas = areas, sessions = sessions, catalogVersion = version)
    }
}
