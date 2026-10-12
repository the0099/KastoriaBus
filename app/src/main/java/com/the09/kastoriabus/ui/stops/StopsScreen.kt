package com.the09.kastoriabus.ui.stops

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.ui.common.CircleIcon
import com.the09.kastoriabus.ui.common.EmptyMessage
import com.the09.kastoriabus.ui.common.ErrorView
import com.the09.kastoriabus.ui.common.FavoriteButton
import com.the09.kastoriabus.ui.common.FavoriteCard
import com.the09.kastoriabus.ui.common.GradientHeader
import com.the09.kastoriabus.ui.common.LineBadge
import com.the09.kastoriabus.ui.common.ListCard
import com.the09.kastoriabus.ui.common.LoadingView
import com.the09.kastoriabus.ui.common.Pill
import com.the09.kastoriabus.ui.common.SectionTitle
import com.the09.kastoriabus.ui.common.favoritesBar
import com.the09.kastoriabus.ui.common.formatDistance
import com.the09.kastoriabus.ui.theme.AccentBrush

val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun StopsScreen(viewModel: StopsViewModel, onStopClick: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Shows Android's "Allow location?" dialog and gives us the answer.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { answers -> viewModel.onLocationPermissionResult(answers.values.any { it }) }

    // rememberSaveable: remember that we already asked, even after rotating the phone.
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        when {
            viewModel.hasLocationPermission() -> viewModel.onLocationPermissionResult(true)
            !askedOnce -> {
                askedOnce = true
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
            else -> viewModel.onLocationPermissionResult(false)
        }
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = stringResource(R.string.tab_stops),
                subtitle = stringResource(R.string.app_subtitle)
            ) {
                SearchField(query = state.query, onQueryChange = viewModel::onQueryChange)
            }
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingView(Modifier.padding(padding))
            state.hasError -> ErrorView(onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // While searching, hide the favorites so the results are right below the box.
                if (state.query.isBlank()) {
                    favoritesBar(state.favoriteRows, R.string.favorites_empty_stops) { row ->
                        FavoriteStopCard(row, onClick = { onStopClick(row.stop.code) })
                    }
                }
                item(key = "location") {
                    LocationInfo(
                        status = state.locationStatus,
                        onAllowClick = { permissionLauncher.launch(LOCATION_PERMISSIONS) }
                    )
                }
                if (state.rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyMessage(stringResource(R.string.no_stops_found), R.drawable.ic_bus)
                    }
                }
                items(state.rows, key = { it.stop.code }) { row ->
                    StopListItem(
                        row = row,
                        onClick = { onStopClick(row.stop.code) },
                        onToggleFavorite = { viewModel.onToggleFavorite(row.stop.code) }
                    )
                }
            }
        }
    }
}

/** White rounded search box that sits on the blue header. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_stops)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** The title above the list, which also says how the stops are sorted. */
@Composable
private fun LocationInfo(status: LocationStatus, onAllowClick: () -> Unit) {
    when (status) {
        LocationStatus.Found -> SectionTitle(stringResource(R.string.stops_nearest))
        LocationStatus.Unknown -> SectionTitle(stringResource(R.string.stops_all))
        LocationStatus.Locating -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(stringResource(R.string.location_finding), modifier = Modifier.padding(start = 12.dp))
        }
        LocationStatus.Denied, LocationStatus.Unavailable, LocationStatus.FarAway -> Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        when (status) {
                            LocationStatus.Denied -> R.string.location_denied
                            LocationStatus.FarAway -> R.string.location_far_away
                            else -> R.string.location_unavailable
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)
                )
                if (status == LocationStatus.Denied) {
                    TextButton(onClick = onAllowClick) {
                        Text(stringResource(R.string.location_allow), fontWeight = FontWeight.Bold)
                    }
                }
            }
            SectionTitle(stringResource(R.string.stops_all))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopListItem(row: StopRow, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    ListCard(onClick = onClick) {
        CircleIcon(R.drawable.ic_bus)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                row.stop.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            row.distanceMeters?.let { meters ->
                Pill(
                    text = formatDistance(meters),
                    iconRes = R.drawable.ic_walk,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (row.lines.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    row.lines.forEach { line -> LineBadge(line.code, line.color, size = 26.dp) }
                }
            }
        }
        FavoriteButton(isFavorite = row.isFavorite, onClick = onToggleFavorite)
    }
}

/** Orange-pink gradient card in the favorites bar. */
@Composable
private fun FavoriteStopCard(row: StopRow, onClick: () -> Unit) {
    FavoriteCard(brush = AccentBrush, onClick = onClick) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                row.stop.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            row.distanceMeters?.let {
                Text(
                    formatDistance(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
