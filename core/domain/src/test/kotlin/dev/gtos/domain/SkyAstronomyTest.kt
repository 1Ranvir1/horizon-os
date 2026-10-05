package dev.gtos.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SkyAstronomyTest {
    @Test fun moonMatchesIndependentSunCalcReferenceAcrossHemispheresAndPhases() {
        // Fixtures evaluated with the unmodified SunCalc JS bundled by WeatherGPT.
        val cases = listOf(
            arrayOf<Any>("2026-10-03T04:30:00Z", 28.6139, 77.209, 36.45945315228746, 284.54342862936323, .5479017969519797, .7347289629938905, .3166008093362289),
            arrayOf<Any>("2024-04-08T18:00:00Z", 32.8, -96.8, 64.0051044852681, 161.16670152391188, .00007693396069852199, .002791995678644188, -1.818009029259514),
            arrayOf<Any>("2024-03-25T07:00:00Z", -33.8688, 151.2093, -11.955007965549571, 99.89818327077639, .9999425884123632, .5024118709208443, 2.6792977699316682),
            arrayOf<Any>("2026-10-03T16:30:00Z", 28.6139, 77.209, -13.921960069576608, 48.06562572651055, .48870530375637505, .7535955193073343, 2.4806459766229665)
        )
        for (c in cases) {
            val m = SkyAstronomy.moon(Instant.parse(c[0] as String).toEpochMilli(), c[1] as Double, c[2] as Double)
            assertEquals(c[3] as Double, m.position.altitude, 1e-7)
            assertEquals(c[4] as Double, m.position.azimuth, 1e-7)
            assertEquals(c[5] as Double, m.fraction, 1e-7)
            assertEquals(c[6] as Double, m.phase, 1e-7)
            assertEquals(c[7] as Double, m.limbAngle, 1e-7)
        }
    }
    @Test fun moonVisibilityChangesWithPlaceAtSameInstant() {
        val now = Instant.parse("2026-10-03T16:30:00Z").toEpochMilli()
        assertTrue(SkyAstronomy.moon(now, 28.6139, 77.209).position.altitude < 0)
        assertTrue(SkyAstronomy.moon(now, 40.7128, -74.006).position.altitude > 0)
    }
}

