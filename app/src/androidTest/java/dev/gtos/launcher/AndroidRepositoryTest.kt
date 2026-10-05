package dev.gtos.launcher

import androidx.test.platform.app.InstrumentationRegistry
import dev.gtos.domain.AppCatalog
import dev.gtos.domain.AppId
import dev.gtos.launcher.platform.apps.AndroidAppRepository
import dev.gtos.launcher.platform.apps.AppIconCache
import dev.gtos.launcher.platform.apps.AppLaunchGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRepositoryTest {
    @Test fun realCatalogLoadsAndProvidesAnIcon() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icons = AppIconCache(context)
        val repository = AndroidAppRepository(context, icons)
        val snapshot = withTimeout(10_000) { repository.catalog.first { !it.loading } }
        assertFalse(snapshot.failed)
        assertTrue(snapshot.apps.isNotEmpty())
        assertTrue(snapshot.apps.none { it.packageName == context.packageName })
        assertEquals(AppCatalog.sorted(snapshot.apps), snapshot.apps)
        assertNotNull(icons.load(snapshot.apps.first().id, 96))
    }

    @Test fun invalidLaunchIdentityIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = AppLaunchGateway(context)
        assertFalse(gateway.launch(AppId("not-a-component", 0)))
        assertFalse(gateway.launch(AppId("dev.missing/.Missing", -1)))
    }
}
