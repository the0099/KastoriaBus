package com.the09.kastoriabus.ui.stopdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.data.FavoritesStore
import com.the09.kastoriabus.data.model.Trip
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.loadCatching
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A scheduled bus that hasn't left yet, and how many minutes until it comes. */
data class Departure(val trip: Trip, val minutesUntil: Long)

data class StopDetailUiState(
    val stopName: String = "",
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val departures: List<Departure> = emptyList(),
    val isFavorite: Boolean = false,
)

class StopDetailViewModel(
    private val stopCode: String,
    private val repository: BusRepository,
    private val favorites: FavoritesStore,
) : ViewModel() {

    private val data = MutableStateFlow<LoadState<Pair<String, List<Trip>>>>(LoadState.Loading)

    // Ticks every 30 seconds so "in 5 min" counts down and departed buses disappear.
    private val clock = flow {
        while (true) {
            emit(LocalTime.now(ATHENS).truncatedTo(ChronoUnit.MINUTES))
            delay(30_000)
        }
    }

    val uiState: StateFlow<StopDetailUiState> = combine(data, clock, favorites.stopCodes) { data, now, favoriteCodes ->
        val isFavorite = stopCode in favoriteCodes
        when (data) {
            LoadState.Loading -> StopDetailUiState(isLoading = true, isFavorite = isFavorite)
            LoadState.Error -> StopDetailUiState(isLoading = false, hasError = true, isFavorite = isFavorite)
            is LoadState.Ready -> {
                val (name, trips) = data.data
                StopDetailUiState(
                    stopName = name,
                    isLoading = false,
                    departures = trips
                        .map { Departure(it, Duration.between(now, LocalTime.of(it.tripTimeHour, it.tripTimeMinute)).toMinutes()) }
                        .filter { it.minutesUntil >= 0 }
                        .sortedBy { it.minutesUntil },
                    isFavorite = isFavorite,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StopDetailUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            data.value = LoadState.Loading
            data.value = loadCatching {
                val trips = repository.getTimetable(stopCode) // today's timetable
                val name = repository.getStops().firstOrNull { it.code == stopCode }?.name
                    ?: trips.firstOrNull()?.stopName
                    ?: stopCode
                name to trips
            }
        }
    }

    fun onToggleFavorite() = favorites.toggleStop(stopCode)

    private companion object {
        val ATHENS: ZoneId = ZoneId.of("Europe/Athens")
    }
}
