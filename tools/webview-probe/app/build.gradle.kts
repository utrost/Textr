plugins { id("com.android.application") }
android {
    namespace = "de.utrost.textr.webviewprobe"
    compileSdk = 36
    defaultConfig {
        applicationId = "de.utrost.textr.webviewprobe"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "debug-probe"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
