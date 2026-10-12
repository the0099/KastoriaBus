package com.the09.kastoriabus.ui.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.data.FavoritesStore
import com.the09.kastoriabus.data.LocationHelper
import com.the09.kastoriabus.data.estimate.RoutePlan
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.RouteShape
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.loadCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/** A point on the map, without depending on the map library here. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** One-time instructions for the map camera ("fly there"). */
sealed interface CameraCommand {
    data class Center(val point: GeoPoint, val zoom: Double) : CameraCommand
    data class Fit(val points: List<GeoPoint>) : CameraCommand
}

/** A bus drawn on the map at its estimated position. */
data class BusMarker(
    val id: String,
    val routeName: String,
    val position: GeoPoint,
    val nextStopName: String,
    val secondsToNextStop: Int,
    val departureTime: String, // when it left the first stop, "HH:mm"
)

data class MapUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val lines: List<Line> = emptyList(),          // for the chips, sorted by number
    val visibleStops: List<Stop> = emptyList(),   // all stops, or only the chosen line's
    val selectedLine: Line? = null,
    val shapes: List<RouteShape> = emptyList(),   // route drawings of the chosen line
    val selectedStop: Stop? = null,
    val selectedStopLines: List<Line> = emptyList(),
    val selectedStopIsFavorite: Boolean = false,
    val selectedStopDistance: Float? = null,
    val userLocation: Location? = null,
    val buses: List<BusMarker> = emptyList(),     // estimated buses of the chosen line
    val busesLoading: Boolean = false,
    val selectedBus: BusMarker? = null,
)

/**
 * The logic of the Map tab. [initialLineCode] / [initialStopCode] come from the
 * "show on map" buttons of the line and stop screens.
 */
class MapViewModel(
    private val repository: BusRepository,
    private val favorites: FavoritesStore,
    private val locationHelper: LocationHelper,
    private val initialLineCode: String?,
    private val initialStopCode: String?,
) : ViewModel() {

    private val data = MutableStateFlow<LoadState<Pair<List<Stop>, List<Line>>>>(LoadState.Loading)
    private val selectedLineCode = MutableStateFlow<String?>(null)
    private val shapes = MutableStateFlow<List<RouteShape>>(emptyList())
    private val selectedStopCode = MutableStateFlow<String?>(null)
    private val userLocation = MutableStateFlow<Location?>(null)
    private var lineJob: Job? = null

    // Estimated buses: one plan per route of the chosen line, and a clock.
    private val plans = MutableStateFlow<LoadState<List<RoutePlan>>?>(null)
    private val selectedBusId = MutableStateFlow<String?>(null)
    private val clock = flow {
        while (true) {
            emit(LocalTime.now(BusRepository.ATHENS).toSecondOfDay())
            delay(3_000) // the buses move a little every 3 seconds
        }
    }

    private val _camera = Channel<CameraCommand>(Channel.BUFFERED)
    /** The screen applies these to the map once it's ready. */
    val cameraCommands: Flow<CameraCommand> = _camera.receiveAsFlow()

    private val selection = combine(selectedLineCode, shapes, selectedStopCode) { line, shapes, stop ->
        Triple(line, shapes, stop)
    }

    private val baseState: Flow<MapUiState> = combine(
        data, selection, userLocation, favorites.stopCodes
    ) { data, (lineCode, shapes, stopCode), location, favoriteStops ->
        when (data) {
            LoadState.Loading -> MapUiState(isLoading = true)
            LoadState.Error -> MapUiState(isLoading = false, hasError = true)
            is LoadState.Ready -> {
                val (stops, lines) = data.data
                val linesByCode = lines.associateBy { it.code }
                val selectedStop = stops.firstOrNull { it.code == stopCode }
                MapUiState(
                    isLoading = false,
                    lines = lines,
                    visibleStops = if (lineCode == null) stops else stops.filter { lineCode in it.lineCodes },
                    selectedLine = lineCode?.let { linesByCode[it] },
                    shapes = shapes,
                    selectedStop = selectedStop,
                    selectedStopLines = selectedStop?.lineCodes.orEmpty()
                        .mapNotNull { linesByCode[it] }
                        .sortedBy { it.code.toIntOrNull() ?: Int.MAX_VALUE },
                    selectedStopIsFavorite = selectedStop != null && selectedStop.code in favoriteStops,
                    selectedStopDistance = if (selectedStop != null && location != null) {
                        FloatArray(1).also {
                            Location.distanceBetween(
                                location.latitude, location.longitude,
                                selectedStop.latitude, selectedStop.longitude, it
                            )
                        }[0]
                    } else null,
                    userLocation = location,
                )
            }
        }
    }

    val uiState: StateFlow<MapUiState> = combine(baseState, plans, clock, selectedBusId) { base, plans, now, busId ->
        val routeNames = base.selectedLine?.routes?.associate { it.code to it.name }.orEmpty()
        val buses = (plans as? LoadState.Ready)?.data.orEmpty().flatMap { plan ->
            plan.busesAt(now).map { bus ->
                BusMarker(
                    id = "${bus.routeCode}-${bus.tripStartSeconds}",
                    routeName = routeNames[bus.routeCode] ?: bus.routeCode,
                    position = GeoPoint(bus.position.lat, bus.position.lon),
                    nextStopName = plan.stops[bus.nextStopIndex].name,
                    secondsToNextStop = bus.secondsToNextStop,
                    departureTime = LocalTime.ofSecondOfDay(bus.tripStartSeconds.toLong()).toString(),
                )
            }
        }
        base.copy(
            buses = buses,
            busesLoading = plans == LoadState.Loading,
            selectedBus = buses.firstOrNull { it.id == busId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        load()
        // If location is already allowed, quietly show the blue "you are here" dot.
        if (locationHelper.hasPermission()) locateUser(moveCamera = false)
    }

    fun load() {
        viewModelScope.launch {
            data.value = LoadState.Loading
            val result = loadCatching {
                repository.getStops() to repository.getLines()
                    .sortedWith(compareBy<Line> { it.code.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.code })
            }
            data.value = result
            if (result is LoadState.Ready) {
                initialLineCode?.let(::selectLine)
                initialStopCode?.let { code ->
                    result.data.first.firstOrNull { it.code == code }?.let { stop ->
                        selectedStopCode.value = code
                        _camera.trySend(CameraCommand.Center(GeoPoint(stop.latitude, stop.longitude), 16.0))
                    }
                }
            }
        }
    }

    /**
     * Chooses a line (or null for "all lines"): draws its routes, zooms to them,
     * then works out where its buses should be right now.
     */
    fun selectLine(lineCode: String?) {
        selectedLineCode.value = lineCode
        shapes.value = emptyList()
        plans.value = null
        selectedBusId.value = null
        lineJob?.cancel()
        if (lineCode == null) return
        lineJob = viewModelScope.launch {
            val result = loadCatching { repository.getLineShapes(lineCode) }
            val drawn = (result as? LoadState.Ready)?.data.orEmpty()
            shapes.value = drawn
            // Zoom to the route drawing, or to the line's stops if there is no drawing.
            val points = drawn.flatMap { shape -> shape.routePoints.map { GeoPoint(it.latitude, it.longitude) } }
                .ifEmpty {
                    (data.value as? LoadState.Ready)?.data?.first.orEmpty()
                        .filter { lineCode in it.lineCodes }
                        .map { GeoPoint(it.latitude, it.longitude) }
                }
            if (points.isNotEmpty()) _camera.trySend(CameraCommand.Fit(points))
            // One plan per route of the line, downloaded side by side.
            val line = (data.value as? LoadState.Ready)?.data?.second?.firstOrNull { it.code == lineCode }
                ?: return@launch
            plans.value = LoadState.Loading
            plans.value = loadCatching {
                coroutineScope {
                    line.routes.map { route ->
                        async { repository.getRoutePlan(lineCode, route.code) }
                    }.awaitAll()
                }
            }
        }
    }

    fun onStopTapped(stopCode: String) {
        selectedBusId.value = null
        selectedStopCode.value = stopCode
    }

    fun onBusTapped(busId: String) {
        selectedStopCode.value = null
        selectedBusId.value = busId
    }

    fun clearSelection() {
        selectedStopCode.value = null
        selectedBusId.value = null
    }

    fun onToggleFavorite(stopCode: String) = favorites.toggleStop(stopCode)

    fun hasLocationPermission() = locationHelper.hasPermission()

    /** Finds the user; with [moveCamera] the map also flies to them. */
    fun locateUser(moveCamera: Boolean = true) {
        viewModelScope.launch {
            val location = locationHelper.getCurrentLocation() ?: return@launch
            userLocation.value = location
            if (moveCamera) {
                _camera.trySend(CameraCommand.Center(GeoPoint(location.latitude, location.longitude), 15.5))
            }
        }
    }
}
