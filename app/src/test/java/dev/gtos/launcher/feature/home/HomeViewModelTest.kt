package dev.gtos.launcher.feature.home

import dev.gtos.domain.AppId
import dev.gtos.domain.LaunchableApp
import dev.gtos.launcher.platform.apps.AppRepository
import dev.gtos.launcher.platform.apps.CatalogSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val main = UnconfinedTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(main) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun catalogChangesSearchAndHomeEventsReachUi() = runTest {
        val app = LaunchableApp(AppId("dev.camera/.Main", 0), "Camera", "dev.camera")
        val repository = FakeRepository()
        val model = HomeViewModel(repository, main)
        backgroundScope.launch(main) { model.state.collect {} }
        repository.catalog.value = CatalogSnapshot(listOf(app), loading = false)
        withTimeout(5_000) { model.state.first { it.apps == listOf(app) } }
        model.search("missing")
        withTimeout(5_000) { model.state.first { it.query == "missing" && it.apps.isEmpty() } }
        model.onHome()
        val home = withTimeout(5_000) { model.state.first { it.homeGeneration == 1 } }
        assertEquals("", home.query)
        assertEquals(listOf(app), home.apps)
        model.refresh()
        assertEquals(1, repository.refreshes)
    }

    @Test fun packageRemovalUpdatesAnActiveSearch() = runTest {
        val app = LaunchableApp(AppId("dev.camera/.Main", 0), "Camera", "dev.camera")
        val repository = FakeRepository()
        val model = HomeViewModel(repository, main)
        backgroundScope.launch(main) { model.state.collect {} }
        repository.catalog.value = CatalogSnapshot(listOf(app), loading = false, revision = 1)
        model.search("cam")
        withTimeout(5_000) { model.state.first { it.apps.size == 1 && it.query == "cam" } }
        repository.catalog.value = CatalogSnapshot(loading = false, revision = 2)
        val removed = withTimeout(5_000) { model.state.first { it.catalogRevision == 2L } }
        assertEquals(emptyList<LaunchableApp>(), removed.apps)
        assertEquals("cam", removed.query)
    }

    @Test fun loadFailureRemainsVisibleAndRetryReachesRepository() = runTest {
        val repository = FakeRepository()
        val model = HomeViewModel(repository, main)
        backgroundScope.launch(main) { model.state.collect {} }
        repository.catalog.value = CatalogSnapshot(loading = false, failed = true)
        withTimeout(5_000) { model.state.first { it.loadFailed && !it.loading } }
        model.refresh()
        assertEquals(1, repository.refreshes)
        repository.catalog.value = CatalogSnapshot(loading = false, failed = false, revision = 1)
        withTimeout(5_000) { model.state.first { !it.loadFailed && it.catalogRevision == 1L } }
    }

    private class FakeRepository : AppRepository {
        override val catalog = MutableStateFlow(CatalogSnapshot())
        var refreshes = 0
        override fun refresh() { refreshes++ }
    }
}
