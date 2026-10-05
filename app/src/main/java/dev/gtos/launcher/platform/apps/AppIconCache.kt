package dev.gtos.launcher.platform.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Process
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.createBitmap
import dev.gtos.domain.AppId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Application context only. Bounded in bytes; never retains full-resolution icons. */
class AppIconCache(context: Context) {
    private val launcher = context.applicationContext.getSystemService(LauncherApps::class.java)
    private val density = context.resources.displayMetrics.densityDpi
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val decoders = Semaphore(4)
    private var generation = 0L

    @Synchronized fun invalidate() {
        generation++
        cache.evictAll()
    }

    suspend fun load(id: AppId, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val size = sizePx.coerceIn(32, 256)
        val token = synchronized(this@AppIconCache) { generation }
        val key = "${id.stableKey}:$size:$token"
        cache.get(key)?.let { return@withContext it }
        decoders.withPermit {
            cache.get(key)?.let { return@withPermit it }
            val component = ComponentName.unflattenFromString(id.component) ?: return@withPermit null
            val bitmap = try {
                val drawable = launcher.resolveActivity(Intent(Intent.ACTION_MAIN).setComponent(component), Process.myUserHandle())
                    ?.getBadgedIcon(density)
                if (drawable is AdaptiveIconDrawable) {
                    createBitmap(size, size, Bitmap.Config.ARGB_8888).also { image ->
                        val canvas = Canvas(image)
                        val clip = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()),
                            size * .24f, size * .24f, Path.Direction.CW) }
                        canvas.clipPath(clip)
                        drawable.background.setBounds(0, 0, size, size)
                        drawable.background.draw(canvas)
                        // Adaptive foreground includes overscan; retain its original visual scale.
                        val inset = (size * .25f).toInt()
                        drawable.foreground.setBounds(-inset, -inset, size + inset, size + inset)
                        drawable.foreground.draw(canvas)
                    }
                } else drawable?.toBitmap(size, size, Bitmap.Config.ARGB_8888)
            } catch (_: SecurityException) {
                null
            } catch (_: android.content.res.Resources.NotFoundException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
            synchronized(this@AppIconCache) {
                if (bitmap != null && token == generation) cache.put(key, bitmap)
            }
            bitmap
        }
    }
}
