package dev.gtos.launcher.design

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder

/** Read-only local bridge for SystemUI. Never exports photos, weather queries or files. */
class SkyStateProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.gtos.sky-state"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = requireNotNull(context)
        val caller = Binder.getCallingUid()
        val systemUi = ctx.packageManager.getApplicationInfo("com.android.systemui", 0).uid
        if (caller != systemUi && caller != ctx.applicationInfo.uid) throw SecurityException("SystemUI only")
        require(uri.path == "/state" && selection == null && selectionArgs == null)
        val result = MatrixCursor(arrayOf("latitude", "longitude", "animation_start_nanos"))
        val prefs = ctx.getSharedPreferences("living_sky", 0)
        if (prefs.contains("latitude") && prefs.contains("longitude")) {
            result.addRow(arrayOf<Any>(prefs.getFloat("latitude", 0f), prefs.getFloat("longitude", 0f),
                SkyScene.animationStartNanos))
        }
        return result
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
