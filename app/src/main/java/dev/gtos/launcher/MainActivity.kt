package dev.gtos.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gtos.launcher.design.GTTheme
import dev.gtos.launcher.feature.home.CachedAppIcon
import dev.gtos.launcher.feature.home.HomeScreen
import dev.gtos.launcher.feature.home.HomeViewModel
import dev.gtos.launcher.platform.apps.AndroidAppRepository
import dev.gtos.launcher.platform.apps.AppLaunchGateway
import dev.gtos.launcher.platform.home.HomeRoleController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val icons get() = (application as GTApplication).icons
    private val role by lazy { HomeRoleController(this) }
    private val launchGateway by lazy { AppLaunchGateway(this) }
    private var isDefaultHome by mutableStateOf(false)
    private val model: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(AndroidAppRepository(applicationContext, icons)) as T
            }
        }
    }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultHome = role.isDefault()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.gtos.launcher.design.SkyScene.configure(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GTTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                fun showError(resource: Int) {
                    scope.launch { snackbar.showSnackbar(getString(resource), getString(R.string.dismiss)) }
                }
                BackHandler { model.onBack() }
                Box(Modifier.fillMaxSize()) {
                dev.gtos.launcher.design.SkyBackground()
                Scaffold(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    HomeScreen(
                        state = state,
                        isDefaultHome = isDefaultHome,
                        onSearch = model::search,
                        onChooseHome = {
                            try {
                                roleRequest.launch(role.requestIntent())
                            } catch (_: ActivityNotFoundException) {
                                showError(R.string.home_error)
                            } catch (_: SecurityException) {
                                showError(R.string.home_error)
                            }
                        },
                        onLaunch = { id ->
                            if (!launchGateway.launch(id)) {
                                showError(R.string.launch_error)
                                model.refresh()
                            }
                        },
                        onRetry = model::refresh,
                        modifier = Modifier.padding(padding),
                        icon = { CachedAppIcon(it, icons, state.catalogRevision) },
                    )
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = role.isDefault()
        model.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) model.onHome()
    }
}
