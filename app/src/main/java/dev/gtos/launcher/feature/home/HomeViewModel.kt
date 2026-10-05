package dev.gtos.launcher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gtos.domain.AppCatalog
import dev.gtos.domain.HomeSession
import dev.gtos.domain.LaunchableApp
import dev.gtos.launcher.platform.apps.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HomeUiState(
    val apps: List<LaunchableApp> = emptyList(),
    val allApps: List<LaunchableApp> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val catalogRevision: Long = 0,
    val homeGeneration: Int = 0,
)

class HomeViewModel(
    private val repository: AppRepository,
    worker: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val session = MutableStateFlow(HomeSession())
    val state = combine(repository.catalog, session) { catalog, navigation ->
        HomeUiState(
            apps = AppCatalog.search(catalog.apps, navigation.query),
            allApps = catalog.apps,
            query = navigation.query,
            loading = catalog.loading,
            loadFailed = catalog.failed,
            catalogRevision = catalog.revision,
            homeGeneration = navigation.homeGeneration,
        )
    }.flowOn(worker).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), HomeUiState())

    fun search(query: String) { session.update { it.search(query) } }
    fun onHome() { session.update { it.home() } }
    fun onBack() { session.update { it.back() } }
    fun refresh() = repository.refresh()
}
