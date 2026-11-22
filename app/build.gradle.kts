plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val ks = System.getenv("PARDELA_KEYSTORE")

android {
    namespace = "io.github.cruzpazv.pardela"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.cruzpazv.pardela"
        minSdk = 26
        targetSdk = 35
        // Subir ambos en cada versión publicada (tag vX.Y.Z). F-Droid los lee de aquí.
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "CONTACTO", "\"${project.findProperty("pardela.contacto") ?: ""}\"")
    }
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = file(ks)
            storePassword = System.getenv("PARDELA_KEYSTORE_PASS")
            keyAlias = "pardela"
            keyPassword = System.getenv("PARDELA_KEYSTORE_PASS")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (ks != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
    // Quita el bloque cifrado de dependencias que AGP mete para Google Play (no hace falta fuera de Play).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Mapas de OpenStreetMap, sin cuenta ni clave.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
