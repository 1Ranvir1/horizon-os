package dev.gtos.launcher.platform.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import dev.gtos.domain.AppId

class AppLaunchGateway(context: Context) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)

    fun launch(id: AppId): Boolean {
        val component = ComponentName.unflattenFromString(id.component) ?: return false
        val user = Process.myUserHandle()
        if (users.getSerialNumberForUser(user) != id.userSerial) return false
        return try {
            if (!launcher.isActivityEnabled(component, user)) return false
            launcher.startMainActivity(component, user, null, null)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
}
