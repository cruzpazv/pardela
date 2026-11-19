package io.github.cruzpazv.pardela.data

import io.github.cruzpazv.pardela.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ErrorRed(msg: String) : IOException(msg)

/** Peticiones HTTP y utilidades JSON tolerantes (las APIs cambian campos a menudo). */
internal object Red {
    val agente = "Pardela/${BuildConfig.VERSION_NAME} (Android; +${BuildConfig.CONTACTO})"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { c -> c.proceed(c.request().newBuilder().header("User-Agent", agente).build()) }
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // adsb.fi permite ~1 petición por segundo.
    private val turno = Mutex()
    private var ultima = 0L

    suspend fun get(url: String, cabeceras: Map<String, String> = emptyMap(), despacio: Boolean = false): JsonElement? =
        withContext(Dispatchers.IO) {
            if (despacio) turno.withLock {
                val espera = 1100 - (System.currentTimeMillis() - ultima)
                if (espera > 0) delay(espera)
                ultima = System.currentTimeMillis()
            }
            val req = Request.Builder().url(url).apply { cabeceras.forEach { (k, v) -> header(k, v) } }.build()
            http.newCall(req).execute().use { r ->
                when {
                    r.code == 204 || r.code == 404 -> null
                    r.code == 401 || r.code == 403 -> throw ErrorRed("La clave API no es válida")
                    r.code == 429 -> throw ErrorRed("Demasiadas consultas. Espere un momento")
                    !r.isSuccessful -> throw ErrorRed("Error del servidor (${r.code})")
                    else -> r.body?.string()?.takeIf { it.isNotBlank() }?.let { intentar { json.parseToJsonElement(it) }.getOrNull() }
                }
            }
        }
}

internal fun JsonElement?.o(k: String): JsonElement? = (this as? JsonObject)?.get(k)?.takeIf { it !is JsonNull }
internal fun JsonElement?.s(k: String): String? = (o(k) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
internal fun JsonElement?.d(k: String): Double? = (o(k) as? JsonPrimitive)?.doubleOrNull
internal fun JsonElement?.b(k: String): Boolean? = (o(k) as? JsonPrimitive)?.booleanOrNull
internal fun JsonElement?.lista(k: String): List<JsonElement> = (o(k) as? JsonArray)?.toList() ?: emptyList()

/**
 * Como runCatching, pero deja pasar la cancelación de corrutinas.
 * Si no, al salir de una pantalla la cancelación se mostraba como error.
 */
inline fun <T> intentar(bloque: () -> T): Result<T> = try {
    Result.success(bloque())
} catch (e: kotlin.coroutines.cancellation.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
