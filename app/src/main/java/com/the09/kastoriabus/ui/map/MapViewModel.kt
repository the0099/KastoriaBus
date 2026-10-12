package com.the09.kastoriabus.ui.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.data.FavoritesStore
import com.the09.kastoriabus.data.LocationHelper
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.RouteShape
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.loadCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A point on the map, without depending on the map library here. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** One-time instructions for the map camera ("fly there"). */
sealed interface CameraCommand {
    data class Center(val point: GeoPoint, val zoom: Double) : CameraCommand
    data class Fit(val points: List<GeoPoint>) : CameraCommand
}

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
    private var shapesJob: Job? = null

    private val _camera = Channel<CameraCommand>(Channel.BUFFERED)
    /** The screen applies these to the map once it's ready. */
    val cameraCommands: Flow<CameraCommand> = _camera.receiveAsFlow()

    private val selection = combine(selectedLineCode, shapes, selectedStopCode) { line, shapes, stop ->
        Triple(line, shapes, stop)
    }

    val uiState: StateFlow<MapUiState> = combine(
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

    /** Chooses a line (or null for "all lines"), draws its routes and zooms to them. */
    fun selectLine(lineCode: String?) {
        selectedLineCode.value = lineCode
        shapes.value = emptyList()
        shapesJob?.cancel()
        if (lineCode == null) return
        shapesJob = viewModelScope.launch {
            val result = loadCatching { repository.getLineShapes(lineCode) }
            if (result is LoadState.Ready) {
                shapes.value = result.data
                val points = result.data.flatMap { shape ->
                    shape.routePoints.map { GeoPoint(it.latitude, it.longitude) }
                }
                if (points.isNotEmpty()) _camera.trySend(CameraCommand.Fit(points))
            }
        }
    }

    fun onStopTapped(stopCode: String) {
        selectedStopCode.value = stopCode
    }

    fun clearSelectedStop() {
        selectedStopCode.value = null
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
