package io.github.cruzpazv.pardela.data

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos de un vuelo para compartir, leer en voz alta o guardar en el calendario. */
object Resumen {
    private val es get() = io.github.cruzpazv.pardela.Idioma.locale()
    private val diaLargo get() = DateTimeFormatter.ofPattern(tx(R.string.formato_dia), es)

    private fun lugar(l: Lugar) = when {
        l.ciudad.isNotBlank() && l.iata.isNotBlank() -> "${l.ciudad} (${l.iata})"
        else -> l.ciudad.ifBlank { l.iata.ifBlank { "?" } }
    }

    fun dia(v: Vuelo): String? = v.fecha?.let { runCatching { LocalDate.parse(it).format(diaLargo) }.getOrNull() }

    private fun hora(e: Extremo): String? {
        val prevista = e.horaPrevista?.takeIf { it != e.hora }
        return when {
            e.hora != null && prevista != null -> tx(R.string.x_ahora_prevista_a_las_x, e.hora, prevista)
            else -> e.hora ?: e.horaPrevista
        }
    }

    /** Texto para compartir por WhatsApp, correo… */
    fun compartir(v: Vuelo): String = buildList {
        add(tx(R.string.vuelo_x_3, v.numero) + (v.aerolineaNombre?.let { " · $it" } ?: ""))
        dia(v)?.let { add(it.replaceFirstChar { c -> c.titlecase(es) }) }
        add("${lugar(v.salida.lugar)} → ${lugar(v.llegada.lugar)}")
        hora(v.salida)?.let { h -> add(tx(R.string.sale_x_2, h) + listOfNotNull(v.salida.terminal?.let { tx(R.string.terminal_x, it) }, v.salida.puerta?.let { tx(R.string.puerta_x_2, it) }).joinToString("") { " · $it" }) }
        hora(v.llegada)?.let { h -> add(tx(R.string.llega_x_2, h) + (v.llegada.cinta?.let { tx(R.string.cinta_x, it) } ?: "")) }
        if (v.estado != Estado.DESCONOCIDO) add(tx(R.string.estado_x, v.estado.etiqueta.lowercase(es).replaceFirstChar { it.titlecase(es) }))
        add("https://www.flightradar24.com/data/flights/${v.numero.lowercase()}")
    }.joinToString("\n")

    /** Frase para leer en voz alta, clara y sin abreviaturas. */
    fun voz(v: Vuelo): String {
        // "IB3166" → "I B 3166": la voz lee las letras una a una.
        val numero = Aerolineas.partir(v.numero)?.let { (p, n) -> p.toList().joinToString(" ") + " " + n } ?: v.numero
        val de = v.salida.lugar.ciudad.ifBlank { v.salida.lugar.iata }
        val a = v.llegada.lugar.ciudad.ifBlank { v.llegada.lugar.iata }
        val partes = mutableListOf(tx(R.string.vuelo_x, numero) + (v.aerolineaNombre?.let { tx(R.string.de_aerolinea_x, it) } ?: "") + tx(R.string.de_x_a_x, de, a))
        val s = v.salida; val l = v.llegada
        when (v.estado) {
            Estado.ATERRIZADO -> {
                partes += tx(R.string.ha_aterrizado) + ((l.horaPrevista ?: l.hora)?.let { tx(R.string.a_las_x, it) } ?: "") + "."
                l.cinta?.let { partes += tx(R.string.las_maletas_salen_por_la_cinta_x, it) }
            }
            Estado.CANCELADO -> partes += tx(R.string.el_vuelo_esta_cancelado)
            Estado.DESVIADO -> partes += tx(R.string.el_vuelo_ha_sido_desviado_a_otro_aerop)
            Estado.EN_VUELO, Estado.APROXIMANDO -> {
                partes += if (v.estado == Estado.APROXIMANDO) tx(R.string.esta_a_punto_de_aterrizar) else tx(R.string.esta_en_el_aire)
                (l.horaPrevista ?: l.hora)?.let { partes += tx(R.string.llegada_prevista_a_las_x, it) }
            }
            else -> {
                if (v.estado != Estado.DESCONOCIDO) partes += tx(R.string.estado_x_2, v.estado.texto.lowercase(es))
                (s.horaPrevista ?: s.hora)?.let { partes += tx(R.string.sale_a_las_x, it) + (s.puerta?.let { p -> tx(R.string.por_la_puerta_x, p) } ?: "") + "." }
                s.retraso?.takeIf { it >= 5 }?.let { partes += tx(R.string.lleva_x_minutos_de_retraso, it) }
                (l.horaPrevista ?: l.hora)?.let { partes += tx(R.string.llega_a_las_x_2, it) }
            }
        }
        dia(v)?.takeIf { v.fecha != LocalDate.now().toString() }?.let { partes += tx(R.string.es_el_x, it) }
        return partes.joinToString(" ")
    }
}
