package com.the09.kastoriabus.data

import com.google.gson.reflect.TypeToken
import com.the09.kastoriabus.data.estimate.LatLon
import com.the09.kastoriabus.data.estimate.RoutePlan
import com.the09.kastoriabus.data.estimate.RoutePlanBuilder
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.LiveVehicle
import com.the09.kastoriabus.data.model.RouteShape
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.data.model.Trip
import com.the09.kastoriabus.data.remote.CitybusApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * The one place the screens ask for bus data. It hides the API details and makes the
 * app work without internet: every answer is kept in memory AND saved on the phone
 * ([DiskCache]). For each request it tries, in order:
 *   1. memory (instant)
 *   2. the saved copy, if it's less than 7 days old (no network at all: fast, and gentle on the server)
 *   3. the internet (and saves the answer)
 *   4. the saved copy even if it's old, when there is no internet
 *
 * All functions are `suspend`: call them from a coroutine. They throw an exception
 * (usually IOException) only when there's no internet AND nothing saved.
 *
 * @param language returns "el" or "en", read each time so it follows the phone's language.
 */
class BusRepository(
    private val api: CitybusApi,
    private val disk: DiskCache,
    private val language: () -> String,
) {
    private val memory = ConcurrentHashMap<String, Any>()
    // One lock per item, so two screens asking at the same time cause only one download.
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val routePlanCache = ConcurrentHashMap<String, RoutePlan>()
    // At most 3 timetable downloads at the same time, to be gentle with the server.
    private val downloadPermits = Semaphore(3)

    private val _usingSavedData = MutableStateFlow(false)
    /** True after a download failed and we showed saved data instead (= no internet). */
    val usingSavedData: StateFlow<Boolean> = _usingSavedData.asStateFlow()

    suspend fun getStops(forceRefresh: Boolean = false): List<Stop> {
        val lang = language()
        return fetch("stops|$lang", typeOf<List<Stop>>(), forceRefresh, fallbackKeys = listOf("stops|${other(lang)}")) {
            api.getStops(lang)
        }
    }

    suspend fun getLines(forceRefresh: Boolean = false): List<Line> {
        val lang = language()
        return fetch("lines|$lang", typeOf<List<Line>>(), forceRefresh, fallbackKeys = listOf("lines|${other(lang)}")) {
            api.getLines(lang)
        }
    }

    /** Map shapes of every route of a line. Same in both languages. */
    suspend fun getLineShapes(lineCode: String, forceRefresh: Boolean = false): List<RouteShape> =
        fetch("shapes|$lineCode", typeOf<List<RouteShape>>(), forceRefresh) { api.getLineShapes(lineCode) }

    /** The stop codes of a route in driving order (the same in both languages). */
    suspend fun getRouteStopCodes(routeCode: String, forceRefresh: Boolean = false): List<String> =
        fetch("sequence|$routeCode", typeOf<List<String>>(), forceRefresh) {
            api.getRouteSequence(language(), routeCode).sortedBy { it.sequence }.map { it.code }
        }

    /** The stops of a route, in driving order, with names and coordinates. */
    suspend fun getRouteStops(routeCode: String): List<Stop> {
        val codes = getRouteStopCodes(routeCode)
        val stopsByCode = getStops().associateBy { it.code }
        return codes.mapNotNull { stopsByCode[it] }
    }

    /** Timetable of a stop. [day]: 0 = Sunday ... 6 = Saturday; defaults to today. */
    suspend fun getTimetable(stopCode: String, day: Int = todayDayNumber(), forceRefresh: Boolean = false): List<Trip> {
        val lang = language()
        return fetch(
            "timetable|$lang|$stopCode|$day", typeOf<List<Trip>>(), forceRefresh,
            fallbackKeys = listOf("timetable|${other(lang)}|$stopCode|$day"),
        ) {
            downloadPermits.withPermit { api.getStopTimetable(lang, stopCode, day) }
        }
    }

    /**
     * Everything needed to estimate where today's buses of one route are (see [RoutePlan]).
     * Built from saved data when possible, so it works offline too.
     */
    suspend fun getRoutePlan(lineCode: String, routeCode: String): RoutePlan {
        val key = "$routeCode|${today()}"
        routePlanCache[key]?.let { return it }

        val stops = getRouteStops(routeCode)
        val shape = getLineShapes(lineCode).firstOrNull { it.routeCode == routeCode }
            ?.routePoints?.sortedBy { it.sequence }?.map { LatLon(it.latitude, it.longitude) }
            .orEmpty()
        val timesAtAnchors = RoutePlanBuilder.chooseAnchorIndices(stops.map { it.code })
            .associateWith { index ->
                getTimetable(stops[index].code)
                    .filter { it.routeCode == routeCode }
                    .map { it.tripTimeHour * 3600 + it.tripTimeMinute * 60 }
            }
        return RoutePlanBuilder.build(
            routeCode = routeCode,
            stops = stops.map { Triple(it.code, it.name, LatLon(it.latitude, it.longitude)) },
            shape = shape,
            timesAtAnchors = timesAtAnchors,
        ).also { routePlanCache[key] = it }
    }

    /** Buses coming to a stop in the next ~30 minutes. Never saved: it's live. */
    suspend fun getLiveArrivals(stopCode: String): List<LiveVehicle> {
        val response = api.getLiveArrivals(language(), stopCode)
        return when {
            response.isSuccessful -> response.body()?.vehicles.orEmpty()
            response.code() == 404 -> emptyList() // the API's way of saying "no buses"
            else -> throw HttpException(response)
        }
    }

    /** How much space the saved data takes on the phone, in bytes. */
    suspend fun savedDataSize(): Long = withContext(Dispatchers.IO) { disk.sizeBytes() }

    /** The heart of offline mode: memory -> fresh saved copy -> internet -> old saved copy. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> fetch(
        key: String,
        type: Type,
        forceRefresh: Boolean,
        fallbackKeys: List<String> = emptyList(),
        download: suspend () -> T,
    ): T {
        if (!forceRefresh) (memory[key] as T?)?.let { return it }
        return locks.computeIfAbsent(key) { Mutex() }.withLock {
            if (!forceRefresh) {
                (memory[key] as T?)?.let { return@withLock it }
                val saved = readDisk<T>(key, type)
                if (saved != null && System.currentTimeMillis() - saved.savedAt < MAX_AGE_MS) {
                    memory[key] = saved.value
                    return@withLock saved.value
                }
            }
            try {
                val fresh = download()
                withContext(Dispatchers.IO) { disk.write(key, fresh) }
                memory[key] = fresh
                _usingSavedData.value = false
                fresh
            } catch (e: Exception) {
                if (e !is IOException && e !is HttpException) throw e
                // "Download fresh" (the offline download button) must really download.
                if (forceRefresh) throw e
                // No internet (or the server failed): use whatever we saved, however old.
                val saved = (listOf(key) + fallbackKeys).firstNotNullOfOrNull { readDisk<T>(it, type) }
                    ?: throw e
                _usingSavedData.value = true
                memory[key] = saved.value
                saved.value
            }
        }
    }

    private suspend fun <T> readDisk(key: String, type: Type): DiskCache.Entry<T>? =
        withContext(Dispatchers.IO) { disk.read(key, type) }

    private fun other(lang: String) = if (lang == "el") "en" else "el"

    private inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type

    companion object {
        val ATHENS: ZoneId = ZoneId.of("Europe/Athens")

        /** Saved data younger than this is used without asking the server. */
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        fun today(): LocalDate = LocalDate.now(ATHENS)

        /** Today's day number in Kastoria, in the API's numbering (0 = Sunday). */
        fun todayDayNumber(): Int =
            // java.time: Monday = 1 ... Sunday = 7, so "% 7" turns Sunday into 0.
            today().dayOfWeek.value % 7
    }
}
