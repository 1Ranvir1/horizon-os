package dev.gtos.launcher

import android.app.Application
import dev.gtos.launcher.platform.apps.AppIconCache

class GTApplication : Application() {
    // Survives Activity recreation so the repository and UI always share invalidation.
    val icons by lazy { AppIconCache(this) }
}
