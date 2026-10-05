package dev.gtos.launcher.feature.home

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class WorkspaceShortcut(val key: String, val page: Int)
data class WorkspaceWidget(val id: Int, val page: Int, val height: Int = 180)

/** User-chosen items only; the full app catalog never becomes a Home page. */
class WorkspaceStore(context: Context) {
    private val prefs = context.getSharedPreferences("home_workspace", 0)
    var pages: Int
        get() = prefs.getInt("pages", 2).coerceIn(2, 12)
        set(value) { prefs.edit { putInt("pages", value.coerceIn(2, 12)) } }
    var clock: Boolean
        get() = prefs.getBoolean("clock", true)
        set(value) { prefs.edit { putBoolean("clock", value) } }
    fun shortcuts(): List<WorkspaceShortcut> = read("shortcuts").mapNotNull {
        runCatching { WorkspaceShortcut(it.getString("key"), it.getInt("page")) }.getOrNull()
    }
    fun widgets(): List<WorkspaceWidget> = read("widgets").mapNotNull {
        runCatching { WorkspaceWidget(it.getInt("id"), it.getInt("page"), it.optInt("height", 180).coerceIn(100, 300)) }.getOrNull()
    }
    fun saveShortcuts(items: List<WorkspaceShortcut>) { write("shortcuts", items.map {
        JSONObject().put("key", it.key).put("page", it.page)
    }) }
    fun saveWidgets(items: List<WorkspaceWidget>) { write("widgets", items.map {
        JSONObject().put("id", it.id).put("page", it.page).put("height", it.height)
    }) }
    private fun read(key: String): List<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())
    private fun write(key: String, items: List<JSONObject>) {
        prefs.edit { putString(key, JSONArray(items).toString()) }
    }
}
