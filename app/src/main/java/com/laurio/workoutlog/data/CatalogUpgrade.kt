package com.laurio.workoutlog.data

/**
 * Brings data already on the phone up to a newer built-in exercise list (catalog.json's
 * "catalogVersion"). Runs once per version bump, so edits he makes in the app afterwards stay.
 *
 * - Exercises in the new list replace the stored ones with the same id (name, areas, equipment,
 *   measures, hidden). New ones are added. Ones he added in the app are kept. The list carries
 *   dropped movements that history uses as hidden, so fresh installs still show their names.
 * - Ids listed in "retired" (dropped and never used) are hidden if a workout uses them, otherwise removed.
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
    ): AppData {
        if (version <= data.catalogVersion) return data

        val listed = catalog.exercises
        val listedIds = listed.map { it.id }.toSet()
        val used = data.sessions.flatMap { s -> s.items.map { it.ex } }.toSet()
        val extras = data.exercises.filter { it.id !in listedIds }.mapNotNull { e ->
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
        val sessions = data.sessions.map { s ->
            val seed = seedByDate[s.date]
            if (seed != null && s.raw.isNotBlank() && s.raw == seed.raw && s.items.size == seed.items.size) {
                seed.copy(note = s.note)
            } else s
        }

        return data.copy(exercises = exercises, areas = areas, sessions = sessions, catalogVersion = version)
    }
}
