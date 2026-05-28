package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationProvider {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(lm)) return null

        // Prefer the freshest last-known fix across providers.
        val providers = lm.allProviders.filter { it != LocationManager.PASSIVE_PROVIDER }
        val best = providers.mapNotNull { p ->
            try {
                @Suppress("MissingPermission")
                lm.getLastKnownLocation(p)
            } catch (e: SecurityException) {
                null
            }
        }.maxByOrNull { it.time }

        // If last-known is recent enough (< 10 min), use it directly.
        if (best != null && (System.currentTimeMillis() - best.time) < 10 * 60_000L) return best

        // Otherwise request a single fresh fix with a short timeout.
        return requestSingleUpdate(context, lm) ?: best
    }

    private suspend fun requestSingleUpdate(context: Context, lm: LocationManager): Location? {
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }

        return suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("MissingPermission")
                    lm.getCurrentLocation(
                        provider,
                        null,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }
                        @Deprecated("Required on older APIs")
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    @Suppress("MissingPermission")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
