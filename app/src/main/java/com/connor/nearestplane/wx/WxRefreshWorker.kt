package com.connor.nearestplane.wx

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.connor.nearestplane.AppSettings
import com.connor.nearestplane.Diagnostics
import com.connor.nearestplane.LocationSource
import java.util.concurrent.TimeUnit

object WxState {
    val STATION = stringPreferencesKey("wx_station")
    val CATEGORY = stringPreferencesKey("wx_category")
    val SUMMARY = stringPreferencesKey("wx_summary")
    val RAW_METAR = stringPreferencesKey("wx_raw_metar")
    val RAW_TAF = stringPreferencesKey("wx_raw_taf")
    val DECODED_METAR = stringPreferencesKey("wx_decoded_metar")
    val DECODED_TAF = stringPreferencesKey("wx_decoded_taf")
    val STATUS = stringPreferencesKey("wx_status")
    val STALE_NOTE = stringPreferencesKey("wx_stale_note")
    val UPDATED_AT = longPreferencesKey("wx_updated_at")
}

class WxRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Diagnostics.noteRun(context, "weather")

        // Readiness, not permission: a pinned place needs neither.
        if (!LocationSource.isReady(context)) {
            write(context) { it[WxState.STATUS] = "no_permission" }
            return Result.success()
        }

        return try {
            val fix = LocationSource.current(context)
            if (fix == null) {
                degrade(context, "no position")
                Diagnostics.noteError(context, "weather", "No position available")
                return Result.retry()
            }

            val metar = AviationWeatherClient.nearestMetar(fix.lat, fix.lon)
            if (metar == null) {
                write(context) {
                    it[WxState.STATUS] = "empty"
                    it[WxState.STATION] = "No station"
                    it[WxState.SUMMARY] = "Nothing reporting within ~45 nm"
                    it[WxState.STALE_NOTE] = fix.staleNote.orEmpty()
                    it[WxState.UPDATED_AT] = System.currentTimeMillis()
                }
                Diagnostics.noteOk(context, "weather")
                return Result.success()
            }

            // A TAF is only issued at larger airports, so a null here is normal.
            val taf = try {
                AviationWeatherClient.taf(metar.stationId)
            } catch (_: Exception) {
                null
            }

            val units = AppSettings.appearance(context).temperature

            write(context) { p ->
                p[WxState.STATUS] = "ok"
                p[WxState.STATION] = metar.stationId
                p[WxState.CATEGORY] = metar.flightCategory
                p[WxState.SUMMARY] = metar.shortSummary(units)
                p[WxState.RAW_METAR] = metar.raw
                p[WxState.DECODED_METAR] = metar.decoded
                p[WxState.RAW_TAF] = taf?.raw.orEmpty()
                p[WxState.DECODED_TAF] = taf?.let { formatTaf(it) }.orEmpty()
                // "Nearest station" is only true relative to where you are, so
                // an old position quietly undermines the whole tile.
                p[WxState.STALE_NOTE] = fix.staleNote.orEmpty()
                p[WxState.UPDATED_AT] = System.currentTimeMillis()
            }
            Diagnostics.noteOk(context, "weather")
            Result.success()
        } catch (e: Exception) {
            degrade(context, "offline")
            Diagnostics.noteError(context, "weather", e.message?.take(90) ?: e.javaClass.simpleName)
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    private fun formatTaf(taf: Taf): String = buildString {
        append("Forecast for ${taf.stationId}")
        taf.issuedEpoch?.let { append("\nIssued ${formatZulu(it)}") }
        taf.periods.forEach { p ->
            append("\n\n")
            append(p.changeType?.uppercase() ?: "FROM")
            append(" ")
            append(formatZulu(p.fromEpoch))
            append(" – ")
            append(formatZulu(p.toEpoch))
            append("\n  ")
            append(p.summary.ifBlank { "no change" })
        }
    }

    private fun formatZulu(epochSeconds: Long?): String {
        if (epochSeconds == null) return "—"
        val fmt = java.text.SimpleDateFormat("dd HH:mm'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(epochSeconds * 1000))
    }

    /**
     * A failed refresh never blanks a working tile. If good data is already
     * there, only a small note changes; the observation stays visible with its
     * age showing. This is what removes the spurious "no data" flash.
     */
    private suspend fun degrade(context: Context, note: String) {
        write(context) { p ->
            val hasData = !p[WxState.STATION].isNullOrBlank()
            if (hasData) {
                // Keep the observation and the status; only the note changes,
                // so the next success clears it without any special handling.
                p[WxState.STATUS] = "ok"
                p[WxState.STALE_NOTE] = note
            } else {
                p[WxState.STATUS] = "error"
                p[WxState.STALE_NOTE] = note
            }
        }
    }

    private suspend fun write(context: Context, block: (MutablePreferences) -> Unit) {
        // Wrapped, like the plane worker's: degrade() calls this on the failure
        // path, and a throw from there would escape doWork's catch.
        runCatching {
            val manager = GlanceAppWidgetManager(context)
            val widget = WeatherWidget()
            manager.getGlanceIds(WeatherWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { block(it) }
                widget.update(context, id)
            }
        }
    }

    companion object {
        private const val PERIODIC_NAME = "nearest-wx-periodic"
        private const val ONE_SHOT_NAME = "nearest-wx-now"

        /** METARs are hourly, so 30 min is plenty and keeps NOAA's load down. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WxRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            // UPDATE, not KEEP: with KEEP an older build's job survives forever
            // and changes here never take effect.
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
                OneTimeWorkRequestBuilder<WxRefreshWorker>().build()
            )
        }
    }
}
