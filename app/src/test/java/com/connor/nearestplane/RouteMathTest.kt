package com.connor.nearestplane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteMathTest {
    private val dulles = Airport("KIAD", "IAD", "Dulles", 38.9445, -77.4558)
    private val lax = Airport("KLAX", "LAX", "Los Angeles", 33.9425, -118.4081)

    @Test fun `headings differ by the short way round`() {
        assertEquals(20.0, RouteMath.angularDifference(350.0, 10.0), 1e-9)
        assertEquals(180.0, RouteMath.angularDifference(0.0, 180.0), 1e-9)
    }

    @Test fun `an aircraft flying away from its claimed destination contradicts it`() {
        // Over Kansas, heading east, "destined for" Los Angeles.
        val q = RouteMath.assessRoute("UAL1", 38.5, -98.0, 90.0, 450.0, 35_000, lax)
        assertEquals(RouteQuality.CONTRADICTED, q)
    }

    @Test fun `pointing at it, at cruise, is good`() {
        val q = RouteMath.assessRoute("UAL1", 38.5, -98.0, 260.0, 450.0, 35_000, lax)
        assertEquals(RouteQuality.GOOD, q)
    }

    @Test fun `climbing out says nothing about the destination`() {
        val q = RouteMath.assessRoute("UAL1", 38.5, -98.0, 90.0, 180.0, 4_000, lax)
        assertEquals(RouteQuality.GOOD, q)
    }

    @Test fun `operators that reuse callsigns are always uncertain`() {
        assertEquals(RouteQuality.UNCERTAIN,
            RouteMath.assessRoute("EJA123", null, null, null, null, null, dulles))
        assertEquals(RouteQuality.UNCERTAIN,
            RouteMath.assessRoute("N123AB", null, null, null, null, null, dulles))
    }

    @Test fun `timing needs a real ground speed`() {
        assertNull(RouteMath.minutesRemaining(38.0, -77.0, dulles, 40.0))
        assertEquals("2h05m", RouteMath.formatDuration(125))
        assertEquals("45m", RouteMath.formatDuration(45))
    }
}
