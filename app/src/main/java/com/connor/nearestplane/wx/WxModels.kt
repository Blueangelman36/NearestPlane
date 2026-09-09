package com.connor.nearestplane.wx

import kotlin.math.roundToInt
import java.util.Locale

data class Cloud(val cover: String, val baseFt: Int?)

data class Metar(
    val stationId: String,
    val siteName: String?,
    val lat: Double?,
    val lon: Double?,
    val distanceNm: Double?,
    val observedEpoch: Long?,
    val raw: String,
    val tempC: Double?,
    val dewpointC: Double?,
    val windDirDeg: Int?,        // null when variable
    val windKts: Int?,
    val gustKts: Int?,
    val visibilitySm: Double?,
    val altimeterInHg: Double?,
    val weather: String?,
    val clouds: List<Cloud>
) {
    /** Lowest broken or overcast layer — what actually defines a ceiling. */
    val ceilingFt: Int?
        get() = clouds
            .filter { it.cover.uppercase() in setOf("BKN", "OVC", "OVX") }
            .mapNotNull { it.baseFt }
            .minOrNull()

    val flightCategory: String
        get() {
            val vis = visibilitySm
            val ceil = ceilingFt
            return when {
                (ceil != null && ceil < 500) || (vis != null && vis < 1.0) -> "LIFR"
                (ceil != null && ceil < 1000) || (vis != null && vis < 3.0) -> "IFR"
                (ceil != null && ceil <= 3000) || (vis != null && vis <= 5.0) -> "MVFR"
                else -> "VFR"
            }
        }

    /** One line for the widget: "310@12G20 · 10sm · BKN035 · 22/14" */
    val shortSummary: String
        get() = buildList {
            add(windText(windDirDeg, windKts, gustKts))
            visibilitySm?.let { add(visText(it)) }
            add(cloudsShort())
            if (tempC != null && dewpointC != null) {
                add("${tempC.roundToInt()}/${dewpointC.roundToInt()}")
            }
        }.filter { it.isNotBlank() }.joinToString(" · ")

    private fun cloudsShort(): String {
        if (clouds.isEmpty()) return ""
        val first = clouds.firstOrNull {
            it.cover.uppercase() in setOf("BKN", "OVC", "OVX")
        } ?: clouds.first()
        return layerCode(first)
    }

    /** Multi-line plain English, for the detail screen. */
    val decoded: String
        get() = buildList {
            add("Station: $stationId${siteName?.let { " — $it" } ?: ""}")
            distanceNm?.let { add("Distance: ${"%.1f".format(Locale.US, it)} nm from you") }
            add("Conditions: $flightCategory")
            add("Wind: ${windLong(windDirDeg, windKts, gustKts)}")
            visibilitySm?.let { add("Visibility: ${visText(it)}") }
            add("Sky: ${cloudsLong()}")
            weather?.takeIf { it.isNotBlank() }?.let { add("Present weather: ${decodeWeather(it)}") }
            tempC?.let { add("Temperature: ${it.roundToInt()}°C (${cToF(it)}°F)") }
            dewpointC?.let { add("Dewpoint: ${it.roundToInt()}°C (${cToF(it)}°F)") }
            if (tempC != null && dewpointC != null) {
                add("Relative humidity: ${relativeHumidity(tempC, dewpointC)}%")
            }
            altimeterInHg?.let { add("Altimeter: ${"%.2f".format(Locale.US, it)} inHg") }
            ceilingFt?.let { add("Ceiling: ${"%,d".format(Locale.US, it)} ft AGL") }
        }.joinToString("\n")

    private fun cloudsLong(): String {
        if (clouds.isEmpty()) return "no cloud data"
        return clouds.joinToString(", ") { c ->
            val name = coverName(c.cover)
            c.baseFt?.let { "$name at ${"%,d".format(Locale.US, it)} ft" } ?: name
        }
    }
}

data class TafPeriod(
    val fromEpoch: Long?,
    val toEpoch: Long?,
    val changeType: String?,   // FM, TEMPO, BECMG, PROB30...
    val windDirDeg: Int?,
    val windKts: Int?,
    val gustKts: Int?,
    val visibilitySm: Double?,
    val weather: String?,
    val clouds: List<Cloud>
) {
    val summary: String
        get() = buildList {
            add(windText(windDirDeg, windKts, gustKts))
            visibilitySm?.let { add(visText(it)) }
            if (clouds.isNotEmpty()) add(clouds.joinToString(" ") { layerCode(it) })
            weather?.takeIf { it.isNotBlank() }?.let { add(decodeWeather(it)) }
        }.filter { it.isNotBlank() }.joinToString(" · ")
}

data class Taf(
    val stationId: String,
    val raw: String,
    val issuedEpoch: Long?,
    val periods: List<TafPeriod>
)

// ---- shared formatting helpers ----

fun windText(dir: Int?, kts: Int?, gust: Int?): String = when {
    kts == null -> ""
    kts == 0 -> "calm"
    dir == null -> "VRB@$kts${gust?.let { "G$it" } ?: ""}"
    else -> "$dir°@$kts${gust?.let { "G$it" } ?: ""}"
}

fun windLong(dir: Int?, kts: Int?, gust: Int?): String = when {
    kts == null -> "not reported"
    kts == 0 -> "calm"
    else -> buildString {
        append(if (dir == null) "variable" else "from $dir°")
        append(" at $kts kt")
        gust?.let { append(", gusting $it kt") }
    }
}

fun visText(sm: Double): String =
    if (sm >= 10) "10+ sm" else "${"%.1f".format(Locale.US, sm).removeSuffix(".0")} sm"

fun layerCode(c: Cloud): String =
    c.baseFt?.let { "${c.cover.uppercase()}${(it / 100).toString().padStart(3, '0')}" }
        ?: c.cover.uppercase()

fun coverName(cover: String): String = when (cover.uppercase()) {
    "SKC", "CLR", "CAVOK", "NCD", "NSC" -> "clear"
    "FEW" -> "few clouds"
    "SCT" -> "scattered"
    "BKN" -> "broken"
    "OVC" -> "overcast"
    "OVX" -> "obscured"
    else -> cover
}

private fun cToF(c: Double): Int = (c * 9 / 5 + 32).roundToInt()

private fun relativeHumidity(tempC: Double, dewC: Double): Int {
    val e = { t: Double -> 6.112 * Math.exp((17.67 * t) / (t + 243.5)) }
    return ((e(dewC) / e(tempC)) * 100).roundToInt().coerceIn(0, 100)
}

/** Covers the codes you'll actually see; unknown groups pass through raw. */
fun decodeWeather(raw: String): String {
    val intensity = mapOf("-" to "light ", "+" to "heavy ", "VC" to "nearby ")
    val descriptors = mapOf(
        "MI" to "shallow ", "PR" to "partial ", "BC" to "patches of ", "DR" to "drifting ",
        "BL" to "blowing ", "SH" to "showers of ", "TS" to "thunderstorm ", "FZ" to "freezing "
    )
    val phenomena = mapOf(
        "DZ" to "drizzle", "RA" to "rain", "SN" to "snow", "SG" to "snow grains",
        "IC" to "ice crystals", "PL" to "ice pellets", "GR" to "hail", "GS" to "small hail",
        "UP" to "unknown precipitation", "BR" to "mist", "FG" to "fog", "FU" to "smoke",
        "VA" to "volcanic ash", "DU" to "dust", "SA" to "sand", "HZ" to "haze",
        "PY" to "spray", "PO" to "dust whirls", "SQ" to "squalls", "FC" to "funnel cloud",
        "SS" to "sandstorm", "DS" to "duststorm"
    )

    return raw.trim().split(Regex("\\s+")).joinToString(", ") { group ->
        var rest = group
        val out = StringBuilder()
        intensity.keys.firstOrNull { rest.startsWith(it) }?.let {
            out.append(intensity[it]); rest = rest.removePrefix(it)
        }
        descriptors.keys.firstOrNull { rest.startsWith(it) }?.let {
            out.append(descriptors[it]); rest = rest.removePrefix(it)
        }
        while (rest.length >= 2 && phenomena.containsKey(rest.take(2))) {
            out.append(phenomena[rest.take(2)]).append(" ")
            rest = rest.drop(2)
        }
        out.toString().trim().ifBlank { group }
    }
}
