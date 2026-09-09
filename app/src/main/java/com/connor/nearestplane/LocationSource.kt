package com.connor.nearestplane

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Where the fix came from — useful for deciding whether to trust it. */
data class Position(val lat: Double, val lon: Double, val fresh: Boolean)

object LocationSource {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Three-tier fallback, in order: a current fix, the system's last known
     * position, then our own cached position from a previous successful run.
     *
     * The third tier is what removes the "no GPS" state. In Doze the fused
     * provider frequently returns null for both live tiers, and there is no
     * reason to blank a widget over that — your position an hour ago is a
     * perfectly good basis for "what's the nearest airport" and close enough
     * for "what's overhead".
     */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Position? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(10 * 60 * 1000L)
            .setDurationMillis(10_000L)
            .build()

        val fresh = runCatching {
            suspendCancellableCoroutine { cont ->
                client.getCurrentLocation(request, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }.getOrNull()

        if (fresh != null) {
            AppSettings.saveLastPosition(context, fresh.latitude, fresh.longitude)
            return Position(fresh.latitude, fresh.longitude, true)
        }

        val last = runCatching {
            suspendCancellableCoroutine { cont ->
                client.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }.getOrNull()

        if (last != null) {
            AppSettings.saveLastPosition(context, last.latitude, last.longitude)
            return Position(last.latitude, last.longitude, true)
        }

        return AppSettings.lastPosition(context)?.let { (lat, lon) ->
            Position(lat, lon, false)
        }
    }
}
