import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from either:
//   1) keystore.properties next to this file (gitignored — for local/manual releases), or
//   2) environment variables (for CI: MICROMAX_KEYSTORE_PATH / _PASSWORD / _KEY_ALIAS / _KEY_PASSWORD)
// Never commit a real keystore or its passwords. If neither source is configured,
// the release build type falls back to no signing config (assembleRelease will fail
// with a clear "not signed" error instead of silently producing a debug-signed APK).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}
fun signingProp(key: String, envVar: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(envVar)

android {
    namespace = "com.micromax.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.micromax.app"
        minSdk = 26
        targetSdk = 35
        // Keep versionCode/versionName in step with the README's V-number on every release.
        versionCode = 301
        versionName = "3.0.1"

        val apiBaseUrl = System.getenv("MICROMAX_API_BASE_URL") ?: "https://api.example.com"
        buildConfigField("String", "MICROMAX_API_BASE_URL", "\"$apiBaseUrl\"")
        manifestPlaceholders["allowCleartext"] = "false"
        val googleWebClientId = System.getenv("MICROMAX_GOOGLE_WEB_CLIENT_ID") ?: "REPLACE_WITH_GOOGLE_WEB_CLIENT_ID"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    val storeFilePath = signingProp("storeFile", "MICROMAX_KEYSTORE_PATH")
    val storePasswordVal = signingProp("storePassword", "MICROMAX_KEYSTORE_PASSWORD")
    val keyAliasVal = signingProp("keyAlias", "MICROMAX_KEY_ALIAS")
    val keyPasswordVal = signingProp("keyPassword", "MICROMAX_KEY_PASSWORD")
    val hasReleaseSigning = storeFilePath != null && storePasswordVal != null &&
        keyAliasVal != null && keyPasswordVal != null && file(storeFilePath).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePasswordVal
                keyAlias = keyAliasVal
                keyPassword = keyPasswordVal
            }
        }
    }

    buildTypes {
        debug {
            // Local emulator backend only; never enabled in release builds.
            manifestPlaceholders["allowCleartext"] = "true"
            buildConfigField("String", "MICROMAX_API_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    // Never allow an unsigned or debug-signed artifact to be mistaken for a Play release.
    // Debug builds remain usable without credentials; assembleRelease/bundleRelease do not.
    tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
        doFirst {
            check(hasReleaseSigning) {
                "Release signing is not configured. Provide keystore.properties or " +
                    "MICROMAX_KEYSTORE_PATH, MICROMAX_KEYSTORE_PASSWORD, " +
                    "MICROMAX_KEY_ALIAS, and MICROMAX_KEY_PASSWORD before building Play artifacts."
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20241224")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.print:print:1.1.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
