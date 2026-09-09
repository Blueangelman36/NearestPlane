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
 * All three of these speak the same response shape, so switching is a
 * one-line change. If you later point this at your own Pi, dump1090's
 * /data/aircraft.json is close but has no `dst` field — you'd compute
 * distance yourself with the haversine below.
 */
object AdsbClient {

    // airplanes.live — free, no key. Swap for either of these if it's flaky:
    //   https://opendata.adsb.fi/api/v2/lat/%s/lon/%s/dist/%d
    //   https://api.adsb.lol/v2/point/%s/%s/%d
    private const val ENDPOINT = "https://api.airplanes.live/v2/point/%s/%s/%d"

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
        val url = URL(
            ENDPOINT.format(
                Locale.US,
                "%.4f".format(Locale.US, lat),
                "%.4f".format(Locale.US, lon),
                radiusNm
            )
        )
        val body = (url.openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // Aggregators ask that clients identify themselves.
            setRequestProperty("User-Agent", "NearestPlaneWidget/1.0")
            try {
                if (responseCode !in 200..299) {
                    throw IllegalStateException("ADS-B API returned HTTP $responseCode")
                }
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }

        val list = JSONObject(body).optJSONArray("ac") ?: return@withContext null

        (0 until list.length())
            .map { parseAircraft(list.getJSONObject(it)) }
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
