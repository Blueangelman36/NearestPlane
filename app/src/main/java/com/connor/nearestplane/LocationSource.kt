package com.connor.nearestplane

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Which of the three tiers a fix came from, best first. */
enum class PositionSource { LIVE, LAST_KNOWN, CACHED }

data class Position(
    val lat: Double,
    val lon: Double,
    val source: PositionSource,
    val ageMinutes: Long?
) {
    /**
     * A short note for the tile when the position is old enough to matter, or
     * null when it isn't worth the space.
     *
     * This is the failure mode the three-tier fallback quietly introduced. A
     * tile reading "3.2 nm NNE" off an hour-old position isn't wrong about the
     * aircraft — it's wrong about *you*, and that's the harder error to spot,
     * because everything on the tile still looks live.
     */
    val staleNote: String?
        get() {
            val age = ageMinutes
                ?: return if (source == PositionSource.LIVE) null else "old position"
            if (age < 20) return null
            return if (age < 120) "position ${age}m old" else "position ${age / 60}h old"
        }
}

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
     *
     * Every tier reports its own age, so the widget can say when it's working
     * from something old rather than presenting it as a live reading.
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

        await { client.getCurrentLocation(request, null) }?.let {
            return remember(context, it, PositionSource.LIVE)
        }

        // lastLocation has no age bound at all — it can be days old, which is
        // exactly why the age travels with the position from here on.
        await { client.lastLocation }?.let {
            return remember(context, it, PositionSource.LAST_KNOWN)
        }

        return AppSettings.lastPosition(context)?.let { cached ->
            Position(cached.lat, cached.lon, PositionSource.CACHED, cached.ageMinutes)
        }
    }

    private suspend fun await(start: () -> Task<Location>): Location? = runCatching {
        suspendCancellableCoroutine { cont ->
            start()
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }.getOrNull()

    private suspend fun remember(
        context: Context,
        location: Location,
        source: PositionSource
    ): Position {
        AppSettings.saveLastPosition(context, location.latitude, location.longitude, location.time)
        return Position(location.latitude, location.longitude, source, ageOf(location.time))
    }

    /** Location.time is the epoch millis of the fix itself, not of this read. */
    private fun ageOf(fixTimeMs: Long): Long? {
        if (fixTimeMs <= 0L) return null
        return ((System.currentTimeMillis() - fixTimeMs) / 60_000L).coerceAtLeast(0L)
    }
}
