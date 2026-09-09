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

        // Readiness, not permission: a pinned place needs neither.
        if (!LocationSource.isReady(context)) {
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

        val filter = AppSettings.planeFilter(context)

        // Only the aircraft fetch can fail the job. Everything after it is
        // enrichment, and enrichment failing must never cost you the tile.
        val plane = try {
            AdsbClient.nearest(
                lat = fix.lat,
                lon = fix.lon,
                radiusNm = filter.radius.nm,
                maxAltitudeFt = filter.ceiling.maxFt
            )
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
                it[WidgetState.POSITION] = buildString {
                    append("Nothing within ${filter.radius.nm} nm")
                    filter.ceiling.maxFt?.let { append(" ${filter.ceiling.label.lowercase()}") }
                }
                it[WidgetState.DETAIL] = ""
                it[WidgetState.EMERGENCY] = ""
                it[WidgetState.STALE_NOTE] = fix.staleNote.orEmpty()
                it[WidgetState.UPDATED_AT] = System.currentTimeMillis()
            }
            Diagnostics.noteOk(context, "plane")
            return Result.success()
        }

        // Shared with the detail screen, so the two can't disagree about a route.
        val report = PlaneSummary.build(context, plane)
        report.enrichError?.let { Diagnostics.noteError(context, "plane", "enrich: $it") }

        write(context) { p ->
            p[WidgetState.STATUS] = "ok"
            p[WidgetState.TITLE] = plane.label
            p[WidgetState.AIRLINE] = report.operator
            p[WidgetState.TYPE_NAME] = report.typeName.orEmpty()
            p[WidgetState.ROUTE] = report.routeLine
            p[WidgetState.TIMING] = report.timingLine
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
