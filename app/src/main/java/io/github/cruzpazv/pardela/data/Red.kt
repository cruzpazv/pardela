package io.github.cruzpazv.pardela.data

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

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

class ErrorRed(msg: String, val codigo: Int = 0) : IOException(msg)

/** Peticiones HTTP y utilidades JSON tolerantes (las APIs cambian campos a menudo). */
internal object Red {
    /**
     * RapidAPI indica en cada respuesta cuánto queda del plan: x-ratelimit-<nombre>-remaining / -limit.
     * El nombre depende del plan ("requests", "units"…): se usa el que menos margen deja.
     */
    private fun apuntarCuota(r: okhttp3.Response) {
        val cuotas = r.headers.names()
            .filter { it.startsWith("x-ratelimit-", ignoreCase = true) && it.endsWith("-remaining", ignoreCase = true) }
            .mapNotNull { n ->
                val queda = r.header(n)?.toIntOrNull() ?: return@mapNotNull null
                queda to r.header(n.dropLast("remaining".length) + "limit")?.toIntOrNull()
            }
        val (queda, limite) = cuotas.minByOrNull { it.first } ?: return
        Almacen.ajustar { it.copy(cuotaRestante = queda, cuotaLimite = limite ?: it.cuotaLimite, cuotaMomento = System.currentTimeMillis()) }
    }

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
                if (url.contains("rapidapi.com")) apuntarCuota(r)
                when {
                    r.code == 204 || r.code == 404 -> null
                    r.code == 401 || r.code == 403 -> throw ErrorRed(tx(R.string.la_clave_api_no_es_valida), r.code)
                    r.code == 429 && url.contains("rapidapi.com") ->
                        throw ErrorRed(tx(R.string.se_han_agotado_las_consultas_de_la_cla))
                    r.code == 429 -> throw ErrorRed(tx(R.string.demasiadas_consultas_espere_un_momento))
                    !r.isSuccessful -> throw ErrorRed(tx(R.string.error_del_servidor_x, r.code))
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
