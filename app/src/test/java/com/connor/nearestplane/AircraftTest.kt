package com.connor.nearestplane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AircraftTest {
    private fun plane(
        callsign: String? = null, registration: String? = null, altitudeFt: Int? = null,
        onGround: Boolean = false, squawk: String? = null, gs: Double? = null,
        distance: Double? = null, bearing: Double? = null
    ) = Aircraft("abc123", callsign, registration, null, null, null, altitudeFt, onGround,
        gs, null, distance, bearing, null, null, squawk)

    @Test fun `label prefers callsign, then tail, then hex`() {
        assertEquals("UAL12", plane(callsign = "UAL12", registration = "N1").label)
        assertEquals("N1", plane(callsign = " ", registration = "N1").label)
        assertEquals("ABC123", plane().label)
    }

    @Test fun `the three emergency squawks are named and nothing else is`() {
        assertEquals("HIJACK", plane(squawk = "7500").emergency)
        assertEquals("NO RADIO", plane(squawk = "7600").emergency)
        assertEquals("EMERGENCY", plane(squawk = "7700").emergency)
        assertNull(plane(squawk = "1200").emergency)
    }

    @Test fun `flight levels above the transition altitude, feet below`() {
        assertEquals("FL180 · 250 kts", plane(altitudeFt = 18_000, gs = 250.0).detail)
        assertEquals("5,000 ft", plane(altitudeFt = 5_000).detail)
        assertEquals("on ground", plane(onGround = true, altitudeFt = 0).detail)
    }

    @Test fun `position reads as distance and a compass point`() {
        assertEquals("3.2 nm NNE", plane(distance = 3.24, bearing = 22.0).positionLine)
    }

    @Test fun `sixteen compass points, wrapping both ways`() {
        assertEquals("N", compass(0.0))
        assertEquals("N", compass(359.0))
        assertEquals("NNE", compass(22.5))
        assertEquals("W", compass(-90.0))
        assertEquals("", compass(null))
    }
}
