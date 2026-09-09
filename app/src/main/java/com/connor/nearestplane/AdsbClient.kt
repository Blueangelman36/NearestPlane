package com.connor.nearestplane

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Fetches live aircraft from a community ADS-B aggregator.
 *
 * If you later point this at your own Pi, dump1090's /data/aircraft.json is
 * close but has no `dst` field — you'd compute distance yourself with the
 * haversine in AviationWeatherClient.
 */
object AdsbClient {

    /**
     * Tried in order, first success wins.
     *
     * This is a list rather than a constant because airplanes.live closed its
     * public v2 API in September 2026 — it answers 403 with "please contact
     * us" to every request, key or no key — and took the widget down with it.
     * One hardcoded host is one press release away from a dead tile.
     *
     * adsb.fi leads because it carries `desc` and `ownOp`, which become the
     * full type name and the operator line. adsb.lol has neither, so on the
     * fallback the type name comes from adsbdb or the local ICAO table instead,
     * and the operator line may simply be absent. Everything else is the same
     * ADSBExchange v2 shape, except that adsb.fi names the list `aircraft`
     * where adsb.lol names it `ac`.
     */
    private val ENDPOINTS = listOf(
        "https://opendata.adsb.fi/api/v2/lat/%s/lon/%s/dist/%d",
        "https://api.adsb.lol/v2/point/%s/%s/%d"
    )

    private const val TIMEOUT_MS = 12_000

    /**
     * Returns the closest airborne aircraft within [radiusNm], or null if the
     * sky is empty. Throws on network or parse failure so the caller can
     * distinguish "nothing up there" from "couldn't reach the network".
     *
     * [maxAltitudeFt] drops high cruisers. Without it, anyone living under an
     * airway gets the same airliner at FL380 forty miles away every refresh,
     * which is technically the nearest aircraft and of no interest at all.
     */
    suspend fun nearest(
        lat: Double,
        lon: Double,
        radiusNm: Int = 50,
        includeGround: Boolean = false,
        maxAltitudeFt: Int? = null
    ): Aircraft? = withContext(Dispatchers.IO) {
        var failure: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                // A successful fetch is authoritative, including when it finds
                // nothing: an empty sky is an answer, not a reason to ask again.
                return@withContext pick(
                    fetch(endpoint, lat, lon, radiusNm),
                    includeGround,
                    maxAltitudeFt
                )
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IllegalStateException("No ADS-B endpoint configured")
    }

    private fun fetch(endpoint: String, lat: Double, lon: Double, radiusNm: Int): String {
        val url = URL(
            endpoint.format(
                Locale.US,
                "%.4f".format(Locale.US, lat),
                "%.4f".format(Locale.US, lon),
                radiusNm
            )
        )
        return (url.openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // Aggregators ask that clients identify themselves.
            setRequestProperty("User-Agent", "NearestPlaneWidget/1.0")
            try {
                if (responseCode !in 200..299) {
                    // Name the host: "HTTP 403" alone doesn't say who refused.
                    throw IllegalStateException("${url.host} returned HTTP $responseCode")
                }
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }
    }

    private fun pick(body: String, includeGround: Boolean, maxAltitudeFt: Int?): Aircraft? {
        val root = JSONObject(body)
        val list = root.optJSONArray("ac") ?: root.optJSONArray("aircraft") ?: return null

        return (0 until list.length())
            .mapNotNull { list.optJSONObject(it) }
            .map { parseAircraft(it) }
            .filter { includeGround || !it.onGround }
            // An unreported altitude is not evidence of a high one, and
            // dropping those would lose exactly the close GA traffic the
            // ceiling exists to surface.
            .filter { maxAltitudeFt == null || (it.altitudeFt ?: 0) <= maxAltitudeFt }
            .filter { it.distanceNm != null }
            .minByOrNull { it.distanceNm!! }
    }

    private fun parseAircraft(o: JSONObject): Aircraft {
        // alt_baro is an Int in flight but the string "ground" on the tarmac.
        val rawAlt = o.opt("alt_baro")
        val onGround = rawAlt is String && rawAlt.equals("ground", ignoreCase = true)

        return Aircraft(
            hex = o.optString("hex", "?"),
            callsign = o.optString("flight", "").trim().ifBlank { null },
            registration = o.optString("r", "").trim().ifBlank { null },
            type = o.optString("t", "").trim().ifBlank { null },
            description = o.optString("desc", "").trim().ifBlank { null },
            operator = o.optString("ownOp", "").trim().ifBlank { null },
            altitudeFt = (rawAlt as? Number)?.toInt(),
            onGround = onGround,
            groundSpeedKts = o.optDoubleOrNull("gs"),
            trackDeg = o.optDoubleOrNull("track"),
            distanceNm = o.optDoubleOrNull("dst"),
            bearingDeg = o.optDoubleOrNull("dir"),
            lat = o.optDoubleOrNull("lat"),
            lon = o.optDoubleOrNull("lon"),
            squawk = o.optString("squawk", "").trim().ifBlank { null }
        )
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
