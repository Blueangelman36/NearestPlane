package com.connor.nearestplane

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun `versions compare by part, not as strings`() {
        assertTrue(UpdateCheck.isNewer("1.10", "1.9"))
        assertFalse(UpdateCheck.isNewer("1.9", "1.10"))
    }

    @Test fun `equal is not newer, and a missing part is zero`() {
        assertFalse(UpdateCheck.isNewer("1.8", "1.8"))
        assertTrue(UpdateCheck.isNewer("1.8.1", "1.8"))
        assertFalse(UpdateCheck.isNewer("1.8", "1.8.0"))
    }

    @Test fun `a leading v and a suffix are tolerated`() {
        assertTrue(UpdateCheck.isNewer("v2.0", "1.9"))
        assertTrue(UpdateCheck.isNewer("1.9-beta", "1.8"))
    }
}
