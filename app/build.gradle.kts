
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

// Semantic Versioning (https://semver.org). The version lives in gradle.properties; versionCode is derived
// from it (2.11.3 -> 21103) so it always increases with the version, as Android requires for updates.
val appVersion: String = providers.gradleProperty("appVersion").get()
val semver = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""").matchEntire(appVersion)
    ?: error("appVersion '$appVersion' must be MAJOR.MINOR.PATCH, see https://semver.org")
val (verMajor, verMinor, verPatch) = semver.destructured.toList().map { it.toInt() }
require(verMinor < 100 && verPatch < 100) { "appVersion minor and patch must stay below 100 to fit versionCode" }

// Bundles CHANGELOG.md into the app (shown as "What's new" after an update), refusing to build if it has
// no entry for the current version.
val changelogDir = layout.buildDirectory.dir("generated/changelog")
val bundleChangelog by tasks.registering(Copy::class) {
    val changelog = rootProject.file("CHANGELOG.md")
    val version = appVersion
    inputs.property("appVersion", version)
    from(changelog)
    into(changelogDir)
    doFirst {
        check(changelog.readText().contains("## [$version]")) {
            "CHANGELOG.md has no \"## [$version]\" entry. Add one before building version $version."
        }
    }
}
tasks.named("preBuild") { dependsOn(bundleChangelog) }

android {
    namespace = "io.github.stephenjoy98.heatmap"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.stephenjoy98.heatmap"
        minSdk = 26
        targetSdk = 35
        versionCode = verMajor * 10_000 + verMinor * 100 + verPatch
        versionName = appVersion

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

    sourceSets {
        getByName("main") { assets.srcDir(changelogDir.get().asFile) }
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

    // Location and activity recognition
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Vector OpenStreetMap maps (OpenFreeMap tiles)
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

    // Profile pictures
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
