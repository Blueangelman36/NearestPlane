package com.connor.nearestplane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {
    // Reagan National, and Laconia, NH: a real trip after which the tiles
    // stayed at DCA for a day because Android withheld the position from
    // background refreshes and the app fell back to the fix it had saved.
    private val dca = CachedPosition(38.85, -77.04, ageMinutes = 26 * 60L)
    private val laconia = 43.57 to -71.42

    @Test fun `a day-old saved fix says how old it is`() {
        val p = Position(dca.lat, dca.lon, PositionSource.CACHED, 26 * 60L)
        assertEquals("location 26h old", p.staleNote)
    }

    @Test fun `ages past two days read in days`() {
        val p = Position(dca.lat, dca.lon, PositionSource.CACHED, 3 * 24 * 60L)
        assertEquals("location 3d old", p.staleNote)
    }

    @Test fun `a fresh live fix earns no note`() {
        assertNull(Position(laconia.first, laconia.second, PositionSource.LIVE, 2L).staleNote)
    }

    @Test fun `a pin never goes stale`() {
        assertNull(Position(dca.lat, dca.lon, PositionSource.FIXED, null).staleNote)
    }

    @Test fun `diagnostics names the source, its age and the place`() {
        val p = Position(dca.lat, dca.lon, PositionSource.CACHED, 26 * 60L)
        assertEquals("this app's last saved location, 26h old (38.85, -77.04)", p.describe)
    }

    @Test fun `diagnostics says a pin is a pin`() {
        assertEquals(
            "pinned place (38.85, -77.04)",
            Position(dca.lat, dca.lon, PositionSource.FIXED, null).describe
        )
    }

    @Test fun `DCA to Laconia is a move`() {
        assertTrue(movedFar(dca, laconia.first, laconia.second))
    }

    @Test fun `GPS jitter at home is not a move`() {
        // About a hundred metres.
        assertFalse(movedFar(dca, dca.lat + 0.001, dca.lon))
    }

    @Test fun `with nothing saved yet any fix is news`() {
        assertTrue(movedFar(null, laconia.first, laconia.second))
    }
}
