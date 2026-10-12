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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.the09.kastoriabus.data.Direction
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.Route
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
                if (line != null) {
                    if (state.directions.isNotEmpty()) {
                        DirectionTabs(
                            directions = state.directions,
                            selected = state.selectedDirection,
                            color = color,
                            onSelect = viewModel::selectDirection
                        )
                    }
                    RouteChips(line, state.routes, state.selectedRouteCode, viewModel::selectRoute)
                }
            }
        }
    ) { padding ->
        when (val lineState = state.line) {
            LoadState.Loading -> LoadingView(Modifier.padding(padding))
            LoadState.Error -> ErrorView(onRetry = viewModel::retry, modifier = Modifier.padding(padding))
            is LoadState.Ready -> Box(Modifier.fillMaxSize().padding(padding)) {
                RouteTimeline(
                    line = lineState.data,
                    direction = state.selectedDirection,
                    stops = state.stops,
                    favoriteStopCodes = state.favoriteStopCodes,
                    busesByNextStop = state.busesByNextStop,
                    onStopClick = onStopClick,
                    onToggleStopFavorite = viewModel::onToggleStopFavorite
                )
            }
        }
    }
}

/** The Μετάβαση | Επιστροφή switch at the top of the line screen. */
@Composable
private fun DirectionTabs(
    directions: List<Direction>,
    selected: Direction?,
    color: Color,
    onSelect: (Direction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(4.dp)
    ) {
        // Always show both tabs; one that this line doesn't have is greyed out.
        Direction.entries.forEach { direction ->
            val available = direction in directions
            val isSelected = direction == selected
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable(enabled = available) { onSelect(direction) }
                    .padding(vertical = 10.dp)
            ) {
                val tint = when {
                    isSelected -> color
                    available -> Color.White
                    else -> Color.White.copy(alpha = 0.4f)
                }
                Icon(
                    if (direction == Direction.OUTBOUND) Icons.AutoMirrored.Filled.ArrowForward
                    else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    stringResource(directionLabel(direction)),
                    color = tint,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

private fun directionLabel(direction: Direction) =
    if (direction == Direction.OUTBOUND) R.string.direction_outbound else R.string.direction_return

/** One white chip per route variant that runs in the chosen direction, on the coloured header. */
@Composable
private fun RouteChips(line: Line, routes: List<Route>, selectedRouteCode: String?, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        items(routes, key = { it.code }) { route ->
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
    direction: Direction?,
    stops: List<TimelineStop>,
    favoriteStopCodes: Set<String>,
    busesByNextStop: Map<Int, Int>,
    onStopClick: (String) -> Unit,
    onToggleStopFavorite: (String) -> Unit,
) {
    val color = lineColor(line.code, line.color)
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "count") {
            val count = pluralStringResource(R.plurals.route_stop_count, stops.size, stops.size)
            SectionTitle(
                if (direction == null) count
                else stringResource(R.string.direction_title, stringResource(directionLabel(direction)), count)
            )
            // "From ΑΦΕΤΗΡΙΑ to ΜΕΣΟΠΟΤΑΜΙΑ ΠΛΑΤΕΙΑ"
            if (stops.size >= 2) {
                Text(
                    stringResource(R.string.direction_from_to, stops.first().stop.name, stops.last().stop.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, end = 20.dp, bottom = 4.dp)
                )
            }
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
        itemsIndexed(stops, key = { _, row -> "${row.routeIndex}-${row.stop.code}" }) { index, row ->
            val stop = row.stop
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
                busesByNextStop[row.routeIndex]?.let { seconds -> BusComingPill(seconds, color) }
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
