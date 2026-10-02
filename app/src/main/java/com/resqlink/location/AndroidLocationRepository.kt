package com.resqlink.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import com.resqlink.domain.model.LocationSnapshot
import com.resqlink.domain.repository.LocationRepository
import com.resqlink.domain.repository.LocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@SuppressLint("MissingPermission") // Every entry point checks the location permission first.
@Singleton
class AndroidLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationRepository {
    override suspend fun currentLocation(): LocationResult {
        val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) return LocationResult.PermissionDenied
        return try {
            val manager = context.getSystemService(LocationManager::class.java) ?: return LocationResult.Unavailable
            // GPS requires fine permission on older devices; coarse-only users need a network provider.
            val providers = if (fine) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                else listOf(LocationManager.NETWORK_PROVIDER)
            val provider = providers.firstOrNull { it in manager.allProviders && manager.isProviderEnabled(it) }
                ?: return LocationResult.Unavailable
            val location = withTimeoutOrNull(10_000) { acquireLocation(manager, provider) }
            location?.let { LocationResult.Available(it.toSnapshot()) } ?: LocationResult.Unavailable
        } catch (_: SecurityException) {
            // Permissions may be revoked between checking and calling the platform.
            LocationResult.PermissionDenied
        } catch (_: IllegalArgumentException) {
            LocationResult.Unavailable
        } catch (_: IllegalStateException) {
            LocationResult.Unavailable
        }
    }

    /**
     * Returns the freshest cached fix across providers without waiting for a new
     * acquisition, so the outgoing message can carry coordinates immediately.
     */
    override suspend fun lastKnownLocation(): LocationResult {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) return LocationResult.PermissionDenied
        return try {
            val manager = context.getSystemService(LocationManager::class.java) ?: return LocationResult.Unavailable
            val newest = manager.allProviders
                .filter { manager.isProviderEnabled(it) }
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
            newest?.let { LocationResult.Available(it.toSnapshot()) } ?: LocationResult.Unavailable
        } catch (_: SecurityException) {
            LocationResult.PermissionDenied
        } catch (_: IllegalArgumentException) {
            LocationResult.Unavailable
        }
    }

    private fun Location.toSnapshot() = LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        capturedAt = time.takeIf { timestamp -> timestamp > 0 } ?: System.currentTimeMillis(),
        provider = provider ?: "unknown",
    )

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun acquireLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }
}
