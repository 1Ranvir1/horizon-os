package dev.gtos.launcher.feature.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import dev.gtos.domain.AppId
import dev.gtos.domain.LaunchableApp
import dev.gtos.launcher.design.SkyBackground
import dev.gtos.launcher.design.SkyWallpaperService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Home workspace and its weather page are independent of the all-apps drawer. */
@Composable
fun HomeScreen(
    state: HomeUiState, isDefaultHome: Boolean, onSearch: (String) -> Unit,
    onChooseHome: () -> Unit, onLaunch: (AppId) -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (LaunchableApp) -> Unit = { AppIconPlaceholder(it.label) },
) {
    val context = LocalContext.current
    val store = remember { WorkspaceStore(context) }
    val scope = rememberCoroutineScope()
    var pages by remember { mutableIntStateOf(store.pages) }
    var shortcuts by remember { mutableStateOf(store.shortcuts()) }
    var widgets by remember { mutableStateOf(store.widgets()) }
    var showClock by remember { mutableStateOf(store.clock) }
    var drawer by rememberSaveable { mutableStateOf(false) }
    var editPage by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf<LaunchableApp?>(null) }
    var editing by remember { mutableStateOf<WorkspaceShortcut?>(null) }
    var editingWidget by remember { mutableStateOf<WorkspaceWidget?>(null) }
    var widgetPicker by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val widgetActions = rememberHomeWidgets(onAdded = {
        widgets = widgets + it; store.saveWidgets(widgets)
    }, onError = { error = it })
    val blockSize = pages + 1
    val pager = rememberPagerState(initialPage = pages - 1) { blockSize * 1000 }
    val position by remember { derivedStateOf { pager.currentPage % (pages + 1) } }
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(30_000); value = LocalDateTime.now() }
    }
    fun closeDrawer() { drawer = false; onSearch("") }
    fun home() { scope.launch { pager.scrollToPage(pages - 1) }; closeDrawer() }
    fun next() { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(blockSize * 1000 - 1)) } }
    fun addToPage(app: LaunchableApp, page: Int) {
        if (shortcuts.none { it.key == app.id.stableKey && it.page == page }) {
            shortcuts = shortcuts + WorkspaceShortcut(app.id.stableKey, page); store.saveShortcuts(shortcuts)
        }
        adding = null; closeDrawer()
        scope.launch { pager.animateScrollToPage(pages - 1 - page) }
    }
    LaunchedEffect(state.homeGeneration, pages) { home(); editPage = null }
    BackHandler(enabled = drawer || editPage != null || widgetPicker != null || adding != null || editing != null || editingWidget != null || position != pages - 1) {
        when {
            adding != null -> adding = null
            editing != null -> editing = null
            editingWidget != null -> editingWidget = null
            widgetPicker != null -> widgetPicker = null
            editPage != null -> editPage = null
            else -> home()
        }
    }
    Box(modifier.fillMaxSize()) {
        SkyBackground(offset = { (pager.currentPageOffsetFraction + 1f) / 2f })
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = position != pages && position != 0) { index ->
            val slot = index % blockSize
            if (slot == pages) WeatherPage(onExitToApps = ::next)
            else {
                val page = pages - 1 - slot
                var horizontal by remember { mutableFloatStateOf(0f) }
                var vertical by remember { mutableFloatStateOf(0f) }
                Box(Modifier.fillMaxSize()
                    .pointerInput(index) {
                        detectVerticalDragGestures(onDragStart = { vertical = 0f },
                            onVerticalDrag = { change, amount -> change.consume(); vertical += amount },
                            onDragEnd = { if (vertical < -60.dp.toPx()) { onSearch(""); drawer = true } })
                    }
                    .pointerInput(index, slot) {
                        if (slot == 0) detectHorizontalDragGestures(onDragStart = { horizontal = 0f },
                            onHorizontalDrag = { change, amount -> change.consume(); horizontal += amount },
                            onDragEnd = { if (horizontal < -60.dp.toPx()) next() })
                    }
                    .pointerInput(page) { detectTapGestures(onLongPress = { editPage = page }) }) {
                    LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (page == 0 && showClock) item(key = "clock", span = { GridItemSpan(4) }) {
                            Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 26.dp)
                                .combinedClickable(onClick = {}, onLongClick = { editPage = page }),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(now.format(DateTimeFormatter.ofPattern("h:mm")), fontSize = 84.sp,
                                    fontWeight = FontWeight.ExtraLight, letterSpacing = (-3).sp)
                                Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), fontSize = 15.sp)
                            }
                        }
                        items(widgets.filter { it.page == page }, key = { "widget-${it.id}" }, span = { GridItemSpan(4) }) { widget ->
                            Column {
                                HomeWidgetView(widget, widgetActions, widget.height, Modifier.fillMaxWidth().height(widget.height.dp))
                                TextButton(onClick = { editingWidget = widget }) { Text("Edit widget") }
                            }
                        }
                        items(shortcuts.filter { it.page == page }, key = { it.key }) { shortcut ->
                            val app = state.allApps.find { it.id.stableKey == shortcut.key }
                            if (app != null) Column(Modifier.fillMaxWidth().combinedClickable(
                                onClick = { onLaunch(app.id) }, onLongClickLabel = "Edit shortcut",
                                onLongClick = { editing = shortcut }).padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                icon(app)
                                Text(app.label, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    if (page > 0 && shortcuts.none { it.page == page } && widgets.none { it.page == page }) {
                        TextButton(onClick = { editPage = page }, modifier = Modifier.align(Alignment.Center)) { Text("Customize this Home page") }
                    }
                    if (!isDefaultHome) TextButton(onClick = onChooseHome, modifier = Modifier.align(Alignment.BottomCenter)) { Text("Set as default Home") }
                }
            }
        }
    }
    if (drawer) Dialog(onDismissRequest = ::closeDrawer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            SkyBackground()
            AppLibrary(state, isDefaultHome, onSearch, onChooseHome, onLaunch, onRetry,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), icon = icon,
                onAdd = { adding = it }, onClose = ::home)
        }
    }
    editPage?.let { page ->
        AlertDialog(onDismissRequest = { editPage = null }, title = { Text(if (page == 0) "Edit Home" else "Edit Home page ${page + 1}") },
            text = {
                Column {
                    TextButton(onClick = { editPage = null; drawer = true }) { Text("Add app shortcuts") }
                    TextButton(onClick = { editPage = null; widgetPicker = page }) { Text("Add widget") }
                    if (page == 0) TextButton(onClick = { showClock = !showClock; store.clock = showClock }) { Text(if (showClock) "Hide clock" else "Show clock") }
                    if (pages < 12) TextButton(onClick = { pages++; store.pages = pages; editPage = null }) { Text("Add Home page") }
                    TextButton(onClick = {
                        editPage = null
                        context.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, SkyWallpaperService::class.java)))
                    }) { Text("Living sky wallpaper") }
                    for (widget in widgets.filter { it.page == page }) TextButton(onClick = {
                        widgetActions.host.deleteAppWidgetId(widget.id)
                        widgets = widgets.filterNot { it.id == widget.id }; store.saveWidgets(widgets)
                    }) { Text("Remove ${widgetActions.manager.getAppWidgetInfo(widget.id)?.loadLabel(context.packageManager) ?: "widget"}") }
                    if (page > 0) TextButton(onClick = {
                        widgets.filter { it.page == page }.forEach { widgetActions.host.deleteAppWidgetId(it.id) }
                        widgets = widgets.filterNot { it.page == page }; store.saveWidgets(widgets)
                        shortcuts = shortcuts.filterNot { it.page == page }; store.saveShortcuts(shortcuts); editPage = null
                    }) { Text("Clear this Home page") }
                }
            }, confirmButton = { TextButton(onClick = { editPage = null }) { Text("Done") } })
    }
    adding?.let { app ->
        AlertDialog(onDismissRequest = { adding = null }, title = { Text("Add ${app.label}") }, text = {
            Column { for (page in 0 until pages) TextButton(onClick = { addToPage(app, page) }) {
                Text(if (page == 0) "Home" else "Home page ${page + 1}")
            } }
        }, confirmButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } })
    }
    editing?.let { shortcut ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Edit shortcut") }, text = {
            Column {
                TextButton(onClick = { shortcuts = shortcuts - shortcut; store.saveShortcuts(shortcuts); editing = null }) { Text("Remove from Home") }
                TextButton(onClick = {
                    val current = shortcuts.indexOf(shortcut)
                    val previous = (current - 1 downTo 0).firstOrNull { shortcuts[it].page == shortcut.page }
                    if (previous != null) { val updated = shortcuts.toMutableList(); updated.removeAt(current); updated.add(previous, shortcut); shortcuts = updated; store.saveShortcuts(shortcuts) }
                    editing = null
                }) { Text("Move earlier") }
                TextButton(onClick = {
                    val current = shortcuts.indexOf(shortcut)
                    val after = (current + 1 until shortcuts.size).firstOrNull { shortcuts[it].page == shortcut.page }
                    if (after != null) { val updated = shortcuts.toMutableList(); updated.removeAt(current); updated.add(after, shortcut); shortcuts = updated; store.saveShortcuts(shortcuts) }
                    editing = null
                }) { Text("Move later") }
                for (page in 0 until pages) if (page != shortcut.page) TextButton(onClick = {
                    shortcuts = shortcuts - shortcut
                    if (shortcuts.none { it.key == shortcut.key && it.page == page }) shortcuts = shortcuts + shortcut.copy(page = page)
                    store.saveShortcuts(shortcuts); editing = null
                }) { Text(if (page == 0) "Move to Home" else "Move to Home page ${page + 1}") }
            }
        }, confirmButton = { TextButton(onClick = { editing = null }) { Text("Done") } })
    }
    editingWidget?.let { widget ->
        fun change(updated: WorkspaceWidget) {
            widgets = widgets.map { if (it.id == widget.id) updated else it }; store.saveWidgets(widgets); editingWidget = updated
        }
        AlertDialog(onDismissRequest = { editingWidget = null }, title = { Text("Edit widget") }, text = {
            Column {
                Row {
                    TextButton(onClick = { change(widget.copy(height = (widget.height - 40).coerceAtLeast(100))) }) { Text("Smaller") }
                    TextButton(onClick = { change(widget.copy(height = (widget.height + 40).coerceAtMost(300))) }) { Text("Larger") }
                }
                for (page in 0 until pages) if (page != widget.page) TextButton(onClick = { change(widget.copy(page = page)); editingWidget = null }) {
                    Text(if (page == 0) "Move to Home" else "Move to Home page ${page + 1}")
                }
                TextButton(onClick = {
                    widgetActions.host.deleteAppWidgetId(widget.id); widgets = widgets.filterNot { it.id == widget.id }
                    store.saveWidgets(widgets); editingWidget = null
                }) { Text("Remove widget") }
            }
        }, confirmButton = { TextButton(onClick = { editingWidget = null }) { Text("Done") } })
    }
    widgetPicker?.let { page ->
        val providers = remember(page) { widgetActions.manager.installedProviders.sortedBy { it.loadLabel(context.packageManager) } }
        AlertDialog(onDismissRequest = { widgetPicker = null }, title = { Text("Widgets") }, text = {
            LazyColumn { if (providers.isEmpty()) item { Text("No widgets are installed. Apps with widgets will appear here.") }
                listItems(providers, key = { it.provider.flattenToString() }) { provider ->
                    TextButton(onClick = { widgetPicker = null; widgetActions.add(provider, page) }) { Text(provider.loadLabel(context.packageManager)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { widgetPicker = null }) { Text("Cancel") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}
