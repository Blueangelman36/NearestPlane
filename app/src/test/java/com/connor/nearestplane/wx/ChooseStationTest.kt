package com.connor.nearestplane.wx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChooseStationTest {
    private fun at(id: String, nm: Double?) = Metar(
        id, null, null, null, nm, null, "RAW", null, null, null, null, null, null, null, null, emptyList()
    )

    private val reports = listOf(at("KGAI", 4.0), at("KDCA", 11.0), at("KCGS", 7.5), at("KIAD", 19.0))

    @Test fun `without the preference, the nearest station wins`() {
        assertEquals("KGAI", AviationWeatherClient.chooseStation(reports, null)?.stationId)
    }

    @Test fun `with it, the nearest station that issues a TAF wins`() {
        val forecasting = setOf("KDCA", "KIAD", "KBWI")
        assertEquals("KDCA", AviationWeatherClient.chooseStation(reports, forecasting)?.stationId)
    }

    @Test fun `when nothing in range issues a TAF, the nearest station still answers`() {
        assertEquals("KGAI", AviationWeatherClient.chooseStation(reports, emptySet())?.stationId)
    }

    @Test fun `a station with no known distance sorts last, and an empty sky is null`() {
        val odd = listOf(at("KXXX", null), at("KYYY", 30.0))
        assertEquals("KYYY", AviationWeatherClient.chooseStation(odd, null)?.stationId)
        assertNull(AviationWeatherClient.chooseStation(emptyList(), setOf("KDCA")))
    }
}
