plugins {
    id("com.android.application")
}

android {
    namespace = "de.utrost.textr"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.utrost.textr"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES")
    }
    lint {
        abortOnError = true
    }
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
}
