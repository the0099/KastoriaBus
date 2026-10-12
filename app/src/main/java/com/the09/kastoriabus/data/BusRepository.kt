package com.the09.kastoriabus.data

import com.the09.kastoriabus.data.estimate.LatLon
import com.the09.kastoriabus.data.estimate.RoutePlan
import com.the09.kastoriabus.data.estimate.RoutePlanBuilder
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.LiveVehicle
import com.the09.kastoriabus.data.model.RouteShape
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.data.model.Trip
import com.the09.kastoriabus.data.remote.CitybusApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * The one place the screens ask for bus data. It hides the API details and keeps
 * the data that rarely changes (stops, lines, route shapes) in memory, so we don't
 * download it again on every screen. (Saving it on the phone with Room comes later.)
 *
 * All functions are `suspend`: call them from a coroutine. They throw an exception
 * (usually IOException or HttpException) when the network call fails.
 *
 * @param language returns "el" or "en", read each time so it follows the phone's language.
 */
class BusRepository(
    private val api: CitybusApi,
    private val language: () -> String,
) {
    // Caches are keyed by language ("el"/"en") or by line/route code.
    private val stopsCache = mutableMapOf<String, List<Stop>>()
    private val linesCache = mutableMapOf<String, List<Line>>()
    private val shapesCache = mutableMapOf<String, List<RouteShape>>()
    private val sequenceCache = mutableMapOf<String, List<String>>()
    // Makes sure two screens asking at the same time cause only one download.
    private val cacheMutex = Mutex()
    // A stop's timetable doesn't change during the day, so keep it until midnight.
    // Keyed by "language|stop|day|date".
    private val timetableCache = ConcurrentHashMap<String, List<Trip>>()
    private val routePlanCache = ConcurrentHashMap<String, RoutePlan>()
    // At most 3 timetable downloads at the same time, to be gentle with the server.
    private val downloadPermits = Semaphore(3)

    suspend fun getStops(forceRefresh: Boolean = false): List<Stop> {
        val lang = language()
        return cached(stopsCache, lang, forceRefresh) { api.getStops(lang) }
    }

    suspend fun getLines(forceRefresh: Boolean = false): List<Line> {
        val lang = language()
        return cached(linesCache, lang, forceRefresh) { api.getLines(lang) }
    }

    /** Map shapes of every route of a line. Same in both languages. */
    suspend fun getLineShapes(lineCode: String): List<RouteShape> =
        cached(shapesCache, lineCode) { api.getLineShapes(lineCode) }

    /** The stops of a route, in driving order, with names and coordinates. */
    suspend fun getRouteStops(routeCode: String): List<Stop> {
        val codes = cached(sequenceCache, routeCode) {
            api.getRouteSequence(language(), routeCode)
                .sortedBy { it.sequence }
                .map { it.code }
        }
        val stopsByCode = getStops().associateBy { it.code }
        return codes.mapNotNull { stopsByCode[it] }
    }

    /** Timetable of a stop. [day]: 0 = Sunday ... 6 = Saturday; defaults to today. */
    suspend fun getTimetable(stopCode: String, day: Int = todayDayNumber()): List<Trip> {
        val lang = language()
        val key = "$lang|$stopCode|$day|${today()}"
        timetableCache[key]?.let { return it }
        return downloadPermits.withPermit { api.getStopTimetable(lang, stopCode, day) }
            .also { timetableCache[key] = it }
    }

    /**
     * Everything needed to estimate where today's buses of one route are (see [RoutePlan]).
     * Downloads the timetables of a few checkpoint stops the first time; after that
     * it's cached until midnight.
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

    /** Buses coming to a stop in the next ~30 minutes. Never cached: it's live. */
    suspend fun getLiveArrivals(stopCode: String): List<LiveVehicle> {
        val response = api.getLiveArrivals(language(), stopCode)
        return when {
            response.isSuccessful -> response.body()?.vehicles.orEmpty()
            response.code() == 404 -> emptyList() // the API's way of saying "no buses"
            else -> throw HttpException(response)
        }
    }

    private suspend fun <T> cached(
        cache: MutableMap<String, T>,
        key: String,
        forceRefresh: Boolean = false,
        load: suspend () -> T,
    ): T = cacheMutex.withLock {
        if (!forceRefresh) cache[key]?.let { return@withLock it }
        load().also { cache[key] = it }
    }

    companion object {
        val ATHENS: ZoneId = ZoneId.of("Europe/Athens")

        fun today(): LocalDate = LocalDate.now(ATHENS)

        /** Today's day number in Kastoria, in the API's numbering (0 = Sunday). */
        fun todayDayNumber(): Int =
            // java.time: Monday = 1 ... Sunday = 7, so "% 7" turns Sunday into 0.
            today().dayOfWeek.value % 7
    }
}
