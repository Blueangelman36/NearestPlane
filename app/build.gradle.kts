import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * A stable signing key, supplied by CI from repository secrets.
 *
 * Without one, every build is signed with whatever debug keystore happens to
 * exist on the machine — and a CI runner is a fresh machine each time, so each
 * build got a *different* key. Android refuses to install an update whose
 * signature doesn't match what's already there, which surfaces on the phone as
 * a bare "App not installed" with no explanation.
 *
 * Absent (a fork without the secrets, or a local build) this falls back to the
 * debug key, which is at least stable per-machine.
 */
val signingKeystore: File? = System.getenv("SIGNING_KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() }
    ?.let { File(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.connor.nearestplane"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.connor.nearestplane"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6"
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("stable") {
                storeFile = signingKeystore
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "nearestplane"
                keyPassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig =
                if (signingKeystore != null) signingConfigs.getByName("stable")
                else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // VERSION_NAME, so the app can tell whether a release is newer than it.
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")

    // Home-screen widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Background refresh
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // App-wide settings shared between the app and both widgets
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // GPS
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
