package io.github.cruzpazv.pardela.notif

import io.github.cruzpazv.pardela.tx

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Estado
import io.github.cruzpazv.pardela.data.Vuelo
import java.time.LocalDate

/** Widget "Próximo vuelo" para la pantalla de inicio del móvil. */
class WidgetVuelo : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gestor: AppWidgetManager, ids: IntArray) {
        Almacen.iniciar(ctx)
        Widget.actualizar(ctx)
    }
}

/** Versión pequeña (2×1): lo esencial en dos líneas. */
class WidgetVueloMini : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gestor: AppWidgetManager, ids: IntArray) {
        Almacen.iniciar(ctx)
        Widget.actualizar(ctx)
    }
}

object Widget {
    /**
     * Vuelo a mostrar: el que está en marcha o el próximo en salir. Uno aterrizado sigue
     * 2 h más en pantalla (útil para ir a recoger a alguien: muestra la cinta).
     */
    fun elegir(vuelos: List<Vuelo>, ahora: Long = System.currentTimeMillis()): Vuelo? {
        val hoy = LocalDate.now().toString()
        return vuelos.filter { v ->
            val llega = v.llegada.utcPrevista ?: v.llegada.utc
            when {
                v.estado == Estado.CANCELADO -> (v.fecha ?: hoy) >= hoy
                v.estado.terminado -> llega != null && ahora - llega < 2 * 3_600_000L
                else -> (v.fecha ?: hoy) >= LocalDate.now().minusDays(1).toString()
            }
        }.minByOrNull { it.salida.utcPrevista ?: it.salida.utc ?: Long.MAX_VALUE }
    }

    fun actualizar(ctx: Context) {
        val gestor = AppWidgetManager.getInstance(ctx)
        val d = Almacen.datos.value
        val v = elegir(d.vuelos.mapNotNull { it.ultimo })
        actualizarMini(ctx, gestor, v, d.ajustes.daltonismo)
        val ids = gestor.getAppWidgetIds(ComponentName(ctx, WidgetVuelo::class.java))
        if (ids.isEmpty()) return
        val r = RemoteViews(ctx.packageName, R.layout.widget_vuelo)
        if (v == null) {
            r.setTextViewText(R.id.numero, "PARDELA")
            r.setTextViewText(R.id.dia, "")
            r.setTextViewText(R.id.ruta, tx(R.string.sin_vuelos_proximos))
            r.setTextViewText(R.id.estado, "")
            r.setTextViewText(R.id.horas, tx(R.string.toque_aqui_para_buscar_un_vuelo_y_puls))
            r.setOnClickPendingIntent(R.id.widget, Avisos.abrirApp(ctx))
        } else {
            r.setTextViewText(R.id.numero, v.numero)
            r.setTextViewText(R.id.dia, dia(v.fecha))
            r.setTextViewText(R.id.ruta, "${nombre(v.salida.lugar.ciudad, v.salida.lugar.iata)} → ${nombre(v.llegada.lugar.ciudad, v.llegada.lugar.iata)}")
            r.setTextViewText(R.id.estado, if (v.estado == Estado.DESCONOCIDO) "" else v.estado.etiqueta)
            r.setTextColor(R.id.estado, color(v.estado, d.ajustes.daltonismo))
            r.setTextViewText(R.id.horas, horas(v))
            r.setOnClickPendingIntent(R.id.widget, Avisos.abrirApp(ctx, v.clave))
        }
        gestor.updateAppWidget(ids, r)
    }

    private fun actualizarMini(ctx: Context, gestor: AppWidgetManager, v: Vuelo?, daltonismo: Boolean) {
        val ids = gestor.getAppWidgetIds(ComponentName(ctx, WidgetVueloMini::class.java))
        if (ids.isEmpty()) return
        val r = RemoteViews(ctx.packageName, R.layout.widget_vuelo_mini)
        if (v == null) {
            r.setTextViewText(R.id.linea1, "PARDELA")
            r.setTextViewText(R.id.linea2, tx(R.string.sin_vuelos_proximos))
            r.setTextColor(R.id.linea2, 0xFFA6A6A6.toInt())
            r.setOnClickPendingIntent(R.id.widget, Avisos.abrirApp(ctx))
        } else {
            // "IB3166  LPA→MAD" / "✈ EN VUELO · 13:45" (con el día delante si no es hoy).
            val ruta = "${v.salida.lugar.iata.ifBlank { "?" }}→${v.llegada.lugar.iata.ifBlank { "?" }}"
            r.setTextViewText(R.id.linea1, "${v.numero}  $ruta")
            val enMarcha = v.estado == Estado.EN_VUELO || v.estado == Estado.APROXIMANDO || v.estado == Estado.ATERRIZADO
            val e = if (enMarcha) v.llegada else v.salida
            val hora = e.horaPrevista ?: e.hora
            val dia = dia(v.fecha).takeIf { it.isNotEmpty() && v.fecha != LocalDate.now().toString() }
            val cuando = listOfNotNull(dia, hora).joinToString(" ")
            val estado = if (v.estado == Estado.DESCONOCIDO) "" else v.estado.etiqueta
            r.setTextViewText(R.id.linea2, listOf(estado, cuando).filter { it.isNotEmpty() }.joinToString(" · "))
            r.setTextColor(R.id.linea2, if (v.estado == Estado.DESCONOCIDO) 0xFFF2F2F2.toInt() else color(v.estado, daltonismo))
            r.setOnClickPendingIntent(R.id.widget, Avisos.abrirApp(ctx, v.clave))
        }
        gestor.updateAppWidget(ids, r)
    }

    private fun nombre(ciudad: String, iata: String) = ciudad.ifBlank { iata.ifBlank { "?" } }

    private fun dia(fecha: String?): String {
        val f = fecha ?: return ""
        val hoy = LocalDate.now()
        return when (f) {
            hoy.toString() -> tx(R.string.hoy)
            hoy.plusDays(1).toString() -> tx(R.string.manana)
            hoy.minusDays(1).toString() -> tx(R.string.ayer)
            else -> f.takeLast(5).split("-").reversed().joinToString("/")
        }
    }

    private fun horas(v: Vuelo): String {
        val s = v.salida; val l = v.llegada
        return when (v.estado) {
            Estado.ATERRIZADO -> tx(R.string.llego_a_las_x, l.horaPrevista ?: l.hora ?: "?") + (l.cinta?.let { tx(R.string.cinta_x, it) } ?: "")
            Estado.EN_VUELO, Estado.APROXIMANDO -> tx(R.string.llega_a_las_x, l.horaPrevista ?: l.hora ?: "?") + (l.cinta?.let { tx(R.string.cinta_x, it) } ?: "")
            else -> listOfNotNull(
                (s.horaPrevista ?: s.hora)?.let { tx(R.string.sale_x, it) },
                s.puerta?.let { tx(R.string.puerta_x_2, it) },
                (l.horaPrevista ?: l.hora)?.let { tx(R.string.llega_x, it) },
            ).joinToString(" · ")
        }
    }

    /** Colores de la paleta oscura (o la de daltonismo): el widget siempre es oscuro, como el tablón. */
    private fun color(e: Estado, daltonismo: Boolean): Int = when (e) {
        Estado.ATERRIZADO -> if (daltonismo) 0xFF56B4E9 else 0xFF39D98A
        Estado.CANCELADO, Estado.DESVIADO, Estado.RETRASADO -> if (daltonismo) 0xFFFF8C1A else 0xFFFF5A4E
        Estado.EN_VUELO, Estado.APROXIMANDO -> if (daltonismo) 0xFFF2F2F2 else 0xFF5AB4FF
        Estado.EMBARCANDO, Estado.PUERTA_CERRADA, Estado.FACTURACION -> 0xFFE5B45C
        else -> 0xFFA6A6A6
    }.toInt()
}
