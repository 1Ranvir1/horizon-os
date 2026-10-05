package dev.gtos.domain

import kotlin.math.*

/** SunCalc BSD-2-Clause equations; UTC instants, angles in radians internally. */
object SkyAstronomy {
    data class Body(val altitude: Double, val azimuth: Double, val parallacticAngle: Double = 0.0)
    data class Moon(val position: Body, val fraction: Double, val phase: Double, val limbAngle: Double)
    private data class Equatorial(val ra: Double, val dec: Double, val distance: Double)
    private val rad = PI / 180.0
    private val obliquity = 23.4397 * rad
    private fun days(now: Long) = now / 86400000.0 - .5 + 2440588 - 2451545
    private fun coordinates(l: Double, b: Double, distance: Double) = Equatorial(
        atan2(sin(l) * cos(obliquity) - tan(b) * sin(obliquity), cos(l)),
        asin(sin(b) * cos(obliquity) + cos(b) * sin(obliquity) * sin(l)), distance)
    private fun solar(d: Double): Equatorial {
        val m = rad * (357.5291 + .98560028 * d)
        val c = rad * (1.9148 * sin(m) + .02 * sin(2 * m) + .0003 * sin(3 * m))
        return coordinates(m + c + 102.9372 * rad + PI, 0.0, 149598000.0)
    }
    private fun lunar(d: Double): Equatorial {
        val m = rad * (134.963 + 13.064993 * d)
        return coordinates(rad * (218.316 + 13.176396 * d) + rad * 6.289 * sin(m),
            rad * 5.128 * sin(rad * (93.272 + 13.229350 * d)), 385001 - 20905 * cos(m))
    }
    private fun position(d: Double, lat: Double, lon: Double, c: Equatorial, moon: Boolean): Body {
        val phi = lat * rad
        val h = rad * (280.16 + 360.9856235 * d + lon) - c.ra
        var altitude = asin(sin(phi) * sin(c.dec) + cos(phi) * cos(c.dec) * cos(h))
        if (moon) {
            val a = altitude.coerceAtLeast(0.0)
            altitude += .0002967 / tan(a + .00312536 / (a + .08901179))
        }
        val azimuth = atan2(sin(h), cos(h) * sin(phi) - tan(c.dec) * cos(phi))
        return Body(altitude / rad, (azimuth / rad + 180) % 360,
            atan2(sin(h), tan(phi) * cos(c.dec) - sin(c.dec) * cos(h)))
    }
    fun sun(now: Long, latitude: Double, longitude: Double): Body =
        position(days(now), latitude, longitude, solar(days(now)), false)
    fun moon(now: Long, latitude: Double, longitude: Double): Moon {
        val d = days(now)
        val s = solar(d)
        val m = lunar(d)
        val separation = acos((sin(s.dec) * sin(m.dec) + cos(s.dec) * cos(m.dec) * cos(s.ra - m.ra)).coerceIn(-1.0, 1.0))
        val incidence = atan2(s.distance * sin(separation), m.distance - s.distance * cos(separation))
        val angle = atan2(cos(s.dec) * sin(s.ra - m.ra),
            sin(s.dec) * cos(m.dec) - cos(s.dec) * sin(m.dec) * cos(s.ra - m.ra))
        val body = position(d, latitude, longitude, m, true)
        return Moon(body, (1 + cos(incidence)) / 2,
            .5 + .5 * incidence * (if (angle < 0) -1 else 1) / PI, angle - body.parallacticAngle)
    }
}
