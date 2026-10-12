package com.the09.kastoriabus.data

import com.google.gson.Gson
import java.io.File
import java.lang.reflect.Type

/**
 * Saves API answers on the phone as JSON files, so the app works without internet.
 * Each item is one small file in the app's private folder (other apps can't read it,
 * and it's deleted if the app is uninstalled).
 */
class DiskCache(baseDir: File, private val gson: Gson = Gson()) {

    private val dir = File(baseDir, "offline_v1").apply { mkdirs() }

    /** A saved item and when it was saved. */
    data class Entry<T>(val value: T, val savedAt: Long)

    fun <T> read(key: String, type: Type): Entry<T>? {
        val file = fileFor(key)
        if (!file.exists()) return null
        return try {
            Entry(gson.fromJson<T>(file.readText(), type) ?: return null, file.lastModified())
        } catch (e: Exception) {
            file.delete() // a broken file (e.g. phone switched off while writing): forget it
            null
        }
    }

    fun write(key: String, value: Any) {
        // Write to a temporary file first, then rename: a half-written file never replaces a good one.
        val tmp = File(dir, "${fileName(key)}.tmp")
        tmp.writeText(gson.toJson(value))
        tmp.renameTo(fileFor(key))
    }

    /** Total size of everything saved, in bytes. */
    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun fileFor(key: String) = File(dir, fileName(key))

    // Keys look like "timetable|el|16044|1"; keep only safe characters for the file name.
    private fun fileName(key: String) = key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json"
}
