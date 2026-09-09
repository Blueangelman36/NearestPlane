package com.connor.nearestplane

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Diagnostics.noteRun(context, "plane")

        if (!LocationSource.hasPermission(context)) {
            write(context) { it[WidgetState.STATUS] = "no_permission" }
            return Result.success()
        }

        // Background work has no console and no crash dialog, so an exception
        // escaping from here would be invisible. Anything unexpected below is
        // caught, recorded and degraded to a stale tile.
        return try {
            refresh(context)
        } catch (e: Exception) {
            degrade(context, "offline")
            Diagnostics.noteError(context, "plane", e.message?.take(90) ?: e.javaClass.simpleName)
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    private suspend fun refresh(context: Context): Result {
        val fix = LocationSource.current(context)
        if (fix == null) {
            degrade(context, "no position")
            Diagnostics.noteError(context, "plane", "No position available")
            return Result.retry()
        }

        // Only the aircraft fetch can fail the job. Everything after it is
        // enrichment, and enrichment failing must never cost you the tile.
        val plane = try {
            AdsbClient.nearest(fix.lat, fix.lon, RADIUS_NM)
        } catch (e: Exception) {
            val msg = e.message?.take(90) ?: e.javaClass.simpleName
            degrade(context, "offline")
            Diagnostics.noteError(context, "plane", msg)
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }

        if (plane == null) {
            write(context) {
                it[WidgetState.STATUS] = "empty"
                it[WidgetState.TITLE] = "Clear skies"
                it[WidgetState.AIRLINE] = ""
                it[WidgetState.TYPE_NAME] = ""
                it[WidgetState.ROUTE] = ""
                it[WidgetState.TIMING] = ""
                it[WidgetState.POSITION] = "Nothing within $RADIUS_NM nm"
                it[WidgetState.DETAIL] = ""
                it[WidgetState.EMERGENCY] = ""
                it[WidgetState.STALE_NOTE] = fix.staleNote.orEmpty()
                it[WidgetState.UPDATED_AT] = System.currentTimeMillis()
            }
            Diagnostics.noteOk(context, "plane")
            return Result.success()
        }

        // Enrichment is fully isolated: a failure here costs detail, not the tile.
        val extra = try {
            AdsbdbClient.lookup(context, plane.hex, plane.callsign)
        } catch (e: Exception) {
            Diagnostics.noteError(context, "plane", "enrich: ${e.javaClass.simpleName}")
            AdsbdbResult(null, null)
        }

        val quality = runCatching {
            RouteMath.assessRoute(
                callsign = plane.callsign,
                planeLat = plane.lat,
                planeLon = plane.lon,
                trackDeg = plane.trackDeg,
                groundSpeedKts = plane.groundSpeedKts,
                altitudeFt = plane.altitudeFt,
                destination = extra.route?.destination
            )
        }.getOrDefault(RouteQuality.UNCERTAIN)

        val route = extra.route.takeIf { quality != RouteQuality.CONTRADICTED }
        val routeLine = runCatching { buildRouteLine(route, quality) }.getOrDefault("")
        val timingLine = if (quality == RouteQuality.GOOD) {
            runCatching { buildTimingLine(plane, route) }.getOrDefault("")
        } else ""

        val typeName = extra.aircraft?.fullName ?: plane.typeName
        val operator: String = extra.route?.airline
            ?: extra.aircraft?.owner
            ?: plane.operator.orEmpty()

        write(context) { p ->
            p[WidgetState.STATUS] = "ok"
            p[WidgetState.TITLE] = plane.label
            p[WidgetState.AIRLINE] = operator
            p[WidgetState.TYPE_NAME] = typeName.orEmpty()
            p[WidgetState.ROUTE] = routeLine
            p[WidgetState.TIMING] = timingLine
            p[WidgetState.POSITION] = plane.positionLine
            p[WidgetState.DETAIL] = plane.detail
            p[WidgetState.EMERGENCY] = plane.emergency.orEmpty()
            // An old position is worth saying out loud: the distance and bearing
            // are measured from it, and nothing else on the tile hints at that.
            p[WidgetState.STALE_NOTE] = fix.staleNote.orEmpty()
            p[WidgetState.UPDATED_AT] = System.currentTimeMillis()
        }
        Diagnostics.noteOk(context, "plane")
        return Result.success()
    }

    private fun buildRouteLine(route: FlightRoute?, quality: RouteQuality): String {
        val from = route?.origin?.display?.ifBlank { null }
        val to = route?.destination?.display?.ifBlank { null }
        val base = when {
            from != null && to != null -> "$from \u2192 $to"
            from != null -> "from $from"
            to != null -> "to $to"
            else -> return ""
        }
        return if (quality == RouteQuality.UNCERTAIN) "$base ?" else base
    }

    private fun buildTimingLine(plane: Aircraft, route: FlightRoute?): String {
        if (plane.lat == null || plane.lon == null || route == null) return ""
        val parts = mutableListOf<String>()
        route.origin?.let { origin ->
            RouteMath.minutesSinceDeparture(plane.lat, plane.lon, origin, plane.groundSpeedKts)
                ?.let { parts.add("~${RouteMath.formatDuration(it)} out") }
        }
        route.destination?.let { dest ->
            RouteMath.minutesRemaining(plane.lat, plane.lon, dest, plane.groundSpeedKts)
                ?.let { parts.add("~${RouteMath.formatDuration(it)} to go") }
        }
        if (route.origin != null && route.destination != null) {
            RouteMath.progressPercent(plane.lat, plane.lon, route.origin, route.destination)
                ?.let { parts.add("$it%") }
        }
        return parts.joinToString(" · ")
    }

    /**
     * A failed refresh never blanks a working tile, and never latches.
     *
     * Previously a failure set STATUS to "error", which swapped the whole tile
     * for an error screen. Now, if there's usable data, only the corner note
     * changes and STATUS stays "ok" — so the next success clears it by itself.
     * The error screen is reserved for a tile that has never had data at all.
     */
    private suspend fun degrade(context: Context, note: String) {
        write(context) { p ->
            val hasData = !p[WidgetState.TITLE].isNullOrBlank()
            if (hasData) {
                p[WidgetState.STATUS] = "ok"
                p[WidgetState.STALE_NOTE] = note
            } else {
                p[WidgetState.STATUS] = "error"
                p[WidgetState.STALE_NOTE] = note
            }
        }
    }

    private suspend fun write(context: Context, block: (MutablePreferences) -> Unit) {
        runCatching {
            val manager = GlanceAppWidgetManager(context)
            val widget = NearestPlaneWidget()
            manager.getGlanceIds(NearestPlaneWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { block(it) }
                widget.update(context, id)
            }
        }
    }

    companion object {
        const val RADIUS_NM = 50

        private const val PERIODIC_NAME = "nearest-plane-periodic"
        private const val ONE_SHOT_NAME = "nearest-plane-now"

        /**
         * UPDATE, not KEEP. With KEEP, the job enqueued by an older build lives
         * forever and every change made here is silently ignored — including
         * this one, until the app is reinstalled.
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RefreshWorker>()
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                    .build()
            )
        }
    }
}
