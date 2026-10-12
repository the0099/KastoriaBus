package com.the09.kastoriabus.ui.lines

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.ui.common.ErrorView
import com.the09.kastoriabus.ui.common.FavoriteButton
import com.the09.kastoriabus.ui.common.FavoriteCard
import com.the09.kastoriabus.ui.common.GradientHeader
import com.the09.kastoriabus.ui.common.LineBadge
import com.the09.kastoriabus.ui.common.ListCard
import com.the09.kastoriabus.ui.common.LoadingView
import com.the09.kastoriabus.ui.common.SectionTitle
import com.the09.kastoriabus.ui.common.favoritesBar
import com.the09.kastoriabus.ui.common.lineColor
import com.the09.kastoriabus.ui.common.lineNumber

@Composable
fun LinesScreen(viewModel: LinesViewModel, onLineClick: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GradientHeader(
                title = stringResource(R.string.tab_lines),
                subtitle = if (state.rows.isNotEmpty()) {
                    pluralStringResource(R.plurals.line_count, state.rows.size, state.rows.size)
                } else null
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingView(Modifier.padding(padding))
            state.hasError -> ErrorView(onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
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
                }
            }
        }
    }
}

@Composable
private fun LineListItem(row: LineRow, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    ListCard(onClick = onClick) {
        LineBadge(row.line.code, row.line.color, size = 52.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = row.line.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = pluralStringResource(R.plurals.route_count, row.line.routes.size, row.line.routes.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FavoriteButton(isFavorite = row.isFavorite, onClick = onToggleFavorite)
    }
}

/** Favorite line card, painted in the line's own colour. */
@Composable
private fun FavoriteLineCard(row: LineRow, onClick: () -> Unit) {
    val color = lineColor(row.line.code, row.line.color)
    FavoriteCard(
        brush = Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.25f))),
        onClick = onClick
    ) {
        Text(
            lineNumber(row.line.code),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp
        )
        Text(
            row.line.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
