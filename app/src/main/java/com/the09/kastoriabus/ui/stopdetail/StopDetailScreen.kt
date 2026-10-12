package com.the09.kastoriabus.ui.stopdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.ui.common.ErrorView
import com.the09.kastoriabus.ui.common.FavoriteButton
import com.the09.kastoriabus.ui.common.LineBadge
import com.the09.kastoriabus.ui.common.LoadingView
import com.the09.kastoriabus.ui.common.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    viewModel: StopDetailViewModel,
    onBack: () -> Unit,
    onLineClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.stopName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = { FavoriteButton(state.isFavorite, viewModel::onToggleFavorite) }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingView(Modifier.padding(padding))
            state.hasError -> ErrorView(onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                item(key = "title") { SectionTitle(stringResource(R.string.upcoming_title)) }
                if (state.departures.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(R.string.upcoming_none),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(state.departures, key = { it.trip.id }) { departure ->
                    DepartureRow(departure, onClick = { onLineClick(departure.trip.lineCode) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure, onClick: () -> Unit) {
    val trip = departure.trip
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = trip.tripTime,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
        LineBadge(trip.lineCode, trip.lineColor, trip.lineTextColor)
        Text(
            text = trip.routeName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        // Only show the countdown for buses in the next hour; later ones just show the time.
        val minutes = departure.minutesUntil
        if (minutes <= 60) {
            Text(
                text = if (minutes == 0L) stringResource(R.string.departure_now)
                       else stringResource(R.string.departure_in_minutes, minutes.toInt()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
