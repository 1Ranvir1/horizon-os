package dev.gtos.launcher.feature.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.gtos.domain.LaunchableApp
import dev.gtos.launcher.platform.apps.AppIconCache

@Composable
fun CachedAppIcon(app: LaunchableApp, cache: AppIconCache, revision: Long) {
    val size = with(LocalDensity.current) { 48.dp.roundToPx() }
    val bitmap by produceState<Bitmap?>(null, app.id, revision, size) {
        value = null
        value = cache.load(app.id, size)
    }
    val loaded = bitmap
    if (loaded == null) AppIconPlaceholder(app.label)
    else Image(loaded.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp))
}
