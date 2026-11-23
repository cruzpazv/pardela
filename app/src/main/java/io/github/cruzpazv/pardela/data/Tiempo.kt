package io.github.cruzpazv.pardela.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Tiempo y hora local de un aeropuerto con Open-Meteo (gratis, sin clave, CC BY 4.0).
 * Una sola consulta da el tiempo actual, la previsión por horas de 16 días y la zona horaria.
 */
object Tiempo {
    data class Parte(val temperatura: Double, val codigo: Int, val deDia: Boolean)
    data class Info(val zona: ZoneId?, val ahora: Parte?, val porHoras: List<Pair<Long, Parte>>)

    private val cache = HashMap<String, Pair<Long, Info>>()

    suspend fun de(lat: Double, lon: Double): Info? {
        val k = "%.2f,%.2f".format(java.util.Locale.ROOT, lat, lon)
        synchronized(cache) { cache[k]?.let { (t, i) -> if (System.currentTimeMillis() - t < 30 * 60_000L) return i } }
        val j = intentar {
            Red.get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
                    "&timezone=auto&timeformat=unixtime&forecast_days=16"
            )
        }.getOrNull() ?: return null
        val zona = j.s("timezone")?.let { intentar { ZoneId.of(it) }.getOrNull() }
        val c = j.o("current")
        val ahora = c.d("temperature_2m")?.let { t -> Parte(t, c.d("weather_code")?.toInt() ?: -1, c.d("is_day") != 0.0) }
        val h = j.o("hourly")
        fun serie(n: String) = h.lista(n).map { (it as? JsonPrimitive)?.doubleOrNull }
        val horas = serie("time"); val temp = serie("temperature_2m"); val cod = serie("weather_code"); val dia = serie("is_day")
        val porHoras = horas.indices.mapNotNull { i ->
            val t = horas[i] ?: return@mapNotNull null
            val grados = temp.getOrNull(i) ?: return@mapNotNull null
            (t.toLong() * 1000) to Parte(grados, cod.getOrNull(i)?.toInt() ?: -1, dia.getOrNull(i) != 0.0)
        }
        val info = Info(zona, ahora, porHoras)
        synchronized(cache) { cache[k] = System.currentTimeMillis() to info }
        return info
    }

    /** Previsión para un momento (ms UTC), si está dentro de los 16 días. */
    fun para(info: Info, momento: Long): Parte? =
        info.porHoras.minByOrNull { kotlin.math.abs(it.first - momento) }?.takeIf { kotlin.math.abs(it.first - momento) <= 90 * 60_000L }?.second

    /** Código WMO → símbolo y texto. */
    fun describir(p: Parte): Pair<String, String> = when (p.codigo) {
        0 -> (if (p.deDia) "☀" else "🌙") to "despejado"
        1 -> (if (p.deDia) "🌤" else "🌙") to "casi despejado"
        2 -> "⛅" to "nubes y claros"
        3 -> "☁" to "nublado"
        45, 48 -> "🌫" to "niebla"
        51, 53, 55 -> "🌦" to "llovizna"
        56, 57 -> "🌧" to "llovizna helada"
        61 -> "🌦" to "lluvia débil"
        63 -> "🌧" to "lluvia"
        65 -> "🌧" to "lluvia fuerte"
        66, 67 -> "🌧" to "lluvia helada"
        71 -> "🌨" to "nieve débil"
        73 -> "🌨" to "nieve"
        75 -> "❄" to "nieve fuerte"
        77 -> "🌨" to "nieve granulada"
        80, 81 -> "🌦" to "chubascos"
        82 -> "🌧" to "chubascos fuertes"
        85, 86 -> "🌨" to "chubascos de nieve"
        95 -> "⛈" to "tormenta"
        96, 99 -> "⛈" to "tormenta con granizo"
        else -> "🌡" to ""
    }

    fun grados(p: Parte) = "${Math.round(p.temperatura)} °C"

    /** "1 h menos que aquí", "misma hora que aquí", "5 h 30 min más que aquí". */
    fun diferencia(zona: ZoneId): String {
        val ahora = Instant.now()
        val seg = zona.rules.getOffset(ahora).totalSeconds - ZoneId.systemDefault().rules.getOffset(ahora).totalSeconds
        if (seg == 0) return "la misma hora que aquí"
        val min = kotlin.math.abs(seg) / 60
        val texto = if (min % 60 == 0) "${min / 60} h" else "${min / 60} h ${min % 60} min"
        return texto + if (seg > 0) " más que aquí" else " menos que aquí"
    }

    fun horaAhora(zona: ZoneId): String = ZonedDateTime.now(zona).format(DateTimeFormatter.ofPattern("HH:mm"))
}
