# WeatherGPT's restricted data bridge is called by JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
