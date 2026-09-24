package com.connor.nearestplane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Choosing the nearest aircraft from a feed, in both shapes the app reads. */
class AdsbClientTest {

    private val here = 38.90 to -77.03

    private fun pick(body: String, radius: Int = 50, ground: Boolean = false, ceiling: Int? = null) =
        AdsbClient.pick(body, here.first, here.second, radius, ground, ceiling)

    @Test fun `the nearest airborne aircraft wins`() {
        val body = """{"ac":[
            {"hex":"a1","flight":"FAR1 ","alt_baro":30000,"dst":20.0},
            {"hex":"a2","flight":"NEAR1","alt_baro":3000,"dst":2.5},
            {"hex":"a3","alt_baro":"ground","dst":0.5}
        ]}"""
        assertEquals("NEAR1", pick(body)!!.callsign)
    }

    @Test fun `ground traffic is left out unless asked for`() {
        val body = """{"ac":[{"hex":"a3","alt_baro":"ground","dst":0.5}]}"""
        assertNull(pick(body))
        assertEquals("a3", pick(body, ground = true)!!.hex)
    }

    @Test fun `a ceiling drops high traffic but keeps an unreported altitude`() {
        val body = """{"ac":[
            {"hex":"hi","alt_baro":38000,"dst":1.0},
            {"hex":"unknown","dst":4.0}
        ]}"""
        assertEquals("unknown", pick(body, ceiling = 10_000)!!.hex)
    }

    @Test fun `adsb fi names the list aircraft`() {
        val body = """{"aircraft":[{"hex":"b1","alt_baro":5000,"dst":3.0,"desc":"BOEING 737-800","ownOp":"Example Air"}]}"""
        val a = pick(body)!!
        assertEquals("BOEING 737-800", a.description)
        assertEquals("Example Air", a.operator)
    }

    @Test fun `a dump1090 feed has no distance, so it is worked out from the position`() {
        // About 5.4 nm north of here, from a receiver of our own.
        val body = """{"now":0,"aircraft":[{"hex":"c1","alt_baro":4000,"lat":38.99,"lon":-77.03,"seen_pos":2.0}]}"""
        val a = pick(body)!!
        assertEquals(5.4, a.distanceNm!!, 0.1)
        assertEquals("N", compass(a.bearingDeg))
    }

    @Test fun `a stale dump1090 position does not count`() {
        val body = """{"aircraft":[{"hex":"c2","alt_baro":4000,"lat":38.91,"lon":-77.03,"seen_pos":180}]}"""
        assertNull(pick(body))
    }

    @Test fun `the radius applies to worked-out distances too`() {
        val body = """{"aircraft":[{"hex":"far","alt_baro":4000,"lat":40.5,"lon":-77.03,"seen_pos":1}]}"""
        assertNull(pick(body, radius = 25))
    }
}
