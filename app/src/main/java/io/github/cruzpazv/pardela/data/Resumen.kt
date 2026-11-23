package io.github.cruzpazv.pardela.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos de un vuelo para compartir, leer en voz alta o guardar en el calendario. */
object Resumen {
    private val es = Locale.forLanguageTag("es-ES")
    private val diaLargo = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)

    private fun lugar(l: Lugar) = when {
        l.ciudad.isNotBlank() && l.iata.isNotBlank() -> "${l.ciudad} (${l.iata})"
        else -> l.ciudad.ifBlank { l.iata.ifBlank { "?" } }
    }

    fun dia(v: Vuelo): String? = v.fecha?.let { runCatching { LocalDate.parse(it).format(diaLargo) }.getOrNull() }

    private fun hora(e: Extremo): String? {
        val prevista = e.horaPrevista?.takeIf { it != e.hora }
        return when {
            e.hora != null && prevista != null -> "${e.hora} (ahora prevista a las $prevista)"
            else -> e.hora ?: e.horaPrevista
        }
    }

    /** Texto para compartir por WhatsApp, correo… */
    fun compartir(v: Vuelo): String = buildList {
        add("✈ Vuelo ${v.numero}" + (v.aerolineaNombre?.let { " · $it" } ?: ""))
        dia(v)?.let { add(it.replaceFirstChar { c -> c.titlecase(es) }) }
        add("${lugar(v.salida.lugar)} → ${lugar(v.llegada.lugar)}")
        hora(v.salida)?.let { h -> add("Sale: $h" + listOfNotNull(v.salida.terminal?.let { "terminal $it" }, v.salida.puerta?.let { "puerta $it" }).joinToString("") { " · $it" }) }
        hora(v.llegada)?.let { h -> add("Llega: $h" + (v.llegada.cinta?.let { " · cinta $it" } ?: "")) }
        if (v.estado != Estado.DESCONOCIDO) add("Estado: ${v.estado.etiqueta.lowercase(es).replaceFirstChar { it.titlecase(es) }}")
        add("https://www.flightradar24.com/data/flights/${v.numero.lowercase()}")
    }.joinToString("\n")

    /** Frase para leer en voz alta, clara y sin abreviaturas. */
    fun voz(v: Vuelo): String {
        // "IB3166" → "I B 3166": la voz lee las letras una a una.
        val numero = Aerolineas.partir(v.numero)?.let { (p, n) -> p.toList().joinToString(" ") + " " + n } ?: v.numero
        val de = v.salida.lugar.ciudad.ifBlank { v.salida.lugar.iata }
        val a = v.llegada.lugar.ciudad.ifBlank { v.llegada.lugar.iata }
        val partes = mutableListOf("Vuelo $numero" + (v.aerolineaNombre?.let { " de $it" } ?: "") + ", de $de a $a.")
        val s = v.salida; val l = v.llegada
        when (v.estado) {
            Estado.ATERRIZADO -> {
                partes += "Ha aterrizado" + ((l.horaPrevista ?: l.hora)?.let { " a las $it" } ?: "") + "."
                l.cinta?.let { partes += "Las maletas salen por la cinta $it." }
            }
            Estado.CANCELADO -> partes += "El vuelo está cancelado."
            Estado.DESVIADO -> partes += "El vuelo ha sido desviado a otro aeropuerto."
            Estado.EN_VUELO, Estado.APROXIMANDO -> {
                partes += if (v.estado == Estado.APROXIMANDO) "Está a punto de aterrizar." else "Está en el aire."
                (l.horaPrevista ?: l.hora)?.let { partes += "Llegada prevista a las $it." }
            }
            else -> {
                if (v.estado != Estado.DESCONOCIDO) partes += "Estado: ${v.estado.texto.lowercase(es)}."
                (s.horaPrevista ?: s.hora)?.let { partes += "Sale a las $it" + (s.puerta?.let { p -> " por la puerta $p" } ?: "") + "." }
                s.retraso?.takeIf { it >= 5 }?.let { partes += "Lleva $it minutos de retraso." }
                (l.horaPrevista ?: l.hora)?.let { partes += "Llega a las $it." }
            }
        }
        dia(v)?.takeIf { v.fecha != LocalDate.now().toString() }?.let { partes += "Es el $it." }
        return partes.joinToString(" ")
    }
}
