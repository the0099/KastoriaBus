package com.the09.kastoriabus.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The user's favorite stops and lines, saved on the phone (SharedPreferences),
 * so they are still there after the app is closed.
 *
 * The codes are exposed as StateFlows: every screen that shows a heart
 * updates by itself when a favorite is added or removed anywhere.
 */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _stopCodes = MutableStateFlow(load(KEY_STOPS))
    val stopCodes: StateFlow<Set<String>> = _stopCodes.asStateFlow()

    private val _lineCodes = MutableStateFlow(load(KEY_LINES))
    val lineCodes: StateFlow<Set<String>> = _lineCodes.asStateFlow()

    fun toggleStop(stopCode: String) = toggle(_stopCodes, KEY_STOPS, stopCode)

    fun toggleLine(lineCode: String) = toggle(_lineCodes, KEY_LINES, lineCode)

    private fun toggle(flow: MutableStateFlow<Set<String>>, key: String, code: String) {
        flow.update { codes -> if (code in codes) codes - code else codes + code }
        prefs.edit().putStringSet(key, flow.value).apply()
    }

    // Copy the set: Android says not to keep the set that getStringSet returns.
    private fun load(key: String): Set<String> =
        prefs.getStringSet(key, emptySet()).orEmpty().toSet()

    private companion object {
        const val KEY_STOPS = "stops"
        const val KEY_LINES = "lines"
    }
}
