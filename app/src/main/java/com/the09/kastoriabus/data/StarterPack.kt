package com.the09.kastoriabus.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.lang.reflect.Type

/**
 * A copy of the stops, lines and route stop lists that ships inside the app
 * (app/src/main/assets/starter, saved from the API on 2026-10-12).
 *
 * It's the last resort when there's no internet (or the server is broken, like /stops
 * returning an empty answer on 2026-10-12) AND nothing is saved on the phone yet, e.g.
 * right after installing. The app always prefers fresh data from the server.
 * Stop names are in Greek only; the timetables are not included (too big).
 *
 * @param open opens a file from the assets folder (a lambda so tests can fake it).
 */
class StarterPack(private val open: (String) -> InputStream?, private val gson: Gson = Gson()) {

    // Route code -> stop codes in driving order. Read once, the first time it's needed.
    private val sequences: Map<String, List<String>> by lazy {
        readFile("starter/sequences.json", object : TypeToken<Map<String, List<String>>>() {}.type) ?: emptyMap()
    }

    /** The built-in copy for a [BusRepository] key, or null if the pack doesn't have it. */
    fun <T> read(key: String, type: Type): T? {
        val parts = key.split('|')
        @Suppress("UNCHECKED_CAST")
        return when (parts[0]) {
            "stops" -> readFile("starter/stops_el.json", type)
            "lines" -> readFile("starter/lines_el.json", type)
            "sequence" -> sequences[parts.getOrNull(1)] as T?
            else -> null
        }
    }

    private fun <T> readFile(path: String, type: Type): T? = try {
        open(path)?.bufferedReader(Charsets.UTF_8)?.use { gson.fromJson<T>(it, type) }
    } catch (e: Exception) {
        null
    }
}
