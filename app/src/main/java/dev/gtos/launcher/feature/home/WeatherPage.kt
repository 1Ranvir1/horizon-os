package dev.gtos.launcher.feature.home

import android.content.Intent
import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.gtos.launcher.design.SkyScene
import org.json.JSONObject

private const val WEATHER_HOST = "weathergpt-7jyp.onrender.com"
private const val WEATHER_URL = "https://$WEATHER_HOST/"

/** The user's real hosted WeatherGPT, with a narrowly scoped sky-data bridge. */
@Composable
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility") // HTTPS data-only bridge; only horizontal drags intercepted, taps/accessibility stay with WebView.
fun WeatherPage(onExitToApps: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var web by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val currentExit by rememberUpdatedState(onExitToApps)
    DisposableEffect(web, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> web?.onResume()
                Lifecycle.Event.ON_PAUSE -> web?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) {
            Surface(color = Color(0xEE142239)) {
                Column(Modifier.padding(20.dp)) {
                    Text("WeatherGPT couldn’t connect. Check your connection and try again.", color = Color.White)
                    TextButton(onClick = { failed = false; loading = true; web?.loadUrl(WEATHER_URL) }) { Text("Retry") }
                }
            }
        }
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { viewContext ->
            WebView(viewContext).apply {
                web = this
                setBackgroundColor(SkyScene.colors()[0])
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setGeolocationEnabled(false)
                var weatherPage = 0
                addJavascriptInterface(SkyBridge(viewContext.applicationContext) { weatherPage = it }, "GTOSSky")
                var startX = 0f
                var startY = 0f
                var gesture = false
                var direction = 0
                val threshold = 60 * resources.displayMetrics.density
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            startX = event.x; startY = event.y; gesture = false; direction = 0
                            false
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - startX
                            val dy = event.y - startY
                            // Map panning stays native except for the header/edge navigation zones.
                            val mapNavigationZone = startY < 100 * resources.displayMetrics.density ||
                                startX < 24 * resources.displayMetrics.density || startX > width - 24 * resources.displayMetrics.density
                            if (!gesture && kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f &&
                                (weatherPage != 2 || mapNavigationZone) && startY < height * .85f) {
                                gesture = true; direction = if (dx < 0) -1 else 1
                                android.view.MotionEvent.obtain(event).also { cancel ->
                                    cancel.action = android.view.MotionEvent.ACTION_CANCEL
                                    view.onTouchEvent(cancel); cancel.recycle()
                                }
                            }
                            gesture
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (gesture) {
                                if (direction < 0 && weatherPage == 0) currentExit()
                                else {
                                    val nextPage = (weatherPage + if (direction > 0) 1 else -1).coerceIn(0, 2)
                                    evaluateJavascript("document.querySelector('.app-pages')?.scrollTo({left:$nextPage*document.querySelector('.app-pages').clientWidth,behavior:'smooth'})", null)
                                }
                            }
                            gesture
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> { gesture = false; false }
                        else -> gesture
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageCommitVisible(view: WebView, url: String) {
                        if (url.toUri().host == WEATHER_HOST) view.postDelayed({ view.evaluateJavascript(SKY_BRIDGE_SCRIPT, null) }, 500)
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme == "https" && uri.host == WEATHER_HOST) return false
                        if (request.isForMainFrame && (uri.scheme == "https" || uri.scheme == "http")) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
                        return request.isForMainFrame
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        loading = false
                        if (url.toUri().host == WEATHER_HOST) {
                            view.evaluateJavascript(SKY_BRIDGE_SCRIPT, null)
                            view.evaluateJavascript("JSON.stringify({h:innerHeight,w:innerWidth,main:document.querySelector('main')?.clientHeight,pages:document.querySelector('.app-pages')?.clientHeight})") {
                                android.util.Log.i("GTOSWeatherLayout", it)
                            }
                        }
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) { failed = true; loading = false }
                    }
                }
                loadUrl(WEATHER_URL)
            }
        }, onRelease = { view -> view.removeJavascriptInterface("GTOSSky"); view.stopLoading(); view.destroy(); web = null })
    }
}

private class SkyBridge(private val context: android.content.Context, private val onNavigation: (Int) -> Unit) {
    @JavascriptInterface
    fun navigation(index: Int) {
        if (index in 0..2) android.os.Handler(android.os.Looper.getMainLooper()).post { onNavigation(index) }
    }
    // This bridge cannot open apps, read files, access credentials, or grant permissions.
    @JavascriptInterface
    fun update(value: String) {
        if (value.length > 2048) return
        runCatching {
            val data = JSONObject(value)
            val lat = data.getDouble("latitude")
            val lon = data.getDouble("longitude")
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return
            val prefs = context.getSharedPreferences("living_sky", 0)
            prefs.edit {
                putFloat("latitude", lat.toFloat()); putFloat("longitude", lon.toFloat())
                putString("place", data.optString("place").take(100))
                putInt("weather_code", data.optInt("code", -1))
                val wind = data.optDouble("wind", Double.NaN)
                if (wind.isFinite()) putFloat("wind", wind.coerceIn(0.0, 250.0).toFloat())
                putLong("updated", System.currentTimeMillis())
            }
        }
    }
}

private val SKY_BRIDGE_SCRIPT = """
    (() => {
      if (location.origin !== 'https://weathergpt-7jyp.onrender.com' || window.__gtosSkyConnected) return;
      window.__gtosSkyConnected = true;
      const sync = () => {
        try {
          if (typeof locationChoice === 'undefined' || !locationChoice) return;
          GTOSSky.update(JSON.stringify({latitude: locationChoice.latitude, longitude: locationChoice.longitude,
            place: locationChoice.name, wind: typeof forecast !== 'undefined' ? forecast?.current?.wind_speed_10m ?? 0 : 0, code: typeof forecast !== 'undefined' ? forecast?.current?.weather_code ?? -1 : -1}));
        } catch (_) {}
      };
      addEventListener('weather-location-changed', sync);
      addEventListener('weather-data-changed', sync);
      const pages = document.querySelector('.app-pages');
      const navigation = () => { if (pages?.clientWidth) GTOSSky.navigation(Math.round(pages.scrollLeft / pages.clientWidth)); };
      pages?.addEventListener('scroll', navigation, {passive:true});
      navigation();
      sync();
    })();
""".trimIndent()
