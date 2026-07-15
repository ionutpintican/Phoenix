plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.phoenix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phoenix"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            // Distinct package id + app name so this feature-branch build installs *alongside*
            // the main app (its own icon, its own SharedPreferences, its own Android Auto entry)
            // instead of replacing it. Lets Settings and main be tested side by side.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-settings"
            resValue("string", "app_name", "Phoenix (Settings)")
        }
        release {
            resValue("string", "app_name", "Phoenix")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // ExoPlayer and the Media3 session/Auto APIs are @UnstableApi; opt in project-wide
        // or the build fails to compile.
        freeCompilerArgs = freeCompilerArgs + "-opt-in=androidx.media3.common.util.UnstableApi"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val media3 = "1.6.1"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")

    // Media3 — playback engine + media session + Android Auto browse tree.
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")

    // Radio-browser JSON is parsed with org.json (bundled in the platform, no dep needed).
    implementation("com.google.guava:guava:33.3.1-android")
}
