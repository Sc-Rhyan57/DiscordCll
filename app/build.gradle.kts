plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace   = "com.discordcall"
    compileSdk  = 34

    defaultConfig {
        applicationId = "com.discordcall"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName   = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
    }

    signingConfigs {
        create("release") {
            storeFile     = file("../keystore.jks")
            storePassword = "astral9090"
            keyAlias      = "astral"
            keyPassword   = "astral9090"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig   = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    implementation("org.webrtc:google-webrtc:1.0.32006")

    implementation("com.github.bumptech.glide:glide:4.16.0")
}
