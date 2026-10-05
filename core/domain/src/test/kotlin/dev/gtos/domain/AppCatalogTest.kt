package dev.gtos.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {
    private val camera = LaunchableApp(AppId("camera/.Main", 0), "Camera", "dev.camera")
    private val cafe = LaunchableApp(AppId("cafe/.Main", 0), "Café", "dev.cafe")
    private val settings = LaunchableApp(AppId("settings/.Main", 0), "Settings", "android.settings")
    private val apps = listOf(camera, cafe, settings)

    @Test fun blankQueryReturnsAll() { assertEquals(apps, AppCatalog.search(apps, "  ")) }
    @Test fun searchesNamesIgnoringCaseAndAccents() {
        assertEquals(listOf(cafe), AppCatalog.search(apps, "  CAFE  "))
    }
    @Test fun searchesPackageAndMultipleTerms() {
        assertEquals(listOf(settings), AppCatalog.search(apps, "android SETTINGS"))
        assertTrue(AppCatalog.search(apps, "camera settings").isEmpty())
    }
    @Test fun sortingIsStableAndPreservesDistinctActivitiesAndUsers() {
        val secondActivity = camera.copy(id = AppId("camera/.Second", 0))
        val secondUser = camera.copy(id = AppId("camera/.Main", 10))
        val result = AppCatalog.sorted(apps + camera + secondActivity + secondUser, Locale.US)
        assertEquals(5, result.size)
        assertEquals("Café", result.first().label)
        assertEquals("Settings", result.last().label)
        assertEquals(result, AppCatalog.sorted(result.reversed(), Locale.US))
    }
    @Test fun matchingDoesNotDependOnTurkishDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf(settings), AppCatalog.search(apps, "SETTINGS"))
        } finally { Locale.setDefault(original) }
    }
}
