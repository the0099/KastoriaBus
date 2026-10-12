package com.the09.kastoriabus.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Finds the phone's current position, once (we don't follow the user around).
 * Uses Android's built-in LocationManager, so no Google Play Services library is needed.
 */
class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Returns the current location, or null if there is no permission or no fix within 10 s. */
    @SuppressLint("MissingPermission") // we check hasPermission() first
    suspend fun getCurrentLocation(): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            val enabled = manager.getProviders(true)

            // A position the phone found in the last 2 minutes is good enough and instant.
            val lastKnown = enabled.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
            if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 2 * 60_000) {
                return lastKnown
            }

            // Otherwise ask for a fresh one. "fused" (Android 12+) mixes GPS, Wi-Fi and mobile network.
            val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { it in enabled } ?: return lastKnown

            val fresh = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        manager, provider, signal, ContextCompat.getMainExecutor(context)
                    ) { location -> if (cont.isActive) cont.resume(location) }
                }
            }
            fresh ?: lastKnown
        } catch (e: SecurityException) {
            null // permission was taken away while we were asking
        }
    }
}
