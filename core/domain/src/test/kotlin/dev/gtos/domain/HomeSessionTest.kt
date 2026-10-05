package dev.gtos.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSessionTest {
    @Test fun homeResetsTransientStateEveryTime() {
        val home = HomeSession().search("camera").home()
        assertEquals("", home.query)
        assertEquals(1, home.homeGeneration)
        assertEquals(2, home.home().homeGeneration)
    }
    @Test fun backClearsQueryAndSignalsEditorReset() {
        assertEquals(HomeSession(homeGeneration = 1), HomeSession().search("camera").back())
    }
    @Test fun inputIsBounded() {
        assertEquals(256, HomeSession().search("a".repeat(1024)).query.length)
    }
}
