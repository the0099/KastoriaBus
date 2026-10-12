package com.the09.kastoriabus.ui.common

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the09.kastoriabus.R
import com.the09.kastoriabus.ui.theme.FavoriteRed
import com.the09.kastoriabus.ui.theme.LocalHeaderBrush
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

/** "Α-Β" -> "Α - Β": spaces let long route names wrap between words, not inside them. */
fun prettyRouteName(name: String): String =
    name.replace(Regex("\\s*-\\s*"), " - ")

/** "01" -> "1", "54" -> "54": how line numbers are shown to the user. */
fun lineNumber(code: String): String = code.trimStart('0').ifEmpty { code }

/** Turns "#787878" into a Compose Color, or [fallback] if the text is not a colour. */
fun parseColor(hex: String?, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

/** The coloured rounded square with the line number, like on the bus signs. */
@Composable
fun LineBadge(
    code: String,
    color: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val small = size < 32.dp
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size, minHeight = size * (if (small) 0.8f else 1f))
            .clip(RoundedCornerShape(if (small) 7.dp else size * 0.3f))
            .background(lineColor(code, color))
            .padding(horizontal = if (small) 5.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = lineNumber(code),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** The heart on the right of every stop and line. */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    inactiveTint: Color = MaterialTheme.colorScheme.outline,
    activeTint: Color = FavoriteRed,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.favorite_remove else R.string.favorite_add
            ),
            tint = if (isFavorite) activeTint else inactiveTint,
        )
    }
}

/**
 * The colourful top part of every screen: a gradient with rounded bottom corners,
 * a big white title and, below it, any extra content (search box, chips...).
 */
@Composable
fun GradientHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    brush: Brush = LocalHeaderBrush.current,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(brush)
            .statusBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack == null) 20.dp else 4.dp, end = 4.dp, top = 8.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
        content()
    }
}

/** A white rounded card used for the rows of every list. */
@Composable
fun ListCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            content = content
        )
    }
}

/** A small coloured "pill" with an icon, e.g. the walking distance "350 m". */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: Color = MaterialTheme.colorScheme.onTertiaryContainer,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (iconRes != null) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = FontWeight.SemiBold)
    }
}

/** A round coloured icon, used at the start of stop rows. */
@Composable
fun CircleIcon(
    iconRes: Int,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 44.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(CircleShape).background(container)
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun formatDistance(meters: Float): String =
    if (meters < 1000) stringResource(R.string.distance_m, meters.toInt())
    else stringResource(R.string.distance_km, meters / 1000f)

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun ErrorView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircleIcon(
            R.drawable.ic_bus,
            size = 72.dp,
            container = MaterialTheme.colorScheme.secondaryContainer,
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            stringResource(R.string.error_loading),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}

/** A friendly message with an icon, for empty lists. */
@Composable
fun EmptyMessage(text: String, iconRes: Int, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(32.dp)
    ) {
        CircleIcon(iconRes, size = 64.dp)
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/** A bold title above a group of rows, with a small coloured mark. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                    .padding(14.dp)
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = FavoriteRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(emptyText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items.size) { index -> itemContent(items[index]) }
            }
        }
    }
}

/** A colourful card in the favorites bar. */
@Composable
fun FavoriteCard(
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(156.dp)
            .height(116.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}
