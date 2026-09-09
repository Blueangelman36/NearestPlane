package com.connor.nearestplane

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.diagStore: DataStore<Preferences> by preferencesDataStore("diagnostics")

/**
 * Background workers fail silently by design — there's no console to watch and
 * no crash to see. This records the last attempt and the last error for each
 * worker so the app can show what actually happened.
 */
object Diagnostics {

    private fun lastRun(w: String) = longPreferencesKey("${w}_last_run")
    private fun lastOk(w: String) = longPreferencesKey("${w}_last_ok")
    private fun lastError(w: String) = stringPreferencesKey("${w}_last_error")
    private fun lastErrorAt(w: String) = longPreferencesKey("${w}_last_error_at")

    suspend fun noteRun(context: Context, worker: String) {
        runCatching {
            context.diagStore.edit { it[lastRun(worker)] = System.currentTimeMillis() }
        }
    }

    suspend fun noteOk(context: Context, worker: String) {
        runCatching {
            context.diagStore.edit {
                it[lastOk(worker)] = System.currentTimeMillis()
                it[lastError(worker)] = ""
            }
        }
    }

    suspend fun noteError(context: Context, worker: String, message: String) {
        runCatching {
            context.diagStore.edit {
                it[lastError(worker)] = message
                it[lastErrorAt(worker)] = System.currentTimeMillis()
            }
        }
    }

    data class Report(
        val lastRun: Long,
        val lastOk: Long,
        val lastError: String,
        val lastErrorAt: Long
    )

    fun report(context: Context, worker: String): Flow<Report> =
        context.diagStore.data.map { p ->
            Report(
                lastRun = p[lastRun(worker)] ?: 0L,
                lastOk = p[lastOk(worker)] ?: 0L,
                lastError = p[lastError(worker)].orEmpty(),
                lastErrorAt = p[lastErrorAt(worker)] ?: 0L
            )
        }

    /**
     * The single most common reason background refresh stops: the app is not
     * exempt from battery optimisation, so Doze defers its jobs indefinitely.
     */
    fun isBatteryOptimized(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun timeAgo(epochMs: Long): String {
        if (epochMs == 0L) return "never"
        val mins = (System.currentTimeMillis() - epochMs) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 1440 -> "${mins / 60} hr ago"
            else -> "${mins / 1440} days ago"
        }
    }

    fun clockTime(epochMs: Long): String {
        if (epochMs == 0L) return "—"
        return SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMs))
    }
}
