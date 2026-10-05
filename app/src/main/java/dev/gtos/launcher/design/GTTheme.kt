package dev.gtos.launcher.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

private val GtDark = darkColorScheme(
    primary = Color(0xFFB1D8EE),
    onPrimary = Color(0xFF20384F),
    background = Color(0xFF142237),
    onBackground = Color(0xFFEEF3FC),
    surface = Color(0xFF20384F),
    onSurface = Color(0xFFEEF3FC),
    surfaceVariant = Color(0xFF314E69),
    onSurfaceVariant = Color(0xFFADBED2),
    outline = Color(0xFF8EAABD),
    error = Color(0xFFFFB4AB),
)

@Composable
fun GTTheme(content: @Composable () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        SkyScene.observe(listener)
        onDispose { SkyScene.unobserve(listener) }
    }
    val minute by produceState(0) { while (true) { delay(60_000); value++ } }
    val night = remember(minute, revision) { SkyScene.isDark() }
    val daylight = lightColorScheme(primary = Color(0xFF285E7B), onPrimary = Color.White,
        background = Color(0xFFE6EDF2), onBackground = Color(0xFF20384F),
        surface = Color(0xFFE6EDF2), onSurface = Color(0xFF20384F),
        surfaceVariant = Color(0xFFC2DDEF), onSurfaceVariant = Color(0xFF4E6980), outline = Color(0xFF859DAF))
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (night) GtDark else daylight
    MaterialTheme(colorScheme = scheme, content = content)
}

