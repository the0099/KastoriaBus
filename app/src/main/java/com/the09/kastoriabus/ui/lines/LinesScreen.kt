package com.the09.kastoriabus.ui.lines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.ui.common.ErrorView
import com.the09.kastoriabus.ui.common.FavoriteButton
import com.the09.kastoriabus.ui.common.LineBadge
import com.the09.kastoriabus.ui.common.LoadingView
import com.the09.kastoriabus.ui.common.SectionTitle
import com.the09.kastoriabus.ui.common.favoritesBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinesScreen(viewModel: LinesViewModel, onLineClick: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_lines)) }) }) { padding ->
        when {
            state.isLoading -> LoadingView(Modifier.padding(padding))
            state.hasError -> ErrorView(onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                favoritesBar(state.favoriteRows, R.string.favorites_empty_lines) { row ->
                    FavoriteLineCard(row, onClick = { onLineClick(row.line.code) })
                }
                item(key = "all_title") { SectionTitle(stringResource(R.string.lines_all)) }
                items(state.rows, key = { it.line.code }) { row ->
                    LineListItem(
                        row = row,
                        onClick = { onLineClick(row.line.code) },
                        onToggleFavorite = { viewModel.onToggleFavorite(row.line.code) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LineListItem(row: LineRow, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
    ) {
        LineBadge(row.line.code, row.line.color, row.line.textColor)
        Text(
            text = row.line.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
        FavoriteButton(isFavorite = row.isFavorite, onClick = onToggleFavorite)
    }
}

@Composable
private fun FavoriteLineCard(row: LineRow, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.widthIn(max = 200.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            LineBadge(row.line.code, row.line.color, row.line.textColor)
            Text(
                text = row.line.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}
