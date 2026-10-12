package com.the09.kastoriabus.ui.linedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.ui.common.ErrorView
import com.the09.kastoriabus.ui.common.FavoriteButton
import com.the09.kastoriabus.ui.common.GradientHeader
import com.the09.kastoriabus.ui.common.LoadState
import com.the09.kastoriabus.ui.common.LoadingView
import com.the09.kastoriabus.ui.common.SectionTitle
import com.the09.kastoriabus.ui.common.lineColor
import com.the09.kastoriabus.ui.common.lineNumber
import com.the09.kastoriabus.ui.common.prettyRouteName

@Composable
fun LineDetailScreen(
    viewModel: LineDetailViewModel,
    onBack: () -> Unit,
    onStopClick: (String) -> Unit,
    onShowOnMap: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val line = (state.line as? LoadState.Ready)?.data
    // The whole screen takes the colour of the line.
    val color = line?.let { lineColor(it.code, it.color) } ?: MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            GradientHeader(
                title = line?.let { stringResource(R.string.line_title, lineNumber(it.code), it.name) } ?: "",
                subtitle = line?.let {
                    pluralStringResource(R.plurals.route_count, it.routes.size, it.routes.size)
                },
                brush = Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.3f))),
                onBack = onBack,
                actions = {
                    if (line != null) {
                        IconButton(onClick = { onShowOnMap(line.code) }) {
                            Icon(
                                painterResource(R.drawable.ic_map),
                                contentDescription = stringResource(R.string.show_on_map),
                                tint = Color.White
                            )
                        }
                        FavoriteButton(
                            isFavorite = state.isFavorite,
                            onClick = viewModel::onToggleFavorite,
                            inactiveTint = Color.White,
                            activeTint = Color.White
                        )
                    }
                }
            ) {
                if (line != null) RouteChips(line, state.selectedRouteCode, viewModel::selectRoute)
            }
        }
    ) { padding ->
        when (val lineState = state.line) {
            LoadState.Loading -> LoadingView(Modifier.padding(padding))
            LoadState.Error -> ErrorView(onRetry = viewModel::retry, modifier = Modifier.padding(padding))
            is LoadState.Ready -> Box(Modifier.fillMaxSize().padding(padding)) {
                when (val stops = state.stops) {
                    LoadState.Loading -> LoadingView()
                    LoadState.Error -> ErrorView(onRetry = viewModel::retry)
                    is LoadState.Ready -> RouteTimeline(
                        line = lineState.data,
                        stops = stops.data,
                        favoriteStopCodes = state.favoriteStopCodes,
                        busesByNextStop = state.busesByNextStop,
                        onStopClick = onStopClick,
                        onToggleStopFavorite = viewModel::onToggleStopFavorite
                    )
                }
            }
        }
    }
}

/** One white chip per route variant of the line, on the coloured header. */
@Composable
private fun RouteChips(line: Line, selectedRouteCode: String?, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        items(line.routes, key = { it.code }) { route ->
            FilterChip(
                selected = route.code == selectedRouteCode,
                onClick = { onSelect(route.code) },
                label = { Text(prettyRouteName(route.name), maxLines = 1, fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(50),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    labelColor = Color.White,
                    selectedContainerColor = Color.White,
                    selectedLabelColor = lineColor(line.code, line.color),
                )
            )
        }
    }
}

/** The stops drawn like a metro map: a coloured line joining a circle for each stop. */
@Composable
private fun RouteTimeline(
    line: Line,
    stops: List<Stop>,
    favoriteStopCodes: Set<String>,
    busesByNextStop: Map<Int, Int>,
    onStopClick: (String) -> Unit,
    onToggleStopFavorite: (String) -> Unit,
) {
    val color = lineColor(line.code, line.color)
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "count") {
            SectionTitle(pluralStringResource(R.plurals.route_stop_count, stops.size, stops.size))
        }
        if (busesByNextStop.isNotEmpty()) {
            item(key = "estimate") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_bus),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.estimate_line_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        // The same stop code can appear twice in a circular route, so the key includes the position.
        itemsIndexed(stops, key = { index, stop -> "$index-${stop.code}" }) { index, stop ->
            val isEnd = index == 0 || index == stops.lastIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable { onStopClick(stop.code) }
                    .padding(start = 20.dp, end = 4.dp)
            ) {
                // The timeline: a vertical bar with a circle in the middle.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.width(28.dp).fillMaxHeight()
                ) {
                    Column(Modifier.fillMaxHeight()) {
                        Box(
                            Modifier.width(6.dp).weight(1f)
                                .background(if (index == 0) Color.Transparent else color)
                        )
                        Box(
                            Modifier.width(6.dp).weight(1f)
                                .background(if (index == stops.lastIndex) Color.Transparent else color)
                        )
                    }
                    Box(
                        Modifier
                            .size(if (isEnd) 22.dp else 16.dp)
                            .clip(CircleShape)
                            .background(if (isEnd) color else Color.White)
                            .border(4.dp, color, CircleShape)
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        text = stop.name,
                        style = if (isEnd) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isEnd) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.stop_number, index + 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                busesByNextStop[index]?.let { seconds -> BusComingPill(seconds, color) }
                FavoriteButton(
                    isFavorite = stop.code in favoriteStopCodes,
                    onClick = { onToggleStopFavorite(stop.code) }
                )
            }
        }
    }
}

/** "🚌 ~2′": an (estimated) bus is on its way to this stop. */
@Composable
private fun BusComingPill(seconds: Int, color: Color) {
    val minutes = seconds / 60
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_bus),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (minutes < 1) stringResource(R.string.estimate_arriving)
                   else stringResource(R.string.estimate_in_minutes, minutes),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
