package com.the09.kastoriabus.ui.common

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the09.kastoriabus.R
import kotlin.coroutines.cancellation.CancellationException

/** The three states of data that comes from the network. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Error : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
}

/** Runs [block] and turns the result (or the failure) into a [LoadState]. */
suspend fun <T> loadCatching(block: suspend () -> T): LoadState<T> = try {
    LoadState.Ready(block())
} catch (e: CancellationException) {
    throw e // the screen was closed: not an error
} catch (e: Exception) {
    Log.w("KastoriaBus", "Loading failed", e)
    LoadState.Error
}

/** "01" -> "1", "54" -> "54": how line numbers are shown to the user. */
fun lineNumber(code: String): String = code.trimStart('0').ifEmpty { code }

/** Turns "#787878" into a Compose Color, or [fallback] if the text is not a colour. */
fun parseColor(hex: String?, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

/** The coloured box with the line number, like on the bus signs. */
@Composable
fun LineBadge(
    code: String,
    color: String?,
    textColor: String?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = if (small) 28.dp else 44.dp, minHeight = if (small) 22.dp else 36.dp)
            .clip(RoundedCornerShape(if (small) 6.dp else 8.dp))
            .background(parseColor(color, MaterialTheme.colorScheme.primary))
            .padding(horizontal = if (small) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = lineNumber(code),
            color = parseColor(textColor, Color.White),
            fontWeight = FontWeight.Bold,
            fontSize = if (small) 12.sp else 16.sp,
        )
    }
}

/** The heart on the right of every stop and line. */
@Composable
fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.favorite_remove else R.string.favorite_add
            ),
            tint = if (isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.error_loading), textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}

/** Small grey title above a group of rows. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * The favorites bar at the top of the Stops and Lines tabs: a title and a row
 * you can swipe sideways. Shows [emptyText] when there are no favorites yet.
 */
fun <T> LazyListScope.favoritesBar(
    items: List<T>,
    emptyText: Int,
    itemContent: @Composable (T) -> Unit,
) {
    item(key = "favorites_title") { SectionTitle(stringResource(R.string.favorites_title)) }
    item(key = "favorites_row") {
        if (items.isEmpty()) {
            Text(
                text = stringResource(emptyText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items.size) { index -> itemContent(items[index]) }
            }
        }
    }
}
