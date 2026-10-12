package com.the09.kastoriabus.ui.linedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.ui.common.ErrorView
import com.the09.kastoriabus.ui.common.FavoriteButton
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.LoadingView
import com.the09.kastoriabus.ui.common.lineNumber
import com.the09.kastoriabus.ui.common.parseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailScreen(
    viewModel: LineDetailViewModel,
    onBack: () -> Unit,
    onStopClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val line = (state.line as? LoadState.Ready)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = line?.let { stringResource(R.string.line_title, lineNumber(it.code), it.name) } ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (line != null) FavoriteButton(state.isFavorite, viewModel::onToggleFavorite)
                }
            )
        }
    ) { padding ->
        when (val lineState = state.line) {
            LoadState.Loading -> LoadingView(Modifier.padding(padding))
            LoadState.Error -> ErrorView(onRetry = viewModel::retry, modifier = Modifier.padding(padding))
            is LoadState.Ready -> Column(Modifier.padding(padding)) {
                RouteChips(lineState.data, state.selectedRouteCode, viewModel::selectRoute)
                HorizontalDivider()
                when (val stops = state.stops) {
                    LoadState.Loading -> LoadingView()
                    LoadState.Error -> ErrorView(onRetry = viewModel::retry)
                    is LoadState.Ready -> RouteStopsList(
                        line = lineState.data,
                        stops = stops.data,
                        favoriteStopCodes = state.favoriteStopCodes,
                        onStopClick = onStopClick,
                        onToggleStopFavorite = viewModel::onToggleStopFavorite
                    )
                }
            }
        }
    }
}

/** One chip per route variant of the line; the selected one is highlighted. */
@Composable
private fun RouteChips(line: Line, selectedRouteCode: String?, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(line.routes, key = { it.code }) { route ->
            FilterChip(
                selected = route.code == selectedRouteCode,
                onClick = { onSelect(route.code) },
                label = { Text(route.name, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun RouteStopsList(
    line: Line,
    stops: List<Stop>,
    favoriteStopCodes: Set<String>,
    onStopClick: (String) -> Unit,
    onToggleStopFavorite: (String) -> Unit,
) {
    LazyColumn {
        item(key = "count") {
            Text(
                text = pluralStringResource(R.plurals.route_stop_count, stops.size, stops.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        // The same stop code can appear twice in a circular route, so the key includes the position.
        itemsIndexed(stops, key = { index, stop -> "$index-${stop.code}" }) { index, stop ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStopClick(stop.code) }
                    .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
            ) {
                // Numbered circle in the line's colour: 1 = first stop.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(parseColor(line.color, MaterialTheme.colorScheme.primary))
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = parseColor(line.textColor, MaterialTheme.colorScheme.onPrimary)
                    )
                }
                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 14.dp)
                )
                FavoriteButton(
                    isFavorite = stop.code in favoriteStopCodes,
                    onClick = { onToggleStopFavorite(stop.code) }
                )
            }
        }
    }
}
