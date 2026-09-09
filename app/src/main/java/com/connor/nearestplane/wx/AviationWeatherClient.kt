package com.connor.nearestplane.wx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Locale

/**
 * NOAA's Aviation Weather Center data API. Free, no key, no rate limit
 * published — but they ask that clients keep the load light, which is why
 * this refreshes on a 30-minute cadence. METARs only update hourly anyway.
 */
object AviationWeatherClient {

    private const val BASE = "https://aviationweather.gov/api/data"
    private const val TIMEOUT_MS = 12_000

    /**
     * Finds the closest reporting station by pulling every METAR in a box
     * around you, then sorting by great-circle distance.
     *
     * [boxDeg] of 0.75 is roughly a 45 nm half-width — wide enough to catch a
     * station almost anywhere in the US, narrow enough that the response
     * stays small.
     */
    suspend fun nearestMetar(
        lat: Double,
        lon: Double,
        boxDeg: Double = 0.75
    ): Metar? = withContext(Dispatchers.IO) {
        val bbox = "%.3f,%.3f,%.3f,%.3f".format(
            Locale.US, lat - boxDeg, lon - boxDeg, lat + boxDeg, lon + boxDeg
        )
        val body = get("$BASE/metar?bbox=$bbox&format=json&hours=3")
        val arr = JSONArray(body)

        (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .mapNotNull { parseMetar(it, lat, lon) }
            // Several hours of reports come back — keep the newest per station.
            .groupBy { it.stationId }
            .mapNotNull { (_, reports) -> reports.maxByOrNull { it.observedEpoch ?: 0L } }
            .minByOrNull { it.distanceNm ?: Double.MAX_VALUE }
    }

    suspend fun taf(stationId: String): Taf? = withContext(Dispatchers.IO) {
        val body = get("$BASE/taf?ids=$stationId&format=json")
        val arr = JSONArray(body)
        if (arr.length() == 0) return@withContext null
        parseTaf(arr.getJSONObject(0))
    }

    // ---- HTTP ----

    private fun get(url: String): String =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "NearestPlaneWidget/1.0")
            setRequestProperty("Accept", "application/json")
            try {
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Aviation Weather API returned HTTP $responseCode")
                }
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }

    // ---- parsing ----

    private fun parseMetar(o: JSONObject, fromLat: Double, fromLon: Double): Metar? {
        val id = o.optString("icaoId", "").ifBlank { return null }
        val raw = o.optString("rawOb", "").ifBlank { return null }
        val sLat = o.optDoubleOrNull("lat")
        val sLon = o.optDoubleOrNull("lon")

        return Metar(
            stationId = id,
            siteName = o.optString("name", "").ifBlank { null },
            lat = sLat,
            lon = sLon,
            distanceNm = if (sLat != null && sLon != null)
                haversineNm(fromLat, fromLon, sLat, sLon) else null,
            observedEpoch = o.optLongOrNull("obsTime"),
            raw = raw,
            tempC = o.optDoubleOrNull("temp"),
            dewpointC = o.optDoubleOrNull("dewp"),
            windDirDeg = o.optWindDir(),
            windKts = o.optIntOrNull("wspd"),
            gustKts = o.optIntOrNull("wgst"),
            visibilitySm = o.optVisibility(),
            altimeterInHg = o.optDoubleOrNull("altim")?.let { mbToInHg(it) },
            weather = o.optString("wxString", "").ifBlank { null },
            clouds = parseClouds(o.optJSONArray("clouds"))
        )
    }

    private fun parseTaf(o: JSONObject): Taf {
        val fcsts = o.optJSONArray("fcsts")
        val periods = (0 until (fcsts?.length() ?: 0)).map { i ->
            val f = fcsts!!.getJSONObject(i)
            TafPeriod(
                fromEpoch = f.optLongOrNull("timeFrom"),
                toEpoch = f.optLongOrNull("timeTo"),
                changeType = f.optString("fcstChange", "").ifBlank { null },
                windDirDeg = f.optWindDir(),
                windKts = f.optIntOrNull("wspd"),
                gustKts = f.optIntOrNull("wgst"),
                visibilitySm = f.optVisibility(),
                weather = f.optString("wxString", "").ifBlank { null },
                clouds = parseClouds(f.optJSONArray("clouds"))
            )
        }
        return Taf(
            stationId = o.optString("icaoId", "?"),
            raw = o.optString("rawTAF", ""),
            issuedEpoch = o.optLongOrNull("issueTime"),
            periods = periods
        )
    }

    private fun parseClouds(arr: JSONArray?): List<Cloud> =
        (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val c = arr!!.getJSONObject(i)
            val cover = c.optString("cover", "").ifBlank { return@mapNotNull null }
            Cloud(cover, c.optIntOrNull("base"))
        }

    // ---- field quirks ----

    /** "VRB" for variable wind arrives where an int normally would. */
    private fun JSONObject.optWindDir(): Int? {
        val v = opt("wdir") ?: return null
        return (v as? Number)?.toInt()
    }

    /** visib is a number, or a string like "10+" or "1 1/2". */
    private fun JSONObject.optVisibility(): Double? {
        val v = opt("visib") ?: return null
        (v as? Number)?.let { return it.toDouble() }
        val s = v.toString().trim().removeSuffix("+")
        s.toDoubleOrNull()?.let { return it }
        // Fractional forms: "1 1/2" or "1/2"
        val parts = s.split(" ")
        return try {
            parts.sumOf { part ->
                if (part.contains("/")) {
                    val (n, d) = part.split("/")
                    n.toDouble() / d.toDouble()
                } else part.toDouble()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) (opt(key) as? Number)?.toDouble() else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) (opt(key) as? Number)?.toInt() else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) (opt(key) as? Number)?.toLong() else null

    private fun mbToInHg(mb: Double): Double = mb * 0.0295299830714

    private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3440.065 // earth radius in nautical miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
