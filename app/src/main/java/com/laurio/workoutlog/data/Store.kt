package com.laurio.workoutlog.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * Keeps all data in workouts.json in the app's private storage. On first launch (no file yet)
 * it starts from the starter files in assets (see [starterJson]). When a newer built-in exercise
 * list ships (catalog.json "catalogVersion"), existing data is updated once ([CatalogUpgrade]).
 */
class Store(private val context: Context) {
    private val file = File(context.filesDir, "workouts.json")

    @Synchronized
    fun load(): AppData {
        if (file.exists()) {
            val data = try {
                AppData.fromJson(JSONObject(file.readText()))
            } catch (e: Exception) {
                // Only a file that can't be read at all is set aside; keep it instead of losing it.
                runCatching { file.copyTo(File(file.parentFile, "workouts-unreadable-${System.currentTimeMillis()}.json")) }
                null
            }
            if (data != null) {
                // A problem applying a newer exercise list must never cost him his log: keep the data as it is.
                val upgraded = try {
                    upgrade(data)
                } catch (e: Exception) {
                    data
                }
                if (upgraded !== data) {
                    runCatching { file.copyTo(File(file.parentFile, "workouts-before-catalog-${upgraded.catalogVersion}.json"), overwrite = true) }
                    save(upgraded)
                }
                return upgraded
            }
        }
        val seed = AppData.fromJson(starterJson())
        save(seed)
        return seed
    }

    private fun upgrade(data: AppData): AppData {
        val catalog = JSONObject(asset("catalog.json") ?: return data)
        val version = catalog.optInt("catalogVersion", 1)
        if (version <= data.catalogVersion) return data
        val retired = catalog.optJSONArray("retired")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val merged = catalog.optJSONObject("merged")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } } ?: emptyMap()
        return CatalogUpgrade.apply(data, AppData.fromJson(catalog), retired, version, AppData.fromJson(starterJson()).sessions, merged)
    }

    /**
     * catalog.json (body areas + exercise list) is in the repo. history.json (his past workouts) is
     * git-ignored and only on his PC, so builds made elsewhere simply start with an empty log.
     */
    private fun starterJson(): JSONObject {
        val json = JSONObject(asset("catalog.json") ?: error("catalog.json is missing from the app"))
        asset("history.json")?.let { h -> JSONObject(h).optJSONArray("sessions")?.let { json.put("sessions", it) } }
        return json
    }

    private fun asset(name: String): String? =
        try {
            context.assets.open(name).bufferedReader().use { it.readText() }
        } catch (e: java.io.IOException) {
            null
        }

    @Synchronized
    fun save(data: AppData) {
        val text = data.toJson().toString()
        val tmp = File(file.parentFile, "workouts.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    /** Writes a backup to a file the user picked (Downloads, Drive, ...). */
    fun export(uri: Uri, data: AppData) {
        // "wt" (write + truncate) is the safe mode, but some storage apps only accept plain "w".
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w")
            ?: error("Couldn't open that file")
        out.use { it.write(data.toJson().toString(1).toByteArray()) }
    }

    /** Reads a backup file. Throws if it isn't a Workout Log backup. */
    fun read(uri: Uri): AppData {
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open that file")
        return input.bufferedReader().use { AppData.fromJson(JSONObject(it.readText())) }
    }
}
