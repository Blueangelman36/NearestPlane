package com.connor.nearestplane

import android.content.Context

/**
 * One aircraft, enriched and formatted. The widget and the detail screen both
 * build this, so a route the tile refuses to show can't reappear on the screen
 * behind it saying something different.
 */
data class PlaneReport(
    val plane: Aircraft,
    val info: AircraftInfo?,
    /** Null when the contradiction check threw the route out. */
    val route: FlightRoute?,
    val quality: RouteQuality,
    val routeLine: String,
    val timingLine: String,
    val typeName: String?,
    val operator: String,
    /** Set when enrichment failed, so the worker can record it and move on. */
    val enrichError: String?
)

object PlaneSummary {

    /**
     * Enrichment is fully isolated: adsbdb being slow or down costs you the
     * route line and the full type name, never the aircraft itself.
     */
    suspend fun build(context: Context, plane: Aircraft): PlaneReport {
        var enrichError: String? = null
        val extra = try {
            AdsbdbClient.lookup(context, plane.hex, plane.callsign)
        } catch (e: Exception) {
            enrichError = e.javaClass.simpleName
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

        return PlaneReport(
            plane = plane,
            info = extra.aircraft,
            route = route,
            quality = quality,
            routeLine = runCatching { routeLine(route, quality) }.getOrDefault(""),
            // Timing off a route we already doubt would be false precision.
            timingLine = if (quality == RouteQuality.GOOD) {
                runCatching { timingLine(plane, route) }.getOrDefault("")
            } else "",
            typeName = extra.aircraft?.fullName ?: plane.typeName,
            operator = extra.route?.airline
                ?: extra.aircraft?.owner
                ?: plane.operator.orEmpty(),
            enrichError = enrichError
        )
    }

    fun routeLine(route: FlightRoute?, quality: RouteQuality): String {
        val from = route?.origin?.display?.ifBlank { null }
        val to = route?.destination?.display?.ifBlank { null }
        val base = when {
            from != null && to != null -> "$from → $to"
            from != null -> "from $from"
            to != null -> "to $to"
            else -> return ""
        }
        return if (quality == RouteQuality.UNCERTAIN) "$base ?" else base
    }

    fun timingLine(plane: Aircraft, route: FlightRoute?): String {
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

    /** Plain English for what the route line's punctuation is hedging about. */
    fun qualityNote(quality: RouteQuality, hasRoute: Boolean): String? = when {
        quality == RouteQuality.CONTRADICTED ->
            "A route was found but discarded: this aircraft is tracking more than " +
                "90° away from the destination the table claims. No route beats a wrong one."
        !hasRoute -> null
        quality == RouteQuality.UNCERTAIN ->
            "Marked with ? because this operator recycles callsigns across unrelated " +
                "flights, or the callsign is a bare tail number. The destination is a " +
                "guess from a static table; the aircraft and its position are not."
        else -> null
    }
}
