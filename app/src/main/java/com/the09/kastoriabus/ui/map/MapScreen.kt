package com.the09.kastoriabus.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.ui.common.CircleIcon
import com.the09.kastoriabus.ui.common.ErrorView
import com.the09.kastoriabus.ui.common.FavoriteButton
import com.the09.kastoriabus.ui.common.GradientHeader
import com.the09.kastoriabus.ui.common.LineBadge
import com.the09.kastoriabus.ui.common.Pill
import com.the09.kastoriabus.ui.common.formatDistance
import com.the09.kastoriabus.ui.common.lineColor
import com.the09.kastoriabus.ui.common.lineColorHex
import com.the09.kastoriabus.ui.common.lineNumber
import com.the09.kastoriabus.ui.common.prettyRouteName
import com.the09.kastoriabus.ui.stops.LOCATION_PERMISSIONS
import com.the09.kastoriabus.ui.theme.LakeBlue
import org.maplibre.android.geometry.LatLng

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onStopDetailsClick: (String) -> Unit,
    onLineClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { answers -> if (answers.values.any { it }) viewModel.locateUser() }

    if (state.hasError) {
        ErrorView(onRetry = viewModel::load)
        return
    }

    val selectedLine = state.selectedLine
    Box(Modifier.fillMaxSize()) {
        BusMap(
            stops = state.visibleStops,
            // All stops: blue rings. One line chosen: rings and route in the line's colour.
            stopColorHex = selectedLine?.let { lineColorHex(it.code, it.color) } ?: "#1449C8",
            shapes = state.shapes,
            shapeColorHex = selectedLine?.let { lineColorHex(it.code, it.color) } ?: "#1449C8",
            selectedStop = state.selectedStop,
            buses = state.buses,
            selectedBus = state.selectedBus,
            busColor = (selectedLine?.let { lineColor(it.code, it.color) } ?: LakeBlue).toArgb(),
            userLatLng = state.userLocation?.let { LatLng(it.latitude, it.longitude) },
            cameraCommands = viewModel.cameraCommands,
            contentPaddingTop = 240.dp,
            contentPaddingBottom = 60.dp,
            onStopClick = viewModel::onStopTapped,
            onBusClick = viewModel::onBusTapped,
            onMapClick = viewModel::clearSelection,
            modifier = Modifier.fillMaxSize()
        )

        GradientHeader(
            title = stringResource(R.string.tab_map),
            subtitle = selectedLine?.let { stringResource(R.string.line_title, lineNumber(it.code), it.name) }
                ?: stringResource(R.string.map_all_stops, state.visibleStops.size)
        ) {
            LineChips(state.lines, selectedLine?.code, viewModel::selectLine)
            EstimateStatus(
                hasLine = selectedLine != null,
                loading = state.busesLoading,
                busCount = state.buses.size,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    if (viewModel.hasLocationPermission()) viewModel.locateUser()
                    else permissionLauncher.launch(LOCATION_PERMISSIONS)
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.map_my_location))
            }
            AnimatedVisibility(
                visible = state.selectedBus != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                state.selectedBus?.let { bus ->
                    SelectedBusCard(
                        bus = bus,
                        line = selectedLine,
                        onClose = viewModel::clearSelection,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = state.selectedStop != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                state.selectedStop?.let { stop ->
                    SelectedStopCard(
                        stop = stop,
                        lines = state.selectedStopLines,
                        distance = state.selectedStopDistance,
                        isFavorite = state.selectedStopIsFavorite,
                        onToggleFavorite = { viewModel.onToggleFavorite(stop.code) },
                        onClose = viewModel::clearSelection,
                        onDetailsClick = { onStopDetailsClick(stop.code) },
                        onLineClick = onLineClick,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

/** "All" plus one round chip per line, each in its own colour. */
@Composable
private fun LineChips(lines: List<Line>, selectedCode: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        item(key = "all") {
            val selected = selectedCode == null
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.2f))
                    .clickable { onSelect(null) }
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    stringResource(R.string.map_all_lines),
                    fontWeight = FontWeight.Bold,
                    color = if (selected) LakeBlue else Color.White
                )
            }
        }
        items(lines, key = { it.code }) { line ->
            val selected = line.code == selectedCode
            val color = lineColor(line.code, line.color)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else color)
                    .border(if (selected) 3.dp else 2.dp, if (selected) color else Color.White, CircleShape)
                    // Tapping the chosen line again goes back to "all".
                    .clickable { onSelect(if (selected) null else line.code) }
            ) {
                Text(
                    lineNumber(line.code),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) color else Color.White
                )
            }
        }
    }
}

/** The card that slides up when you tap a stop on the map. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedStopCard(
    stop: Stop,
    lines: List<Line>,
    distance: Float?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onDetailsClick: () -> Unit,
    onLineClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(R.drawable.ic_bus)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    distance?.let {
                        Pill(formatDistance(it), iconRes = R.drawable.ic_walk, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                FavoriteButton(isFavorite, onToggleFavorite)
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
            if (lines.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp, end = 12.dp)
                ) {
                    lines.forEach { line ->
                        LineBadge(line.code, line.color, size = 32.dp, modifier = Modifier.clickable { onLineClick(line.code) })
                    }
                }
            }
            Button(
                onClick = onDetailsClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, end = 12.dp)
            ) {
                Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.map_show_departures),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * The small line under the chips: asks to pick a line, or says how many buses
 * are on the road, always reminding that the positions are an estimate.
 */
@Composable
private fun EstimateStatus(hasLine: Boolean, loading: Boolean, busCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(
                painterResource(R.drawable.ic_bus),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = when {
                !hasLine -> stringResource(R.string.estimate_pick_line)
                loading -> stringResource(R.string.estimate_loading)
                busCount == 0 -> stringResource(R.string.estimate_no_buses)
                else -> pluralStringResource(R.plurals.estimate_bus_count, busCount, busCount)
            },
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** The card that slides up when you tap a bus. */
@Composable
private fun SelectedBusCard(
    bus: BusMarker,
    line: Line?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (line != null) LineBadge(line.code, line.color, size = 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        prettyRouteName(bus.routeName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.estimate_departed, bus.departureTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 12.dp, end = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.estimate_next_stop),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        bus.nextStopName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                val minutes = bus.secondsToNextStop / 60
                Pill(
                    text = if (minutes < 1) stringResource(R.string.estimate_arriving)
                           else stringResource(R.string.estimate_in_minutes, minutes),
                    container = MaterialTheme.colorScheme.secondary,
                    content = Color.White,
                )
            }
            Text(
                stringResource(R.string.estimate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, end = 12.dp)
            )
        }
    }
}
