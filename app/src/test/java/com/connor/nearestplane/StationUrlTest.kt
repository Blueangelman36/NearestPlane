package com.connor.nearestplane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationUrlTest {
    @Test fun `a bare address becomes the aircraft json on it`() {
        assertEquals("https://my-pc.tail1234.ts.net/data/aircraft.json",
            normalizeStationUrl("https://my-pc.tail1234.ts.net/"))
        assertEquals("https://my-pc.tail1234.ts.net/data/aircraft.json",
            normalizeStationUrl("  my-pc.tail1234.ts.net "))
    }

    @Test fun `a full json path and a port are kept`() {
        assertEquals("https://pi.local:8443/tar1090/data/aircraft.json",
            normalizeStationUrl("https://pi.local:8443/tar1090/data/aircraft.json"))
    }

    @Test fun `plain http and nonsense are refused`() {
        assertNull(normalizeStationUrl("http://192.168.1.20:8480"))
        assertNull(normalizeStationUrl("https://"))
        assertNull(normalizeStationUrl("not a url at all"))
    }
}
