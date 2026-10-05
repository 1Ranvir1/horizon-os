package dev.gtos.launcher.design

import android.graphics.*
import android.content.Context
import androidx.core.graphics.withTranslation

import kotlin.math.*
import dev.gtos.domain.SkyAstronomy

/** Separate drifting cloud volumes with feathered turbulent edges, leaving open sky. */
internal object AtmosphereRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var moonBitmap: Bitmap? = null
    private var moonKey = ""
    private var cloudTexture: Bitmap? = null
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var lastTint = -1
    private val lanes = floatArrayOf(.12f, .255f, .19f, .34f, .075f)
    private val seeds = floatArrayOf(.12f, .79f, .44f, .97f, .63f)
    private val sizes = floatArrayOf(.83f, .69f, .98f, .76f, 1.1f)
    fun configure(context: Context) {
        if (cloudTexture == null) cloudTexture = context.assets.open("sky/cloud-natural-v1.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
        }
    }
    fun moon(canvas: Canvas, x: Float, y: Float, radius: Float, moon: SkyAstronomy.Moon, visibility: Float) {
        val key = "${(moon.fraction * 10000).toInt()}:${(moon.limbAngle * 1000).toInt()}"
        if (key != moonKey) {
            moonKey = key
            val size = 128
            val pixels = IntArray(size * size)
            val zLight = 2 * moon.fraction - 1
            val lateral = sqrt((1 - zLight * zLight).coerceAtLeast(0.0))
            val lx = sin(moon.limbAngle) * lateral
            val ly = -cos(moon.limbAngle) * lateral
            for (j in 0 until size) for (i in 0 until size) {
                val nx = (i + .5 - size / 2) / (size / 2)
                val ny = (j + .5 - size / 2) / (size / 2)
                val r2 = nx * nx + ny * ny
                if (r2 >= 1) continue
                val nz = sqrt(1 - r2)
                val light = (nx * lx + ny * ly + nz * zLight).coerceAtLeast(0.0)
                // Stable mottled lunar surface; the terminator follows sphere illumination.
                val texture = .94 + .025 * sin(nx * 4 + ny * 7) + .035 * cos(nx * 10 - ny * 3)
                val brightness = (.025 + .975 * sqrt(light)) * texture
                val alpha = ((1 - sqrt(r2)) * size).coerceIn(0.0, 1.0) * 255 * if (light > 0) 1.0 else .16
                val value = (brightness * 240).toInt().coerceIn(0, 255)
                pixels[j * size + i] = Color.argb(alpha.toInt(), value, value, (value * 1.025).toInt().coerceAtMost(255))
            }
            moonBitmap?.recycle()
            moonBitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }
        paint.shader = null
        paint.alpha = (visibility * 255).toInt().coerceIn(0, 255)
        moonBitmap?.let { canvas.drawBitmap(it, null, RectF(x - radius, y - radius, x + radius, y + radius), paint) }
    }

    fun clouds(canvas: Canvas, w: Float, h: Float, t: Float, cover: Float, daylight: Float, wind: Float, offset: Float, storm: Float, lighting: Int) {
        val bitmap = cloudTexture ?: return
        if (cover < .01f) return
        val tint = lighting xor (storm * 100).toInt()
        if (tint != lastTint) {
        lastTint = tint
        val shade = 1f - storm * .24f
        cloudPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            Color.red(lighting) / 255f * shade, 0f, 0f, 0f, 0f,
            0f, Color.green(lighting) / 255f * shade, 0f, 0f, 0f,
            0f, 0f, Color.blue(lighting) / 255f * shade, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        }
        // Actual alpha edges and sparse lanes preserve open sky without noise masks.
        for (i in lanes.indices) {
            val presence = ((cover - i * .18f) / .20f).coerceIn(0f, 1f)
            if (presence == 0f) continue
            val width = w * sizes[i]
            val height = width * bitmap.height / bitmap.width
            val travel = w + width
            val speed = (1.4f + wind * .045f) * (1f + i * .13f)
            val cx = (seeds[i] * travel + t * speed) % travel - width / 2 + (offset - .5f) * w * .035f
            val cy = h * lanes[i] + sin(t * .018f + i) * h * .003f
            cloudPaint.alpha = (presence * (.40f + daylight * .38f) * 255).toInt()
            canvas.withTranslation(cx, cy) {
            if (i % 2 == 1) canvas.scale(-1f, 1f)
            canvas.drawBitmap(bitmap, null, RectF(-width / 2, -height / 2, width / 2, height / 2), cloudPaint)
            }
        }
    }
}

