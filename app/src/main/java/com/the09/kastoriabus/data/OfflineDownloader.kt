package com.the09.kastoriabus.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/** What the offline download is doing, for the screen. */
data class OfflineStatus(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val mapProgress: Float = 0f,
    val failed: Boolean = false,
    val lastCompletedAt: Long? = null, // when the last full download finished
    val savedBytes: Long = 0,
)

/**
 * The "Download for offline use" button: saves everything the app needs on the phone.
 * Stops, lines, all route stop lists and drawings, the timetable of every stop for every
 * day of the week (~1,460 small downloads, at most 3 at a time), and the Kastoria map.
 *
 * It runs in the app-wide [scope], so it keeps going when you switch screens.
 */
class OfflineDownloader(
    context: Context,
    private val repository: BusRepository,
    private val mapDownloader: OfflineMapDownloader,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(
        OfflineStatus(lastCompletedAt = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 })
    )
    val status: StateFlow<OfflineStatus> = _status.asStateFlow()
    private var job: Job? = null

    init {
        scope.launch { refreshSize() }
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            _status.update { it.copy(running = true, failed = false, done = 0, total = 0, mapProgress = 0f) }
            try {
                downloadData()
                mapDownloader.download { progress -> _status.update { it.copy(mapProgress = progress) } }
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST, now).apply()
                _status.update { it.copy(running = false, lastCompletedAt = now, mapProgress = 1f) }
            } catch (e: CancellationException) {
                _status.update { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                Log.w("KastoriaBus", "Offline download failed", e)
                _status.update { it.copy(running = false, failed = true) }
            }
            refreshSize()
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun downloadData() {
        // Small things first, always fresh (forceRefresh), because the user asked for an update.
        val stops = repository.getStops(forceRefresh = true)
        val lines = repository.getLines(forceRefresh = true)
        val routes = lines.flatMap { it.routes }

        val total = 2 + lines.size + routes.size + stops.size * 7
        val done = AtomicInteger(2)
        fun step() = _status.update { it.copy(done = done.incrementAndGet(), total = total) }
        _status.update { it.copy(done = 2, total = total) }

        lines.forEach { repository.getLineShapes(it.code, forceRefresh = true); step() }
        routes.forEach { repository.getRouteStopCodes(it.code, forceRefresh = true); step() }

        // The big part: every stop, every day. The repository allows 3 downloads at a time.
        coroutineScope {
            stops.flatMap { stop -> (0..6).map { day -> stop.code to day } }
                .map { (code, day) ->
                    async {
                        repository.getTimetable(code, day, forceRefresh = true)
                        step()
                    }
                }
                .awaitAll()
        }
    }

    private suspend fun refreshSize() {
        val size = repository.savedDataSize()
        _status.update { it.copy(savedBytes = size) }
    }

    private companion object {
        const val KEY_LAST = "last_completed_at"
    }
}
