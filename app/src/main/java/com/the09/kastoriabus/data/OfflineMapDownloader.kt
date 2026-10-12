package com.the09.kastoriabus.data

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Saves the map of the Kastoria area on the phone (MapLibre "offline region"),
 * so the Map tab works without internet. About 5-10 MB.
 */
class OfflineMapDownloader(private val context: Context) {

    /** Downloads (or finishes downloading) the Kastoria map. [onProgress] gets 0.0 .. 1.0. */
    suspend fun download(onProgress: (Float) -> Unit) {
        val manager = OfflineManager.getInstance(context)
        val region = existingRegion(manager) ?: createRegion(manager)
        suspendCancellableCoroutine { cont ->
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    if (status.requiredResourceCount > 0) {
                        onProgress(status.completedResourceCount.toFloat() / status.requiredResourceCount)
                    }
                    if (status.isComplete && cont.isActive) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        cont.resume(Unit)
                    }
                }

                override fun onError(error: OfflineRegionError) {
                    // Single tile errors are retried by MapLibre; we just keep waiting.
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    if (cont.isActive) cont.resumeWithException(IOException("Map too big ($limit tiles)"))
                }
            })
            cont.invokeOnCancellation { region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        }
    }

    private suspend fun existingRegion(manager: OfflineManager): OfflineRegion? =
        suspendCancellableCoroutine { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    cont.resume(offlineRegions?.firstOrNull())
                }

                override fun onError(error: String) {
                    cont.resume(null)
                }
            })
        }

    private suspend fun createRegion(manager: OfflineManager): OfflineRegion =
        suspendCancellableCoroutine { cont ->
            val definition = OfflineTilePyramidRegionDefinition(
                STYLE_URL,
                // The area the citybus site shows: Kastoria and its villages.
                LatLngBounds.from(40.580043, 21.363929, 40.440979, 21.041117),
                9.0,   // zoomed out: whole area
                16.0,  // zoomed in: streets
                context.resources.displayMetrics.density,
            )
            manager.createOfflineRegion(definition, "kastoria".toByteArray(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) = cont.resume(offlineRegion)
                    override fun onError(error: String) = cont.resumeWithException(IOException(error))
                })
        }

    companion object {
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    }
}
