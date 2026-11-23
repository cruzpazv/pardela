pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // El Android simulado de las pruebas (~150 MB) se baja de la copia de Maven Central de Google:
        // Maven Central responde 429 (demasiadas descargas) a los ficheros grandes pedidos desde GitHub Actions.
        exclusiveContent {
            forRepository { maven("https://maven-central.storage-download.googleapis.com/maven2/") }
            filter { includeModule("org.robolectric", "android-all-instrumented") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "Pardela"
include(":app")
