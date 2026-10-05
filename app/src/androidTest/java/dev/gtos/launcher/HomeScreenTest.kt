package dev.gtos.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.gtos.domain.AppCatalog
import dev.gtos.domain.AppId
import dev.gtos.domain.LaunchableApp
import dev.gtos.launcher.design.GTTheme
import dev.gtos.launcher.feature.home.AppLibrary
import dev.gtos.launcher.feature.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchAndLaunchDispatchExactComponent() {
        val camera = LaunchableApp(AppId("camera/.Main", 0), "Camera", "dev.camera")
        var launched: AppId? = null
        compose.setContent {
            var query by remember { mutableStateOf("") }
            GTTheme {
                AppLibrary(
                    HomeUiState(AppCatalog.search(listOf(camera), query), query, loading = false),
                    isDefaultHome = true, onSearch = { query = it }, onChooseHome = {},
                    onLaunch = { launched = it }, onRetry = {},
                )
            }
        }
        compose.onNodeWithText("Search apps").performTextInput("cam")
        compose.onNodeWithContentDescription("Camera").performClick()
        compose.runOnIdle { assertEquals(camera.id, launched) }
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Search apps").performTextInput("missing")
        compose.onNodeWithText("No matching apps").assertIsDisplayed()
    }

    @Test fun defaultHomeActionIsReachable() {
        var requested = false
        compose.setContent {
            GTTheme {
                AppLibrary(HomeUiState(loading = false), false, {}, { requested = true }, {}, {})
            }
        }
        compose.onNodeWithText("Set as default Home").performClick()
        compose.runOnIdle { assertEquals(true, requested) }
    }

    @Test fun editorDoesNotLoseInputWhenSearchResultsLag() {
        compose.setContent {
            GTTheme {
                // Deliberately never feed query updates back: simulates a slow worker.
                AppLibrary(HomeUiState(loading = false), true, {}, {}, {}, {})
            }
        }
        "settings".forEach { compose.onNodeWithText("Search apps").performTextInput(it.toString()) }
        compose.onNodeWithText("Search apps").assertTextContains("settings")
    }

    @Test fun homeEventClearsTheEditor() {
        var generation by mutableStateOf(0)
        compose.setContent {
            GTTheme {
                AppLibrary(HomeUiState(loading = false, homeGeneration = generation), true, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Search apps").performTextInput("settings")
        compose.runOnIdle { generation++ }
        compose.onNodeWithText("Search apps").assertTextContains("")
        compose.onNodeWithText("Clear").assertDoesNotExist()
    }
}

