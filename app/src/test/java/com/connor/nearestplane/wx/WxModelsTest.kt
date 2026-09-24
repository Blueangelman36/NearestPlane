package com.connor.nearestplane.wx

import org.junit.Assert.assertEquals
import org.junit.Test

class WxModelsTest {
    private fun metar(vis: Double?, vararg clouds: Cloud) = Metar(
        "KXYZ", null, null, null, null, null, "RAW", 20.0, 10.0, 270, 10, null, vis, 30.0, null,
        clouds.toList()
    )

    @Test fun `flight category follows the lower of ceiling and visibility`() {
        assertEquals("VFR", metar(10.0, Cloud("SCT", 2000)).flightCategory)
        assertEquals("MVFR", metar(10.0, Cloud("BKN", 3000)).flightCategory)
        assertEquals("MVFR", metar(5.0).flightCategory)
        assertEquals("IFR", metar(10.0, Cloud("OVC", 800)).flightCategory)
        assertEquals("IFR", metar(2.0).flightCategory)
        assertEquals("LIFR", metar(0.5).flightCategory)
        assertEquals("LIFR", metar(10.0, Cloud("OVC", 300)).flightCategory)
    }

    @Test fun `the ceiling is the lowest broken or overcast layer, not the lowest layer`() {
        assertEquals(2500, metar(10.0, Cloud("FEW", 800), Cloud("BKN", 2500), Cloud("OVC", 4000)).ceilingFt)
    }

    @Test fun `wind reads the way a pilot says it`() {
        assertEquals("calm", windText(0, 0, null))
        assertEquals("VRB@4", windText(null, 4, null))
        assertEquals("310°@12G20", windText(310, 12, 20))
    }

    @Test fun `visibility caps at ten plus`() {
        assertEquals("10+ sm", visText(10.0))
        assertEquals("2.5 sm", visText(2.5))
        assertEquals("3 sm", visText(3.0))
    }

    @Test fun `weather groups decode, and a descriptor alone does not dangle`() {
        assertEquals("light showers of rain", decodeWeather("-SHRA"))
        assertEquals("mist", decodeWeather("BR"))
        assertEquals("nearby showers", decodeWeather("VCSH"))
        assertEquals("heavy thunderstorm rain, mist", decodeWeather("+TSRA BR"))
    }
}
