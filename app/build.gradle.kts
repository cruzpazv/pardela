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
        // Versión automática desde git (no hay que tocarla a mano):
        //   etiqueta v1.2.3            → 1.2.3        (código 1020300)
        //   3 commits después de v1.2.3 → 1.2.3-dev3  (código 1020303: se instala encima de la 1.2.3)
        //   sin etiquetas todavía       → 1.0.0-devN  (código N)
        val (codigo, nombre) = versionDesdeGit()
        versionCode = codigo
        versionName = nombre
        buildConfigField("String", "CONTACTO", "\"${project.findProperty("pardela.contacto") ?: ""}\"")
        // Mensaje opcional en la bienvenida. Viene de un secreto de GitHub, así que no queda en el código.
        val dedicatoria = (System.getenv("PARDELA_DEDICATORIA") ?: "").trim()
            .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        buildConfigField("String", "DEDICATORIA", "\"$dedicatoria\"")
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
    // Las pruebas usan los textos de verdad (res/values*).
    testOptions { unitTests { isIncludeAndroidResources = true } }
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

    // Pruebas automáticas (se ejecutan en Actions antes de compilar). Robolectric simula Android sin emulador.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}

/** Ejecuta git en el repo; null si falla (p. ej. sin git o sin etiquetas). */
fun git(vararg args: String): String? = try {
    val p = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
    val salida = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0) salida else null
} catch (e: Exception) { null }

fun versionDesdeGit(): Pair<Int, String> {
    val m = git("describe", "--tags", "--long", "--match", "v[0-9]*")
        ?.let { Regex("""^v(\d+)\.(\d+)\.(\d+)-(\d+)-g[0-9a-f]+$""").find(it) }
    if (m == null) {
        val n = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        return n to "1.0.0-dev$n"
    }
    val (a, b, c, desde) = m.destructured.toList().map { it.toInt() }
    val codigo = a * 1_000_000 + b * 10_000 + c * 100 + minOf(desde, 99)
    return codigo to (if (desde == 0) "$a.$b.$c" else "$a.$b.$c-dev$desde")
}

// Si una prueba falla, el log de Actions muestra el error completo.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }
}
