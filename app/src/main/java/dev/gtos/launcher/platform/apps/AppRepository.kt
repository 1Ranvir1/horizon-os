package dev.gtos.launcher.platform.apps

import dev.gtos.domain.LaunchableApp
import kotlinx.coroutines.flow.Flow

data class CatalogSnapshot(
    val apps: List<LaunchableApp> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val revision: Long = 0,
)

interface AppRepository {
    val catalog: Flow<CatalogSnapshot>
    fun refresh()
}
