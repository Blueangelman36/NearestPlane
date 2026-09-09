package com.connor.nearestplane

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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

/** METARs are always in Celsius; this is only about how the tile reads it back. */
enum class TemperatureUnit(val label: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

enum class SearchRadius(val label: String, val nm: Int) {
    NM10("10 nm", 10),
    NM25("25 nm", 25),
    NM50("50 nm", 50),
    NM100("100 nm", 100)
}

/**
 * An altitude ceiling is the difference between a widget that shows what's
 * actually overhead and one that shows an airliner at FL380 forty miles away
 * every single time. Which of those you want depends entirely on whether you
 * live under an airway.
 */
enum class AltitudeCeiling(val label: String, val maxFt: Int?) {
    ANY("Any altitude", null),
    BELOW_18000("Below 18,000 ft", 18_000),
    BELOW_10000("Below 10,000 ft", 10_000),
    BELOW_5000("Below 5,000 ft", 5_000)
}

enum class LocationMode(val label: String, val blurb: String) {
    DEVICE(
        "Follow this device",
        "Uses GPS. Needs \"Allow all the time\" to keep updating with the screen off."
    ),
    FIXED(
        "A fixed place",
        "A pinned airport or position. No background location permission at all."
    )
}

/** A pinned place. [label] is what the settings screen reads back to you. */
data class FixedLocation(val label: String, val lat: Double, val lon: Double)

data class CachedPosition(val lat: Double, val lon: Double, val ageMinutes: Long?)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("app_settings")

/**
 * App-wide settings, separate from each widget's own Glance state so that one
 * change redraws every placed tile.
 */
object AppSettings {

    private val BACKGROUND = stringPreferencesKey("widget_background")
    private val TEXT_COLOR = stringPreferencesKey("widget_text_color")
    private val TEXT_SCALE = stringPreferencesKey("widget_text_scale")
    private val TEMPERATURE = stringPreferencesKey("temperature_unit")
    private val RADIUS = stringPreferencesKey("search_radius")
    private val CEILING = stringPreferencesKey("altitude_ceiling")
    private val LOCATION_MODE = stringPreferencesKey("location_mode")
    private val PIN_LABEL = stringPreferencesKey("pin_label")
    private val PIN_LAT = doublePreferencesKey("pin_lat")
    private val PIN_LON = doublePreferencesKey("pin_lon")

    data class Appearance(
        val background: WidgetBackground = WidgetBackground.DYNAMIC,
        val textColor: WidgetTextColor = WidgetTextColor.AUTO,
        val textScale: WidgetTextScale = WidgetTextScale.NORMAL,
        val temperature: TemperatureUnit = TemperatureUnit.CELSIUS
    )

    /** What the plane widget looks for, as opposed to how it looks. */
    data class PlaneFilter(
        val radius: SearchRadius = SearchRadius.NM50,
        val ceiling: AltitudeCeiling = AltitudeCeiling.ANY
    )

    private fun readAppearance(p: Preferences) = Appearance(
        background = enumOr(p[BACKGROUND], WidgetBackground.DYNAMIC),
        textColor = enumOr(p[TEXT_COLOR], WidgetTextColor.AUTO),
        textScale = enumOr(p[TEXT_SCALE], WidgetTextScale.NORMAL),
        temperature = enumOr(p[TEMPERATURE], TemperatureUnit.CELSIUS)
    )

    private fun readFilter(p: Preferences) = PlaneFilter(
        radius = enumOr(p[RADIUS], SearchRadius.NM50),
        ceiling = enumOr(p[CEILING], AltitudeCeiling.ANY)
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: fallback.name) }.getOrDefault(fallback)

    // ---- how the tiles look ----

    /** One-shot suspending read, for widgets inside provideGlance. */
    suspend fun appearance(context: Context): Appearance =
        runCatching { readAppearance(context.settingsStore.data.first()) }.getOrDefault(Appearance())

    /** Observable read, for the settings screen. */
    fun appearanceFlow(context: Context): Flow<Appearance> =
        context.settingsStore.data.map { readAppearance(it) }

    suspend fun setBackground(context: Context, v: WidgetBackground) {
        context.settingsStore.edit { it[BACKGROUND] = v.name }
    }

    suspend fun setTextColor(context: Context, v: WidgetTextColor) {
        context.settingsStore.edit { it[TEXT_COLOR] = v.name }
    }

    suspend fun setTextScale(context: Context, v: WidgetTextScale) {
        context.settingsStore.edit { it[TEXT_SCALE] = v.name }
    }

    suspend fun setTemperature(context: Context, v: TemperatureUnit) {
        context.settingsStore.edit { it[TEMPERATURE] = v.name }
    }

    // ---- what the plane widget looks for ----

    suspend fun planeFilter(context: Context): PlaneFilter =
        runCatching { readFilter(context.settingsStore.data.first()) }.getOrDefault(PlaneFilter())

    fun planeFilterFlow(context: Context): Flow<PlaneFilter> =
        context.settingsStore.data.map { readFilter(it) }

    suspend fun setRadius(context: Context, v: SearchRadius) {
        context.settingsStore.edit { it[RADIUS] = v.name }
    }

    suspend fun setCeiling(context: Context, v: AltitudeCeiling) {
        context.settingsStore.edit { it[CEILING] = v.name }
    }

    // ---- where "here" is ----

    fun locationModeFlow(context: Context): Flow<LocationMode> =
        context.settingsStore.data.map { enumOr(it[LOCATION_MODE], LocationMode.DEVICE) }

    suspend fun setLocationMode(context: Context, v: LocationMode) {
        context.settingsStore.edit { it[LOCATION_MODE] = v.name }
    }

    /**
     * The pin as stored, whatever the mode. The settings screen reads this, so
     * switching to device mode and back doesn't lose the pinned place.
     */
    fun pinFlow(context: Context): Flow<FixedLocation?> =
        context.settingsStore.data.map { readPin(it) }

    /**
     * The pin *in effect* — null in device mode, so callers that just want a
     * position can ask for this and ignore the mode entirely.
     */
    suspend fun fixedLocation(context: Context): FixedLocation? = runCatching {
        val p = context.settingsStore.data.first()
        if (enumOr(p[LOCATION_MODE], LocationMode.DEVICE) != LocationMode.FIXED) null
        else readPin(p)
    }.getOrNull()

    private fun readPin(p: Preferences): FixedLocation? {
        val lat = p[PIN_LAT] ?: return null
        val lon = p[PIN_LON] ?: return null
        return FixedLocation(p[PIN_LABEL].orEmpty().ifBlank { "Pinned place" }, lat, lon)
    }

    /** Pinning somewhere is also the act of choosing fixed mode. */
    suspend fun savePin(context: Context, label: String, lat: Double, lon: Double) {
        context.settingsStore.edit {
            it[PIN_LABEL] = label
            it[PIN_LAT] = lat
            it[PIN_LON] = lon
            it[LOCATION_MODE] = LocationMode.FIXED.name
        }
    }

    // ---- last known position, so a failed GPS read never blanks a widget ----

    private val LAST_LAT = doublePreferencesKey("last_lat")
    private val LAST_LON = doublePreferencesKey("last_lon")
    private val LAST_FIX_AT = longPreferencesKey("last_fix_at")

    /** [fixTimeMs] is when the fix was taken, which is not when it was saved. */
    suspend fun saveLastPosition(context: Context, lat: Double, lon: Double, fixTimeMs: Long) {
        context.settingsStore.edit {
            it[LAST_LAT] = lat
            it[LAST_LON] = lon
            it[LAST_FIX_AT] = if (fixTimeMs > 0L) fixTimeMs else System.currentTimeMillis()
        }
    }

    suspend fun lastPosition(context: Context): CachedPosition? = runCatching {
        val prefs = context.settingsStore.data.first()
        val lat = prefs[LAST_LAT] ?: return@runCatching null
        val lon = prefs[LAST_LON] ?: return@runCatching null
        val fixedAt = prefs[LAST_FIX_AT] ?: 0L
        CachedPosition(
            lat = lat,
            lon = lon,
            // Written before this key existed, so its age is genuinely unknown.
            ageMinutes = if (fixedAt > 0L) {
                ((System.currentTimeMillis() - fixedAt) / 60_000L).coerceAtLeast(0L)
            } else null
        )
    }.getOrNull()
}
