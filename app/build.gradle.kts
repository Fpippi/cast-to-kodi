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

    // Su GitHub Actions la chiave arriva dai secrets (vedi apk.yml): stessa firma a ogni build,
    // così l'APK si aggiorna senza disinstallare. Senza, resta la chiave debug.
    signingConfigs {
        System.getenv("KEYSTORE_FILE")?.let { path ->
            create("casttokodi") {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "casttokodi"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("casttokodi")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Solo per i test (app/src/test), non entra nell'APK
dependencies {
    testImplementation("junit:junit:4.13.2")
}
