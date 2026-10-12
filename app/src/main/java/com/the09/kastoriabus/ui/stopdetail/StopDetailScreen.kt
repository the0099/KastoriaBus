package com.the09.kastoriabus.ui.stopdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.ui.common.EmptyMessage
import com.the09.kastoriabus.ui.common.ErrorView
import com.the09.kastoriabus.ui.common.FavoriteButton
import com.the09.kastoriabus.ui.common.GradientHeader
import com.the09.kastoriabus.ui.common.LineBadge
import com.the09.kastoriabus.ui.common.ListCard
import com.the09.kastoriabus.ui.common.LoadingView
import com.the09.kastoriabus.ui.common.Pill
import com.the09.kastoriabus.ui.common.SectionTitle
import com.the09.kastoriabus.ui.common.prettyRouteName
import com.the09.kastoriabus.ui.theme.AccentBrush

@Composable
fun StopDetailScreen(
    viewModel: StopDetailViewModel,
    onBack: () -> Unit,
    onLineClick: (String) -> Unit,
    onShowOnMap: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GradientHeader(
                title = state.stopName,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onShowOnMap) {
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
            ) {
                // The lines that stop here; tap one to open it.
                if (state.lineCodes.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.lineCodes) { code ->
                            LineBadge(code, null, size = 34.dp, modifier = Modifier.clickable { onLineClick(code) })
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingView(Modifier.padding(padding))
            state.hasError -> ErrorView(onRetry = viewModel::load, modifier = Modifier.padding(padding))
            state.departures.isEmpty() -> EmptyMessage(
                stringResource(R.string.upcoming_none),
                R.drawable.ic_schedule,
                modifier = Modifier.padding(padding).padding(top = 32.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                val next = state.departures.first()
                item(key = "next") {
                    NextBusCard(next, onClick = { onLineClick(next.trip.lineCode) })
                }
                if (state.departures.size > 1) {
                    item(key = "title") { SectionTitle(stringResource(R.string.upcoming_title)) }
                    items(state.departures.drop(1), key = { it.trip.id }) { departure ->
                        DepartureRow(departure, onClick = { onLineClick(departure.trip.lineCode) })
                    }
                }
            }
        }
    }
}

/** The big orange card for the very next bus. */
@Composable
private fun NextBusCard(departure: Departure, onClick: () -> Unit) {
    val trip = departure.trip
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(AccentBrush)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Text(
            stringResource(R.string.next_bus),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Bold
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            LineBadge(trip.lineCode, trip.lineColor, size = 52.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    prettyRouteName(trip.routeName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_schedule),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        trip.tripTime,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (departure.minutesUntil == 0L) {
                    Text(
                        stringResource(R.string.departure_now),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp
                    )
                } else {
                    Text(
                        departure.minutesUntil.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 40.sp,
                        lineHeight = 40.sp
                    )
                    Text(
                        stringResource(R.string.minutes_short),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure, onClick: () -> Unit) {
    val trip = departure.trip
    ListCard(onClick = onClick) {
        Text(
            text = trip.tripTime,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(68.dp)
        )
        LineBadge(trip.lineCode, trip.lineColor, size = 38.dp)
        Text(
            text = prettyRouteName(trip.routeName),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        // Only show the countdown for buses in the next hour; later ones just show the time.
        val minutes = departure.minutesUntil
        if (minutes <= 60) {
            Pill(
                text = if (minutes == 0L) stringResource(R.string.departure_now)
                       else stringResource(R.string.departure_in_minutes, minutes.toInt()),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}
