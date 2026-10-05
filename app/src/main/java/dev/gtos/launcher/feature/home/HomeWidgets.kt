package dev.gtos.launcher.feature.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class HomeWidgetActions(
    val host: AppWidgetHost,
    val manager: AppWidgetManager,
    val add: (AppWidgetProviderInfo, Int) -> Unit,
)

/** Uses Android's real widget bind/configure flow, without granting permissions silently. */
@Composable
fun rememberHomeWidgets(onAdded: (WorkspaceWidget) -> Unit, onError: (String) -> Unit): HomeWidgetActions {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val host = remember(context) { AppWidgetHost(context, 2047) }
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    var pendingId by rememberSaveable { mutableIntStateOf(-1) }
    var pendingPage by rememberSaveable { mutableIntStateOf(0) }
    val added by rememberUpdatedState(onAdded)
    val error by rememberUpdatedState(onError)
    fun discard() { if (pendingId != -1) host.deleteAppWidgetId(pendingId); pendingId = -1 }
    fun finish() {
        val id = pendingId
        if (id != -1 && manager.getAppWidgetInfo(id) != null) added(WorkspaceWidget(id, pendingPage))
        else if (id != -1) host.deleteAppWidgetId(id)
        pendingId = -1
    }
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) finish() else discard()
    }
    fun configureOrFinish() {
        val info = manager.getAppWidgetInfo(pendingId)
        if (info == null) { discard(); error("Widget permission was not granted."); return }
        if (info.configure == null) finish()
        else runCatching {
            configure.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId))
        }.onFailure { discard(); error("This widget could not open its setup screen.") }
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) configureOrFinish() else discard()
    }
    DisposableEffect(host, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) host.startListening()
            if (event == Lifecycle.Event.ON_STOP) host.stopListening()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) host.startListening()
        onDispose { lifecycle.removeObserver(observer); host.stopListening() }
    }
    return HomeWidgetActions(host, manager) { provider, page ->
        if (pendingId == -1) {
            pendingPage = page
            pendingId = host.allocateAppWidgetId()
            runCatching {
                if (manager.bindAppWidgetIdIfAllowed(pendingId, provider.provider)) configureOrFinish()
                else bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider))
            }.onFailure { discard(); error("This widget could not be added.") }
        }
    }
}

@Composable
fun HomeWidgetView(widget: WorkspaceWidget, actions: HomeWidgetActions, heightDp: Int, modifier: Modifier = Modifier) {
    val info = actions.manager.getAppWidgetInfo(widget.id) ?: return
    AndroidView(modifier = modifier, factory = { context ->
        actions.host.createView(context, widget.id, info)
    }, update = { view ->
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            view.updateAppWidgetSize(android.os.Bundle(), listOf(android.util.SizeF(360f, heightDp.toFloat())))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, 360, heightDp, 360, heightDp)
        }
    })
}
