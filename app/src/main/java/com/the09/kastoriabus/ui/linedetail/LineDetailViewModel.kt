package com.the09.kastoriabus.ui.linedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.data.FavoritesStore
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.loadCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LineDetailUiState(
    val line: LoadState<Line> = LoadState.Loading,
    val selectedRouteCode: String? = null,
    val stops: LoadState<List<Stop>> = LoadState.Loading,
    val isFavorite: Boolean = false,
    val favoriteStopCodes: Set<String> = emptySet(),
)

/** The logic of the line screen: the line, its route variants, and the stops of the chosen one. */
class LineDetailViewModel(
    private val lineCode: String,
    private val repository: BusRepository,
    private val favorites: FavoritesStore,
) : ViewModel() {

    private val line = MutableStateFlow<LoadState<Line>>(LoadState.Loading)
    private val selectedRoute = MutableStateFlow<String?>(null)
    private val routeStops = MutableStateFlow<LoadState<List<Stop>>>(LoadState.Loading)
    private var routeJob: Job? = null

    val uiState: StateFlow<LineDetailUiState> = combine(
        line, selectedRoute, routeStops, favorites.lineCodes, favorites.stopCodes
    ) { line, route, stops, favoriteLines, favoriteStops ->
        LineDetailUiState(
            line = line,
            selectedRouteCode = route,
            stops = stops,
            isFavorite = lineCode in favoriteLines,
            favoriteStopCodes = favoriteStops,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LineDetailUiState())

    init {
        load()
    }

    /** Loads the line (comes from the cache, so it's instant) and then its first route. */
    fun load() {
        viewModelScope.launch {
            line.value = LoadState.Loading
            val result = loadCatching {
                val found = repository.getLines().first { it.code == lineCode }
                found.copy(routes = found.routes.sortedBy { it.code })
            }
            line.value = result
            if (result is LoadState.Ready) {
                (selectedRoute.value ?: result.data.routes.firstOrNull()?.code)?.let(::selectRoute)
            }
        }
    }

    fun selectRoute(routeCode: String) {
        selectedRoute.value = routeCode
        routeJob?.cancel() // if the user taps quickly, only the last route counts
        routeJob = viewModelScope.launch {
            routeStops.value = LoadState.Loading
            routeStops.value = loadCatching { repository.getRouteStops(routeCode) }
        }
    }

    fun retry() {
        val route = selectedRoute.value
        if (line.value is LoadState.Ready && route != null) selectRoute(route) else load()
    }

    fun onToggleFavorite() = favorites.toggleLine(lineCode)

    fun onToggleStopFavorite(stopCode: String) = favorites.toggleStop(stopCode)
}
