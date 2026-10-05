package dev.gtos.launcher.design

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/** Only public sky fields from our installed Home; never exposes phone operations. */
class SkyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT < 34 || sentFromPackage != "com.android.launcher3") return
        val latitude = intent.getDoubleExtra("latitude", Double.NaN)
        val longitude = intent.getDoubleExtra("longitude", Double.NaN)
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        context.getSharedPreferences("living_sky", 0).edit {
            putFloat("latitude", latitude.toFloat()); putFloat("longitude", longitude.toFloat())
            putString("place", intent.getStringExtra("place")?.take(100))
            putInt("weather_code", intent.getIntExtra("code", -1)); putLong("updated", System.currentTimeMillis())
            val wind = intent.getDoubleExtra("wind", 0.0)
            putFloat("wind", if (wind.isFinite()) wind.coerceIn(0.0, 250.0).toFloat() else 0f)
        }
    }
}
