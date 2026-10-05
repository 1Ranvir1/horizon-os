package dev.gtos.launcher.platform.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

class HomeRoleController(context: Context) {
    private val roles = context.getSystemService(RoleManager::class.java)
    fun isDefault(): Boolean = roles.isRoleAvailable(RoleManager.ROLE_HOME) &&
        roles.isRoleHeld(RoleManager.ROLE_HOME)

    fun requestIntent(): Intent = if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
        roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
