plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.franc.casttokodi"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.franc.casttokodi"
        minSdk = 33 // uso personale su Pixel 9 Pro XL
        targetSdk = 35
        // Su GitHub Actions cresce a ogni build, così ogni APK si installa sopra il precedente
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
