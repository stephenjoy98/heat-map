import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase (sign-in, backup, friends, cloud AI) is only wired up when its config file is present,
// so the app still builds and runs without a Firebase project.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsApiKey: String = System.getenv("MAPS_API_KEY")?.takeIf { it.isNotBlank() }
    ?: localProps.getProperty("MAPS_API_KEY", "")

android {
    namespace = "io.github.stephenjoy98.heatmap"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.stephenjoy98.heatmap"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "HAS_MAPS_KEY", (mapsApiKey.isNotEmpty()).toString())
    }

    signingConfigs {
        // A fixed development key, so the SHA-1 registered in Firebase for Google sign-in stays the same
        // across machines and CI runs. Not meant for Play Store releases.
        create("dev") {
            storeFile = file("signing/dev.keystore")
            storeType = "pkcs12"
            storePassword = "locationticker"
            keyAlias = "dev"
            keyPassword = "locationticker"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.work:work-runtime-ktx:2.10.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Location, activity recognition, maps
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.12.0")
    // Vector OpenStreetMap maps (OpenFreeMap tiles), used when the build has no Google Maps key
    implementation("org.maplibre.gl:android-sdk:11.13.5")

    // Steps
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Sign-in, backup, friends, cloud AI
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-ai")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // On-device Gemini Nano
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")

    // Friend QR codes
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
