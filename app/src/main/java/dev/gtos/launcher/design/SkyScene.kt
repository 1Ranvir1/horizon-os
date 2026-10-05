package dev.gtos.launcher.design

import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import android.content.Context
import android.content.SharedPreferences
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.service.wallpaper.WallpaperService
import android.app.WallpaperColors
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.Choreographer
import androidx.core.content.edit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.sin
import dev.gtos.domain.SkyAstronomy
import dev.gtos.domain.SkyMeteor

/** Local time scene, not fabricated weather. Forecast integration is separate. */
object SkyScene {
    private var preferences: SharedPreferences? = null
    fun configure(context: Context) { preferences = context.getSharedPreferences("living_sky", 0); AtmosphereRenderer.configure(context) }
    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) { preferences?.registerOnSharedPreferenceChangeListener(listener) }
    fun unobserve(listener: SharedPreferences.OnSharedPreferenceChangeListener) { preferences?.unregisterOnSharedPreferenceChangeListener(listener) }
    private fun weatherCode(): Int {
        val prefs = preferences ?: return -1
        if (System.currentTimeMillis() - prefs.getLong("updated", 0) > 3 * 60 * 60 * 1000) return -1
        return prefs.getInt("weather_code", -1)
    }
    private fun wind(): Float = if (weatherCode() >= 0) preferences?.getFloat("wind", 0f) ?: 0f else 0f
    // A fixed random sky stays in place across unlocks; each star has its own rhythm.
    private val stars = java.util.Random(6739L).let { random ->
        Array(70) { floatArrayOf(random.nextFloat(), random.nextFloat() * .8f,
            .6f + random.nextFloat(), random.nextFloat() * 6.283f, .35f + random.nextFloat() * .5f) }
    }
    private val animationStart = android.os.SystemClock.elapsedRealtimeNanos()
    internal val animationStartNanos: Long get() = animationStart
    private fun animationSeconds() = (android.os.SystemClock.elapsedRealtimeNanos() - animationStart) / 1_000_000_000.0
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val decorationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val decorationPath = Path()
    private var gradientKey = ""
    private var skyGradient: LinearGradient? = null
    private var blendedCover = -1f
    private var lastCloudFrame = 0.0
    fun diagnostics(): String {
        val prefs = preferences ?: return "Sky location unavailable"
        if (!prefs.contains("latitude")) return "Sky location unavailable"
        val moon = SkyAstronomy.moon(System.currentTimeMillis(), prefs.getFloat("latitude", 0f).toDouble(), prefs.getFloat("longitude", 0f).toDouble())
        return "phase=${phase()} sunAltitude=${sun(System.currentTimeMillis())?.first} weather=${weatherCode()} moonAltitude=${moon.position.altitude} moonIllumination=${moon.fraction} moonAboveHorizon=${moon.position.altitude > 0}"
    }
    fun frameDelay(): Long {
        val t = animationSeconds()
        val code = weatherCode()
        val day = sun(System.currentTimeMillis())?.let { ((it.first + 6) / 12).coerceIn(0.0, 1.0).toFloat() }
            ?: if (LocalTime.now().hour in 6..19) 1f else 0f
        val meteor = day < .5f && code in -1..2 && (SkyMeteor.age(t, 0) != null || SkyMeteor.age(t, 1) != null)
        val plane = day > .5f && code in 0..1 && t > 20 && (t - 20) % 50 < 10
        // Fast flights use every display frame; slow atmosphere keeps a modest frame rate.
        return if (meteor || plane) 0L else if (code in 1..99) 33L else 50L
    }
    // Sun position equations ported from WeatherGPT's BSD-2-Clause SunCalc.
    // No location request: these are the place coordinates the user chose in WeatherGPT.
    private fun sun(now: Long, moon: Boolean = false): Pair<Double, Double>? {
        val prefs = preferences ?: return null
        if (!prefs.contains("latitude")) return null
        val lat = prefs.getFloat("latitude", 0f).toDouble()
        val lon = prefs.getFloat("longitude", 0f).toDouble()
        val body = if (moon) SkyAstronomy.moon(now, lat, lon).position else SkyAstronomy.sun(now, lat, lon)
        return Pair(body.altitude, body.azimuth)
    }
    // Keep these exact stops and altitude bands in sync with WeatherGPT experience.css/js.
    private fun phase(time: LocalTime = LocalTime.now()): String {
        val now = System.currentTimeMillis()
        val position = sun(now)
        return if (position != null) {
            if (position.first < -8) "night" else if (position.first < 8) {
                if ((sun(now + 60000)?.first ?: position.first) > position.first) "sunrise" else "sunset"
            } else "day"
        } else when (time.hour) { in 6..8 -> "sunrise"; in 9..16 -> "day"; in 17..19 -> "sunset"; else -> "night" }
    }

    fun isDark(): Boolean = phase() == "night"

    fun colors(time: LocalTime = LocalTime.now()): IntArray = when (phase(time)) {
        "night" -> intArrayOf(0xFF071127.toInt(), 0xFF182E4C.toInt(), 0xFF142237.toInt())
        "sunrise" -> intArrayOf(0xFFCD8664.toInt(), 0xFFF2B879.toInt(), 0xFFF0D5BD.toInt())
        "sunset" -> intArrayOf(0xFFB485A2.toInt(), 0xFFEEA584.toInt(), 0xFFE9CBBD.toInt())
        else -> intArrayOf(0xFF89B6DC.toInt(), 0xFFC2DDEF.toInt(), 0xFFE6EDF2.toInt())
    }

    fun draw(canvas: Canvas, width: Float, height: Float, offset: Float = .5f, palette: IntArray? = null, lockDepth: Boolean = false) {
        if (width <= 0 || height <= 0) return
        val time = LocalTime.now()
        val seconds = animationSeconds()
        val colors = palette ?: colors(time)
        val paint = skyPaint
        paint.reset(); paint.isAntiAlias = true
        val skyPhase = phase(time)
        val angle = (if (skyPhase == "night" || skyPhase == "sunset") 155.0 else 165.0) * Math.PI / 180
        val dx = sin(angle).toFloat()
        val dy = -kotlin.math.cos(angle).toFloat()
        val span = width * dx + height * dy
        val mid = when (skyPhase) { "night" -> .58f; "sunrise" -> .50f; "sunset" -> .52f; else -> .53f }
        val key = "$width:$height:$skyPhase:${colors.contentHashCode()}"
        if (key != gradientKey) {
            gradientKey = key
            skyGradient = LinearGradient((width - dx * span) / 2, (height - dy * span) / 2,
                (width + dx * span) / 2, (height + dy * span) / 2, colors,
                floatArrayOf(0f, mid, 1f), Shader.TileMode.CLAMP)
        }
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, width, height, paint)
        val position = sun(System.currentTimeMillis())
        val code = weatherCode()
        val targetCover = when { code == 1 -> .28f; code == 2 -> .55f; code == 3 -> .93f; code in 45..99 -> .98f; else -> 0f }
        if (blendedCover < 0) blendedCover = targetCover
        val elapsed = (seconds - lastCloudFrame).coerceIn(0.0, .2)
        blendedCover += (targetCover - blendedCover) * (1 - kotlin.math.exp(-elapsed / 3)).toFloat()
        lastCloudFrame = seconds
        val cover = blendedCover
        val daylight = position?.let { ((it.first + 6) / 12).coerceIn(0.0, 1.0).toFloat() } ?: if (time.hour in 6..19) 1f else 0f
        for (isMoon in listOf(false, true)) {
            val body = if (isMoon) sun(System.currentTimeMillis(), moon = true) else position
            // No chosen location means no invented celestial position. A daytime moon is possible.
            if (body == null || body.first <= 0) continue
            val horizonFade = (body.first / 3).coerceIn(0.0, 1.0).toFloat()
            val visibility = horizonFade * if (isMoon) (1f - daylight * .82f) else daylight
            if (visibility <= 0) continue
            // Celestial bodies are distant: page/cloud parallax must not move them
            // relative to the same projection on Lock screen and Always-on.
            val x = width * (.12f + .76f * (1 - sin(body.second * Math.PI / 180).toFloat()) / 2)
            val y = height * (.43f - body.first.toFloat().coerceIn(0f, 90f) / 90f * .32f)
            val radius = width * (if (isMoon) .027f else .045f)
            if (isMoon) {
                val prefs = preferences ?: continue
                val moon = SkyAstronomy.moon(System.currentTimeMillis(), prefs.getFloat("latitude", 0f).toDouble(), prefs.getFloat("longitude", 0f).toDouble())
                AtmosphereRenderer.moon(canvas, x, y, radius, moon, visibility)
                continue
            }
            paint.shader = RadialGradient(x, y, radius * 5f,
                intArrayOf(0x80FFE2AE.toInt(), 0x18FFF1CE, 0x00FFF1CE), null, Shader.TileMode.CLAMP)
            paint.alpha = (visibility * 255).toInt()
            canvas.drawCircle(x, y, radius * 5f, paint)
            paint.shader = null
            paint.color = if (isMoon) 0xFFE1E9F5.toInt() else 0xFFFFE7BB.toInt()
            paint.alpha = (visibility * 255).toInt()
            canvas.drawCircle(x, y, radius, paint)
        }
        if (daylight < 1) {
            paint.shader = null
            paint.color = 0xFFF0F8FF.toInt()
            for (star in stars) {
                val twinkle = .25 + .3 * sin(seconds * star[4] + star[3])
                paint.alpha = ((1f - daylight) * (1f - cover * .96f) * twinkle.coerceAtLeast(.035) * 255).toInt()
                canvas.drawCircle(star[0] * width, star[1] * height, star[2] * width / 540f, paint)
            }
        }
        val cloudLight = when (skyPhase) {
            "sunrise" -> 0xFFFFDEB7.toInt()
            "sunset" -> 0xFFFFCEC4.toInt()
            "night" -> 0xFF7A99C7.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        AtmosphereRenderer.clouds(canvas, width, height, seconds.toFloat(), cover, daylight, wind(), offset,
            if (code >= 95) 1f else 0f, cloudLight)
        if (code == 45 || code == 48) {
            paint.shader = LinearGradient(0f, height * .3f, 0f, height,
                intArrayOf(0x00CED7DE, 0xAACED7DE.toInt()), null, Shader.TileMode.CLAMP)
            paint.alpha = 160; canvas.drawRect(0f, height * .3f, width, height, paint); paint.shader = null
        }
        if (code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99) {
            val snow = code in 71..77 || code == 85 || code == 86
            val heavy = code in listOf(55, 65, 75, 82, 86, 95, 96, 99)
            val count = if (heavy) 95 else if (code in 51..57) 28 else 55
            paint.color = if (snow) 0xFFDCE8F2.toInt() else 0xFF7798B5.toInt()
            for (i in 0 until count) {
                val depth = .35f + (i % 7) / 7f
                paint.alpha = (depth * 130).toInt(); paint.strokeWidth = depth * width / 540f
                val speed = (if (snow) 26 else 310) * depth * height / 1100f
                val drift = wind() * .7f * depth
                val x = (((i * 127 + 31) % 997) / 997f * width + seconds * drift + (if (snow) sin(seconds * .8 + i) * 12 else 0.0)).toFloat() % width
                val y = ((seconds * speed + i * 83) % height).toFloat()
                if (snow) canvas.drawCircle(x, y, (1 + depth * 2) * width / 540f, paint)
                else canvas.drawLine(x, y, x + drift * .04f, y + (7 + depth * 18) * height / 1100f, paint)
            }
        }
        drawDecorations(canvas, width, height, seconds, daylight, code, wind())
    }

    /** WeatherGPT's clear-sky plane, moderate-wind kites, strong-wind wisps and meteors. */
    private fun drawDecorations(canvas: Canvas, w: Float, h: Float, t: Double, day: Float, code: Int, wind: Float) {
        val p = decorationPaint
        p.reset(); p.isAntiAlias = true
        val scale = w / 540f
        val path = decorationPath
        path.reset()
        val rain = code in 51..67 || code in 80..82 || code >= 95
        if (day > .5f && code in 0..1 && t > 20 && (t - 20) % 50 < 10) {
            val q = ((t - 20) % 50 / 10).toFloat()
            val x = w * (-.05f + q * 1.1f); val y = h * (.65f - q * .4f)
            p.shader = LinearGradient(x - 90 * scale, y + 33 * scale, x, y,
                intArrayOf(0x00FFFFFF, 0xA6FFFFFF.toInt()), null, Shader.TileMode.CLAMP)
            p.strokeWidth = scale; canvas.drawLine(x - 90 * scale, y + 33 * scale, x, y, p)
            p.shader = null; p.color = 0xE6FFFFFF.toInt()
            path.moveTo(x + 5 * scale, y - 2 * scale); path.lineTo(x - 6 * scale, y - 2 * scale)
            path.lineTo(x - scale, y + scale); path.lineTo(x - 2 * scale, y + 6 * scale)
            path.lineTo(x + scale, y); path.close(); canvas.drawPath(path, p)
        }
        if (day > .5f && !rain && code in 0..3 && wind in 8f..24f) {
            for (i in 0..1) {
                val x = w * (.2f + i * .58f) + sin(t * .6 + i).toFloat() * 9 * scale
                val y = h * (.24f + i * .05f) + sin(t * .8).toFloat() * 7 * scale
                p.style = Paint.Style.FILL; p.color = if (i == 0) 0xB3F08E51.toInt() else 0xB3FFFFFF.toInt()
                path.reset(); path.moveTo(x, y - 10 * scale); path.lineTo(x + 7 * scale, y)
                path.lineTo(x, y + 12 * scale); path.lineTo(x - 7 * scale, y); path.close(); canvas.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.color = 0x80FFFFFF.toInt(); p.strokeWidth = scale
                path.reset(); path.moveTo(x, y + 12 * scale)
                for (j in 1..19) path.lineTo(x + sin(t + j * .4).toFloat() * j * .25f * scale, y + (12 + j * 2) * scale)
                canvas.drawPath(path, p)
            }
        }
        if (wind >= 30) {
            p.style = Paint.Style.STROKE; p.color = 0x3DEBF5FF; p.strokeWidth = scale
            for (i in 0..4) {
                val x = ((t * 70 * scale + i * 190 * scale) % (w + 150 * scale) - 150 * scale).toFloat()
                val y = h * (.2f + i * .07f)
                path.reset(); path.moveTo(x, y); path.cubicTo(x + 30 * scale, y - 12 * scale, x + 60 * scale, y + 10 * scale, x + 120 * scale, y)
                canvas.drawPath(path, p)
            }
        }
        if (day < .5f && code in -1..2 && t >= 8) {
            val cycle = SkyMeteor.cycle(t)
            for (i in 0 until 1 + cycle % 2) {
                val age = SkyMeteor.age(t, i) ?: continue
                val star = stars[(cycle + i) % stars.size]
                val flight = SkyMeteor.flight(w, h, age, star[0], (cycle + i) % 3 == 2)
                val alpha = (flight.opacity * (1f - day) * 230).toInt().coerceIn(0, 255)
                p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
                p.shader = LinearGradient(flight.tailX, flight.tailY, flight.x, flight.y,
                    intArrayOf(0x00DDEEFF, (alpha shl 24) or 0xEAF4FF), null, Shader.TileMode.CLAMP)
                p.strokeWidth = 1.4f * scale
                canvas.drawLine(flight.tailX, flight.tailY, flight.x, flight.y, p)
                p.shader = null; p.style = Paint.Style.FILL
                p.color = (alpha shl 24) or 0xF5FAFF
                canvas.drawCircle(flight.x, flight.y, 1.15f * scale, p)
            }
        }
    }
}

@Composable
fun SkyBackground(modifier: Modifier = Modifier, offset: () -> Float = { .5f }) {
    var revision by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        SkyScene.observe(listener)
        onDispose { SkyScene.unobserve(listener) }
    }
    val minute by produceState(0) { while (true) { delay(1000); value++ } }
    val frame = produceState(0L) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (now - last >= SkyScene.frameDelay() * 1_000_000) { last = now; value = now }
        }
    }
    val palette = androidx.compose.runtime.remember(minute, revision) { SkyScene.colors() }
    val top by androidx.compose.animation.animateColorAsState(androidx.compose.ui.graphics.Color(palette[0]),
        androidx.compose.animation.core.tween(2000), label = "sky top")
    val middle by androidx.compose.animation.animateColorAsState(androidx.compose.ui.graphics.Color(palette[1]),
        androidx.compose.animation.core.tween(2000), label = "sky middle")
    val bottom by androidx.compose.animation.animateColorAsState(androidx.compose.ui.graphics.Color(palette[2]),
        androidx.compose.animation.core.tween(2000), label = "sky horizon")
    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val refresh = minute + revision + frame.value
        SkyScene.draw(drawContext.canvas.nativeCanvas, size.width, size.height, offset(),
            intArrayOf(top.toArgb(), middle.toArgb(), bottom.toArgb()))
    }
}

/** Battery-conscious shared Home/lock wallpaper; no loops while invisible. */
class SkyWallpaperService : WallpaperService() {
    private val weatherWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var lastWeatherAttempt = 0L
    private var weatherLoading = false
    private var destroyed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    override fun onCreate() { super.onCreate(); SkyScene.configure(applicationContext) }
    override fun onCreateEngine(): Engine = SkyEngine()
    override fun onDestroy() {
        destroyed = true
        weatherWorker.shutdownNow()
        super.onDestroy()
    }
    private fun refreshWeather() {
        val prefs = getSharedPreferences("living_sky", 0)
        val now = System.currentTimeMillis()
        if (weatherLoading || !prefs.contains("latitude") || now - lastWeatherAttempt < 5 * 60 * 1000
            || now - prefs.getLong("updated", 0) < 30 * 60 * 1000) return
        val lat = prefs.getFloat("latitude", 0f)
        val lon = prefs.getFloat("longitude", 0f)
        lastWeatherAttempt = now; weatherLoading = true
        weatherWorker.execute {
            val result = runCatching {
                val endpoint = java.net.URL("https://weathergpt-7jyp.onrender.com/api/weather?latitude=$lat&longitude=$lon&model=best_match")
                val connection = endpoint.openConnection() as javax.net.ssl.HttpsURLConnection
                connection.connectTimeout = 10000; connection.readTimeout = 10000
                connection.instanceFollowRedirects = false
                try {
                    check(connection.responseCode == 200)
                    val text = connection.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(65537)
                        var count = 0
                        while (count < buffer.size) {
                            val read = reader.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        check(count < buffer.size)
                        String(buffer, 0, count)
                    }
                    org.json.JSONObject(text).getJSONObject("current")
                } finally { connection.disconnect() }
            }.getOrNull()
            mainHandler.post {
                weatherLoading = false
                if (!destroyed && result != null && lat == prefs.getFloat("latitude", 0f)
                    && lon == prefs.getFloat("longitude", 0f)) {
                    val wind = result.optDouble("wind_speed_10m", Double.NaN)
                    val code = result.optInt("weather_code", -1)
                    if (wind.isFinite() && code in 0..99) prefs.edit {
                        putInt("weather_code", code); putFloat("wind", wind.coerceIn(0.0, 250.0).toFloat())
                        putLong("updated", System.currentTimeMillis())
                    }
                }
            }
        }
    }
    inner class SkyEngine : Engine() {
        private val choreographer = Choreographer.getInstance()
        private var visible = false
        private var offset = .5f
        private var lastPaletteHash = 0
        private var diagnosticMinute = -1
        private var lastFrameNanos = 0L
        private var lastLockCheck = 0L
        private var lockDepth = false
        private val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        private val render = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!visible) return
                if (frameTimeNanos - lastFrameNanos >= SkyScene.frameDelay() * 1_000_000) {
                    lastFrameNanos = frameTimeNanos
                    refreshWeather()
                    draw()
                }
                if (visible) choreographer.postFrameCallback(this)
            }
        }
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            choreographer.removeFrameCallback(render)
            lastFrameNanos = 0L
            if (visible) choreographer.postFrameCallback(render)
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { draw() }
        override fun onOffsetsChanged(x: Float, y: Float, xs: Float, ys: Float, xp: Int, yp: Int) {
            offset = x
            if (visible) draw()
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            choreographer.removeFrameCallback(render)
            super.onSurfaceDestroyed(holder)
        }
        override fun onDestroy() { visible = false; choreographer.removeFrameCallback(render); super.onDestroy() }
        override fun onComputeColors(): WallpaperColors {
            val palette = SkyScene.colors()
            // Let Android derive both palette seeds and legible status-bar text hints.
            val preview = createBitmap(32, 64)
            val paint = Paint().apply {
                shader = LinearGradient(0f, 0f, 8f, 64f, palette, null, Shader.TileMode.CLAMP)
            }
            android.graphics.Canvas(preview).drawRect(0f, 0f, 32f, 64f, paint)
            return WallpaperColors.fromBitmap(preview).also { preview.recycle() }
        }
        private fun draw() {
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: holder.lockCanvas() ?: return
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastLockCheck > 300) {
                lastLockCheck = now
                lockDepth = keyguard?.isKeyguardLocked == true
            }
            try { SkyScene.draw(canvas, canvas.width.toFloat(), canvas.height.toFloat(), offset, lockDepth = lockDepth) }
            finally { holder.unlockCanvasAndPost(canvas) }
            val paletteHash = SkyScene.colors().contentHashCode()
            if (lastPaletteHash != paletteHash) { lastPaletteHash = paletteHash; notifyColorsChanged() }
            val minute = LocalTime.now().minute
            if (diagnosticMinute != minute) {
                diagnosticMinute = minute
                android.util.Log.i("GTOSSky", SkyScene.diagnostics())
            }
        }
    }
}


