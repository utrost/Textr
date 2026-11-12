import java.util.Properties

plugins {
    id("com.android.application")
}

// Credentials stay in an owner-managed file outside the checkout. Debug/PR
// builds neither require nor receive release credentials.
val signingPath = providers.environmentVariable("TEXTR_SIGNING_PROPERTIES").orNull
val releaseCredentials = Properties().apply {
    signingPath?.let { path -> file(path).inputStream().use { load(it) } }
}
val verifyReleaseCredentials = tasks.register("verifyReleaseCredentials") {
    doLast {
        check(signingPath != null) {
            "Release signing requires TEXTR_SIGNING_PROPERTIES. See docs/releasing.md."
        }
        for (name in listOf("storeFile", "storePassword", "keyAlias", "keyPassword")) {
            check(!releaseCredentials.getProperty(name).isNullOrBlank()) { "Missing release signing field: $name" }
        }
        check(file(releaseCredentials.getProperty("storeFile")).isFile) { "Release keystore does not exist." }
    }
}

android {
    namespace = "de.utrost.textr"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.utrost.textr"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.gradleProperty("textrVersionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("textrVersionName").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.create("ownerRelease") {
                releaseCredentials.getProperty("storeFile")?.let { storeFile = file(it) }
                storePassword = releaseCredentials.getProperty("storePassword")
                keyAlias = releaseCredentials.getProperty("keyAlias")
                keyPassword = releaseCredentials.getProperty("keyPassword")
            }
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES")
    }
    lint {
        abortOnError = true
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(verifyReleaseCredentials)
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("org.commonmark:commonmark:0.32.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.32.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.23.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
