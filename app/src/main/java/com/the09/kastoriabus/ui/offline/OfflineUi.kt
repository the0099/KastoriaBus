package com.the09.kastoriabus.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.the09.kastoriabus.R
import com.the09.kastoriabus.data.OfflineDownloader
import com.the09.kastoriabus.data.OfflineStatus
import com.the09.kastoriabus.ui.common.CircleIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The panel that slides up from the bottom: "Use without internet". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSheet(downloader: OfflineDownloader, usingSavedData: Boolean, onDismiss: () -> Unit) {
    val status by downloader.status.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Open fully at once (not half-way), so the button at the bottom is visible.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        // Scrollable and clear of the phone's navigation buttons, so the button at the
        // bottom is always reachable, even on small screens.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(R.drawable.ic_cloud_download)
                Text(
                    stringResource(R.string.offline_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            if (usingSavedData) {
                // Why the cloud in the header is crossed out.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_cloud_off),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.offline_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.offline_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )

            StatusBox(status)

            if (status.running) {
                OutlinedButton(onClick = downloader::cancel, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(stringResource(R.string.offline_cancel))
                }
            } else {
                Button(onClick = downloader::start, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Icon(painterResource(R.drawable.ic_cloud_download), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (status.lastCompletedAt == null) R.string.offline_download else R.string.offline_update),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBox(status: OfflineStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp)
    ) {
        val text = MaterialTheme.colorScheme.onPrimaryContainer
        when {
            status.running -> {
                val dataDone = status.total > 0 && status.done >= status.total
                Text(
                    if (!dataDone) stringResource(R.string.offline_progress_data, status.done, status.total)
                    else stringResource(R.string.offline_progress_map, (status.mapProgress * 100).toInt()),
                    color = text,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(
                    progress = {
                        if (!dataDone) status.done.toFloat() / status.total.coerceAtLeast(1) else status.mapProgress
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
            status.failed -> Text(stringResource(R.string.offline_failed), color = MaterialTheme.colorScheme.error)
            status.lastCompletedAt != null -> Text(
                stringResource(R.string.offline_last, formatDate(status.lastCompletedAt)),
                color = text,
                fontWeight = FontWeight.SemiBold
            )
            else -> Text(stringResource(R.string.offline_never), color = text)
        }
        if (status.savedBytes > 0) {
            Text(
                stringResource(R.string.offline_size, status.savedBytes / 1_000_000f),
                style = MaterialTheme.typography.bodySmall,
                color = text,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun formatDate(millis: Long): String =
    DateTimeFormatter.ofPattern("d/M/yyyy HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * The cloud at the top right of the Stops, Map and Lines tabs. It opens the
 * "Use without internet" panel, and is crossed out while saved data is shown
 * (no internet, or the server isn't answering).
 */
@Composable
fun OfflineButton(usingSavedData: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (usingSavedData) R.drawable.ic_cloud_off else R.drawable.ic_cloud_download),
            contentDescription = stringResource(if (usingSavedData) R.string.offline_banner else R.string.offline_title),
            tint = Color.White
        )
    }
}
