package dev.gtos.launcher.platform.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dev.gtos.domain.AppCatalog
import dev.gtos.domain.AppId
import dev.gtos.domain.LaunchableApp
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

class AndroidAppRepository(context: Context, private val icons: AppIconCache) : AppRepository {
    private val appContext = context.applicationContext
    private val launcher = appContext.getSystemService(LauncherApps::class.java)
    private val users = appContext.getSystemService(UserManager::class.java)
    private val refreshTick = MutableStateFlow(0L)
    private val revision = AtomicLong()

    override fun refresh() { refreshTick.value += 1 }

    private val packageChanges = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
        }
        launcher.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcher.unregisterCallback(callback) }
    }

    override val catalog = flow {
        var last = CatalogSnapshot()
        emit(last)
        merge(packageChanges, refreshTick).conflate().collect {
            val nextRevision = revision.incrementAndGet()
            try {
                val entries = withContext(Dispatchers.IO) {
                    icons.invalidate()
                    val user = Process.myUserHandle()
                    val serial = users.getSerialNumberForUser(user)
                    launcher.getActivityList(null, user)
                        .filter { it.componentName.packageName != appContext.packageName }
                        .map { activity ->
                            LaunchableApp(
                                AppId(activity.componentName.flattenToString(), serial),
                                activity.label.toString().ifBlank { activity.componentName.packageName },
                                activity.componentName.packageName,
                            )
                        }
                }
                last = CatalogSnapshot(
                    apps = withContext(Dispatchers.Default) { AppCatalog.sorted(entries) },
                    loading = false,
                    revision = nextRevision,
                )
            } catch (_: SecurityException) {
                last = last.copy(loading = false, failed = true, revision = nextRevision)
            } catch (_: IllegalStateException) {
                last = last.copy(loading = false, failed = true, revision = nextRevision)
            }
            emit(last)
        }
    }
}
