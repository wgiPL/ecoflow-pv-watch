import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Klucze EcoFlow z secrets.properties (gitignored) -> BuildConfig.
// Apka jest tylko do użytku własnego/sideload, więc to świadomy kompromis.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.akond.ecoflowpv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.akond.ecoflowpv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        buildConfigField(
            "String", "ECOFLOW_ACCESS_KEY",
            "\"${secrets.getProperty("ECOFLOW_ACCESS_KEY", "")}\""
        )
        buildConfigField(
            "String", "ECOFLOW_SECRET_KEY",
            "\"${secrets.getProperty("ECOFLOW_SECRET_KEY", "")}\""
        )
        // Opcjonalny SN; pusty -> apka sama wykryje pierwsze urządzenie z device/list.
        buildConfigField(
            "String", "ECOFLOW_SN",
            "\"${secrets.getProperty("ECOFLOW_SN", "")}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
