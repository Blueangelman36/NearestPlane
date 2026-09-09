package com.connor.nearestplane

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class WidgetBackground(val label: String, val blurb: String) {
    DYNAMIC("Material You", "Follows your wallpaper and light/dark mode"),
    SCRIM("Scrim", "Dark translucent panel — readable over anything"),
    TRANSPARENT("Transparent", "No background at all")
}

/** Explicit colours exist because white on a bright wallpaper is unreadable. */
enum class WidgetTextColor(val label: String, val argb: Long?) {
    AUTO("Automatic", null),
    WHITE("White", 0xFFFFFFFF),
    BLACK("Black", 0xFF000000),
    AMBER("Amber", 0xFFFFC107),
    CYAN("Cyan", 0xFF4DD0E1),
    GREEN("Green", 0xFF81C784)
}

enum class WidgetTextScale(val label: String, val factor: Float) {
    COMPACT("Compact", 0.9f),
    NORMAL("Normal", 1.0f),
    LARGE("Large", 1.15f),
    XLARGE("Extra large", 1.3f)
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("app_settings")

/**
 * App-wide appearance, separate from each widget's own Glance state so that one
 * change redraws every placed tile.
 */
object AppSettings {

    private val BACKGROUND = stringPreferencesKey("widget_background")
    private val TEXT_COLOR = stringPreferencesKey("widget_text_color")
    private val TEXT_SCALE = stringPreferencesKey("widget_text_scale")

    data class Appearance(
        val background: WidgetBackground = WidgetBackground.DYNAMIC,
        val textColor: WidgetTextColor = WidgetTextColor.AUTO,
        val textScale: WidgetTextScale = WidgetTextScale.NORMAL
    )

    private fun read(prefs: Preferences) = Appearance(
        background = enumOr(prefs[BACKGROUND], WidgetBackground.DYNAMIC),
        textColor = enumOr(prefs[TEXT_COLOR], WidgetTextColor.AUTO),
        textScale = enumOr(prefs[TEXT_SCALE], WidgetTextScale.NORMAL)
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: fallback.name) }.getOrDefault(fallback)

    /** One-shot suspending read, for widgets inside provideGlance. */
    suspend fun appearance(context: Context): Appearance =
        runCatching { read(context.settingsStore.data.first()) }.getOrDefault(Appearance())

    /** Observable read, for the settings screen. */
    fun appearanceFlow(context: Context): Flow<Appearance> =
        context.settingsStore.data.map { read(it) }

    suspend fun setBackground(context: Context, v: WidgetBackground) {
        context.settingsStore.edit { it[BACKGROUND] = v.name }
    }

    suspend fun setTextColor(context: Context, v: WidgetTextColor) {
        context.settingsStore.edit { it[TEXT_COLOR] = v.name }
    }

    suspend fun setTextScale(context: Context, v: WidgetTextScale) {
        context.settingsStore.edit { it[TEXT_SCALE] = v.name }
    }

    // ---- last known position, so a failed GPS read never blanks a widget ----

    private val LAST_LAT = doublePreferencesKey("last_lat")
    private val LAST_LON = doublePreferencesKey("last_lon")

    suspend fun saveLastPosition(context: Context, lat: Double, lon: Double) {
        context.settingsStore.edit {
            it[LAST_LAT] = lat
            it[LAST_LON] = lon
        }
    }

    suspend fun lastPosition(context: Context): Pair<Double, Double>? = runCatching {
        val prefs = context.settingsStore.data.first()
        val lat = prefs[LAST_LAT]
        val lon = prefs[LAST_LON]
        if (lat != null && lon != null) lat to lon else null
    }.getOrNull()
}
