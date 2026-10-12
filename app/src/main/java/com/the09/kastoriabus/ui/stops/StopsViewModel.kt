package com.the09.kastoriabus.ui.stops

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.data.FavoritesStore
import com.the09.kastoriabus.data.LocationHelper
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.loadCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

enum class LocationStatus { Unknown, Locating, Found, FarAway, Unavailable, Denied }

// If even the closest stop is further than this, the user isn't in Kastoria:
// sorting by distance would be useless, so we show the stops A–Z instead.
private const val MAX_NEARBY_DISTANCE_METERS = 30_000f

/** One row of the stops list. */
data class StopRow(
    val stop: Stop,
    val distanceMeters: Float?, // null when we don't know where the user is
    val lines: List<Line>,      // the lines that stop here, for the small badges
    val isFavorite: Boolean,
)

data class StopsUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val rows: List<StopRow> = emptyList(),
    val favoriteRows: List<StopRow> = emptyList(),
    val query: String = "",
    val locationStatus: LocationStatus = LocationStatus.Unknown,
)

/**
 * The logic behind the Stops tab. A ViewModel survives screen rotation and tab
 * switches, so we don't download the stops again every time.
 */
class StopsViewModel(
    private val repository: BusRepository,
    private val favorites: FavoritesStore,
    private val locationHelper: LocationHelper,
) : ViewModel() {

    private val data = MutableStateFlow<LoadState<Pair<List<Stop>, Map<String, Line>>>>(LoadState.Loading)
    private val userLocation = MutableStateFlow<Location?>(null)
    private val locationStatus = MutableStateFlow(LocationStatus.Unknown)
    private val query = MutableStateFlow("")

    // Greek alphabetical order (Α, Β, Γ...), not computer "character code" order.
    private val collator = Collator.getInstance(Locale.forLanguageTag("el"))

    /** Everything the screen needs, recalculated whenever one of the inputs changes. */
    val uiState: StateFlow<StopsUiState> = combine(
        data, userLocation, locationStatus, query, favorites.stopCodes
    ) { data, location, status, query, favoriteCodes ->
        when (data) {
            LoadState.Loading -> StopsUiState(isLoading = true, query = query, locationStatus = status)
            LoadState.Error -> StopsUiState(isLoading = false, hasError = true, query = query, locationStatus = status)
            is LoadState.Ready -> {
                val (stops, linesByCode) = data.data
                val closest = location?.let { loc -> stops.minOfOrNull { distance(loc, it) } }
                val tooFar = closest != null && closest > MAX_NEARBY_DISTANCE_METERS
                val location = location.takeUnless { tooFar }
                val status = if (tooFar) LocationStatus.FarAway else status
                val allRows = stops.map { stop ->
                    StopRow(
                        stop = stop,
                        distanceMeters = location?.let { distance(it, stop) },
                        lines = stop.lineCodes.mapNotNull { linesByCode[it] }
                            .sortedBy { it.code.toIntOrNull() ?: Int.MAX_VALUE },
                        isFavorite = stop.code in favoriteCodes,
                    )
                }.sortedWith(
                    if (location != null) compareBy<StopRow> { it.distanceMeters }
                    else Comparator<StopRow> { a, b -> collator.compare(a.stop.name, b.stop.name) }
                )
                val search = normalize(query.trim())
                StopsUiState(
                    isLoading = false,
                    rows = if (search.isEmpty()) allRows
                           else allRows.filter { normalize(it.stop.name).contains(search) },
                    favoriteRows = allRows.filter { it.isFavorite },
                    query = query,
                    locationStatus = status,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StopsUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            data.value = LoadState.Loading
            data.value = loadCatching {
                repository.getStops() to repository.getLines().associateBy { it.code }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onToggleFavorite(stopCode: String) = favorites.toggleStop(stopCode)

    fun hasLocationPermission(): Boolean = locationHelper.hasPermission()

    /** Called with the user's answer to the location permission question. */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            locationStatus.value = LocationStatus.Denied
            return
        }
        // Already found (or looking): don't ask the GPS again on every tab switch.
        if (locationStatus.value == LocationStatus.Found || locationStatus.value == LocationStatus.Locating) return
        viewModelScope.launch {
            locationStatus.value = LocationStatus.Locating
            val location = locationHelper.getCurrentLocation()
            userLocation.value = location
            locationStatus.value = if (location != null) LocationStatus.Found else LocationStatus.Unavailable
        }
    }

    private fun distance(from: Location, stop: Stop): Float {
        val result = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, stop.latitude, stop.longitude, result)
        return result[0]
    }

    /** "Ντίνη" -> "ΝΤΙΝΗ": removes Greek accents and uppercases, so search ignores them. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase(Locale.ROOT)
}
