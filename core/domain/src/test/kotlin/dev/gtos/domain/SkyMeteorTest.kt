package dev.gtos.domain

import org.junit.Assert.*
import org.junit.Test

class SkyMeteorTest {
    @Test fun headAndWholeTrailEnterAndLeaveOutsideBothEdges() {
        for ((w, h) in listOf(1080f to 2400f, 2400f to 1080f)) {
            for (reverse in listOf(false, true)) for (seed in listOf(0f, .5f, 1f)) {
                val start = SkyMeteor.flight(w, h, 0.0, seed, reverse)
                val end = SkyMeteor.flight(w, h, SkyMeteor.duration, seed, reverse)
                if (reverse) {
                    assertTrue(start.x > w && start.tailX > w)
                    assertTrue(end.x < 0 && end.tailX < 0)
                } else {
                    assertTrue(start.x < 0 && start.tailX < 0)
                    assertTrue(end.x > w && end.tailX > w)
                }
                val middle = SkyMeteor.flight(w, h, SkyMeteor.duration / 2, seed, reverse)
                assertEquals(w / 2, middle.x, .001f)
                assertTrue(middle.y in 0f..h)
            }
        }
    }
    @Test fun positionDependsOnElapsedTimeAcrossDroppedFrames() {
        val a = SkyMeteor.flight(1080f, 2400f, 1.0, .4f, false)
        val b = SkyMeteor.flight(1080f, 2400f, 1.5, .4f, false)
        val c = SkyMeteor.flight(1080f, 2400f, 2.0, .4f, false)
        assertEquals(b.x - a.x, c.x - b.x, .001f)
        assertEquals(b.y - a.y, c.y - b.y, .001f)
        assertNull(SkyMeteor.age(7.99, 0))
        assertNull(SkyMeteor.age(11.21, 0))
        assertNotNull(SkyMeteor.age(20.81, 1))
    }
}
