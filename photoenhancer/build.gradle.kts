plugins { alias(libs.plugins.android.application) }
android {
    namespace = "dev.gtos.photoenhancer"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.gtos.photoenhancer"
        minSdk = 35
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // This package is specifically deployed to the arm64 X6739 phone.
    lint { warningsAsErrors = true; disable += "ChromeOsAbiSupport" }
}
dependencies {
    implementation(libs.onnxruntime.android)
    implementation(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext)
}
