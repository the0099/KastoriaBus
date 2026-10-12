package com.the09.kastoriabus.ui.linedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.data.Direction
import com.the09.kastoriabus.data.FavoritesStore
import com.the09.kastoriabus.data.RouteDirections
import com.the09.kastoriabus.data.RouteSplit
import com.the09.kastoriabus.data.estimate.LatLon
import com.the09.kastoriabus.data.estimate.RoutePlan
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.Route
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.loadCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/** One row of the timeline: a stop, and its position in the whole route. */
data class TimelineStop(val routeIndex: Int, val stop: Stop)

/** A line and, for each of its routes, the stops and how they split into the two directions. */
data class LineData(
    val line: Line,
    val routeStops: Map<String, List<Stop>>,
    val splits: Map<String, RouteSplit>,
)

data class LineDetailUiState(
    val line: LoadState<Line> = LoadState.Loading,
    // The Μετάβαση / Επιστροφή tabs: only the directions this line has (empty = town loop, no tabs).
    val directions: List<Direction> = emptyList(),
    val selectedDirection: Direction? = null,
    // The routes that run in the chosen direction, and the chosen one.
    val routes: List<Route> = emptyList(),
    val selectedRouteCode: String? = null,
    // The stops of the chosen route in the chosen direction.
    val stops: List<TimelineStop> = emptyList(),
    val isFavorite: Boolean = false,
    val favoriteStopCodes: Set<String> = emptySet(),
    // Estimated buses of the chosen route: route stop index they are heading to -> seconds until they get there.
    val busesByNextStop: Map<Int, Int> = emptyMap(),
)

/** The logic of the line screen: the two directions, the route variants and their stops. */
class LineDetailViewModel(
    private val lineCode: String,
    private val repository: BusRepository,
    private val favorites: FavoritesStore,
) : ViewModel() {

    private val data = MutableStateFlow<LoadState<LineData>>(LoadState.Loading)
    private val selectedDirection = MutableStateFlow<Direction?>(null)
    private val selectedRoute = MutableStateFlow<String?>(null)
    private val plan = MutableStateFlow<RoutePlan?>(null)
    private var planJob: Job? = null
    private val clock = flow {
        while (true) {
            emit(LocalTime.now(BusRepository.ATHENS).toSecondOfDay())
            delay(10_000)
        }
    }

    private val baseState = combine(
        data, selectedDirection, selectedRoute, favorites.lineCodes, favorites.stopCodes
    ) { data, direction, routeCode, favoriteLines, favoriteStops ->
        val ready = (data as? LoadState.Ready)?.data
        val routes = ready?.let { routesFor(it, direction) }.orEmpty()
        val routeStops = ready?.routeStops?.get(routeCode).orEmpty()
        val range = ready?.splits?.get(routeCode)?.let { split ->
            if (direction == null) routeStops.indices else split.range(direction, routeStops.size)
        }
        LineDetailUiState(
            line = when (data) {
                LoadState.Loading -> LoadState.Loading
                LoadState.Error -> LoadState.Error
                is LoadState.Ready -> LoadState.Ready(data.data.line)
            },
            directions = ready?.let(::directionsOf).orEmpty(),
            selectedDirection = direction,
            routes = routes,
            selectedRouteCode = routeCode,
            stops = range?.map { TimelineStop(it, routeStops[it]) }.orEmpty(),
            isFavorite = lineCode in favoriteLines,
            favoriteStopCodes = favoriteStops,
        )
    }

    val uiState: StateFlow<LineDetailUiState> = combine(baseState, plan, clock) { base, plan, now ->
        // Only buses in the shown direction; if two head to the same stop, the one that arrives first.
        val shown = base.stops.map { it.routeIndex }.toSet()
        val buses = plan?.busesAt(now).orEmpty()
            .filter { it.nextStopIndex in shown }
            .groupBy { it.nextStopIndex }
            .mapValues { (_, list) -> list.minOf { it.secondsToNextStop } }
        base.copy(busesByNextStop = buses)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LineDetailUiState())

    init {
        load()
    }

    /** Loads the line, the stops of all its routes, and works out their directions. */
    fun load() {
        viewModelScope.launch {
            data.value = LoadState.Loading
            val result = loadCatching {
                val found = repository.getLines().first { it.code == lineCode }
                val line = found.copy(routes = found.routes.sortedBy { it.code })
                val routeStops = coroutineScope {
                    line.routes.map { route -> async { route.code to repository.getRouteStops(route.code) } }
                        .awaitAll()
                        .toMap()
                }
                val splits = routeStops.mapValues { (_, stops) ->
                    RouteDirections.split(stops.map { LatLon(it.latitude, it.longitude) })
                }
                LineData(line, routeStops, splits)
            }
            data.value = result
            if (result is LoadState.Ready) {
                // Start with Μετάβαση if the line has it.
                selectDirection(directionsOf(result.data).firstOrNull())
            }
        }
    }

    /** Chooses Μετάβαση or Επιστροφή, keeping the same route if it runs that way too. */
    fun selectDirection(direction: Direction?) {
        val ready = (data.value as? LoadState.Ready)?.data ?: return
        selectedDirection.value = direction
        val routes = routesFor(ready, direction)
        val keep = selectedRoute.value?.takeIf { code -> routes.any { it.code == code } }
        (keep ?: routes.firstOrNull()?.code)?.let(::selectRoute)
    }

    fun selectRoute(routeCode: String) {
        if (selectedRoute.value == routeCode && plan.value != null) return
        selectedRoute.value = routeCode
        plan.value = null
        planJob?.cancel() // if the user taps quickly, only the last route counts
        planJob = viewModelScope.launch {
            // The estimated buses (if this fails, the stops are still shown).
            plan.value = (loadCatching { repository.getRoutePlan(lineCode, routeCode) } as? LoadState.Ready)?.data
        }
    }

    fun retry() = load()

    fun onToggleFavorite() = favorites.toggleLine(lineCode)

    fun onToggleStopFavorite(stopCode: String) = favorites.toggleStop(stopCode)

    /** The directions this line has, Μετάβαση first. Town loops have none. */
    private fun directionsOf(data: LineData): List<Direction> =
        Direction.entries.filter { direction ->
            data.splits.values.any { it !is RouteSplit.Loop && it.range(direction, 2) != null }
        }

    private fun routesFor(data: LineData, direction: Direction?): List<Route> =
        if (direction == null) data.line.routes
        else data.line.routes.filter { route ->
            val stops = data.routeStops[route.code].orEmpty()
            data.splits[route.code]?.range(direction, stops.size) != null
        }
}
