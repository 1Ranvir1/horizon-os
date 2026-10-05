package dev.gtos.launcher

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeManifestTest {
    @Test fun packageExposesHomeActivityAndRealCatalog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.packageName)
        val matches = context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(matches.any { it.activityInfo.name == MainActivity::class.java.name && it.activityInfo.exported })
        val apps = context.getSystemService(LauncherApps::class.java).getActivityList(null, Process.myUserHandle())
        assertTrue(apps.any { it.componentName.packageName != context.packageName })
    }
}
