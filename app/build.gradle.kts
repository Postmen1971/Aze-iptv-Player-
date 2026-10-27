plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Versionsnummer kommt aus dem GitHub-Build (Laufnummer). Lokal: 1.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val keystoreFile = System.getenv("KEYSTORE_FILE")

android {
    namespace = "de.aze.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.aze.iptv"
        minSdk = 21
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "UPDATE_REPO", "\"Postmen1971/Aze-iptv-Player-\"")
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "aze"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Je Prozessor-Typ eine eigene, kleinere APK (VLC bringt große Bibliotheken mit) plus eine für alle Geräte.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Poster und Bilder für Filme & Serien
    implementation("io.coil-kt:coil-compose:2.7.0")

    // VLC als Ausweich-Player (LGPL 2.1, unverändert eingebunden)
    implementation("org.videolan.android:libvlc-all:3.6.0")
}
