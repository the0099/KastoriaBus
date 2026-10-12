package com.the09.kastoriabus.ui.lines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.data.FavoritesStore
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.loadCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LineRow(val line: Line, val isFavorite: Boolean)

data class LinesUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val rows: List<LineRow> = emptyList(),
    val favoriteRows: List<LineRow> = emptyList(),
)

class LinesViewModel(
    private val repository: BusRepository,
    private val favorites: FavoritesStore,
) : ViewModel() {

    private val lines = MutableStateFlow<LoadState<List<Line>>>(LoadState.Loading)

    val uiState: StateFlow<LinesUiState> = combine(lines, favorites.lineCodes) { lines, favoriteCodes ->
        when (lines) {
            LoadState.Loading -> LinesUiState(isLoading = true)
            LoadState.Error -> LinesUiState(isLoading = false, hasError = true)
            is LoadState.Ready -> {
                // Sort by number: 1, 2, ... 17, 53, 54 (codes are text, so convert them first).
                val rows = lines.data
                    .sortedWith(compareBy<Line> { it.code.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.code })
                    .map { LineRow(it, it.code in favoriteCodes) }
                LinesUiState(
                    isLoading = false,
                    rows = rows,
                    favoriteRows = rows.filter { it.isFavorite },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinesUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            lines.value = LoadState.Loading
            lines.value = loadCatching { repository.getLines() }
        }
    }

    fun onToggleFavorite(lineCode: String) = favorites.toggleLine(lineCode)
}
