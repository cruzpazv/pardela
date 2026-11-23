package io.github.cruzpazv.pardela.notif

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

import io.github.cruzpazv.pardela.data.intentar

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Trayecto
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.data.VueloSeguido
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Cada 15 min (mínimo de Android) revisa vuelos seguidos y trayectos.
 * La clave API se gasta con cuidado: plan gratuito ≈ 600 consultas/mes.
 */
class Comprobador(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {

    override suspend fun doWork(): Result {
        Almacen.iniciar(applicationContext)
        Aeropuertos.cargar(applicationContext)
        val ahora = System.currentTimeMillis()

        for (s in Almacen.datos.value.vuelos) {
            val v = s.ultimo ?: continue
            if (v.estado.terminado) continue
            recordar(s, v, ahora)
            // Vuelos de días pasados: ya no se consultan (se pueden quitar a mano).
            val dia = v.fecha?.let { intentar { LocalDate.parse(it) }.getOrNull() }
            if (dia != null && dia.isBefore(LocalDate.now().minusDays(1))) continue
            val usarClave = Almacen.tieneClave && v.conHorarios && ahora - s.ultimaConsultaClave > intervaloClave(v, ahora)
            // Vuelo de otro día y sin tocar consulta con clave: no hay nada que mirar.
            if (!usarClave && !Repositorio.radarUtil(v, ahora)) continue
            val nuevo = intentar { Repositorio.refrescar(v, usarClave) }.getOrNull() ?: continue
            Avisos.avisar(applicationContext, nuevo, Avisos.cambios(v, nuevo))
            Almacen.cambiar { d ->
                d.copy(vuelos = d.vuelos.map {
                    if (it.clave == s.clave) it.copy(ultimo = nuevo, ultimaConsultaClave = if (usarClave) ahora else it.ultimaConsultaClave) else it
                })
            }
        }

        for (t in Almacen.datos.value.trayectos) {
            // Con clave: cada 3 h (1 consulta). Sin clave: radar en cada pasada (gratis).
            if (Almacen.tieneClave && ahora - t.ultimaConsulta < TimeUnit.HOURS.toMillis(3)) continue
            val vuelos = intentar { vuelosTrayecto(t) }.getOrNull() ?: continue
            val nuevos = HashMap<String, Vuelo>()
            for (v in vuelos) {
                val antes = t.vistos[v.numero]
                val textos = if (antes == null) {
                    if (t.ultimaConsulta == 0L) emptyList() // primera vez: solo memoriza
                    else if (v.conHorarios) listOf(Avisos.Cambio(tx(R.string.nuevo_vuelo_en_su_trayecto_sale_x, v.salida.hora ?: "hoy")))
                    else listOf(Avisos.Cambio(tx(R.string.en_el_aire_x, v.estado.texto.lowercase())))
                } else Avisos.cambios(antes, v)
                Avisos.avisar(applicationContext, v, textos)
                nuevos[v.numero] = v
            }
            // Los vuelos de radar desaparecen rápido: se recuerdan 12 h.
            val recientes = t.vistos.filterValues { (it.directo?.momento ?: 0L) > ahora - TimeUnit.HOURS.toMillis(12) && !it.conHorarios }
            Almacen.cambiar { d ->
                d.copy(trayectos = d.trayectos.map {
                    if (it.clave == t.clave) it.copy(vistos = recientes + nuevos, ultimaConsulta = ahora) else it
                })
            }
        }
        return Result.success()
    }

    private suspend fun vuelosTrayecto(t: Trayecto): List<Vuelo> {
        val salidas = Repositorio.tablon(t.origen, salidas = true).filter { it.llegada.lugar.iata == t.destino }
        if (Almacen.tieneClave) return salidas
        val llegadas = Repositorio.tablon(t.destino, salidas = false).filter { it.salida.lugar.iata == t.origen }
        return (salidas + llegadas).distinctBy { it.numero }
    }

    /** Recordatorio antes de salir (una vez por vuelo). El comprobador pasa cada 15 min: llega con ese margen. */
    private fun recordar(s: VueloSeguido, v: Vuelo, ahora: Long) {
        val horas = Almacen.ajustes.recordatorio
        if (horas <= 0 || s.recordado) return
        val sale = v.salida.utcPrevista ?: v.salida.utc ?: return
        val falta = sale - ahora
        if (falta <= 0 || falta > horas * 3_600_000L) return
        val min = falta / 60_000
        val dentro = when {
            min < 60 -> tx(R.string.x_min, min)
            min % 60 < 5 -> tx(R.string.x_h_2, min / 60)
            else -> tx(R.string.x_h_x_min, min / 60, min % 60)
        }
        val hora = v.salida.horaPrevista ?: v.salida.hora
        val donde = listOfNotNull(v.salida.terminal?.let { tx(R.string.terminal_x, it) }, v.salida.puerta?.let { tx(R.string.puerta_x_2, it) }).joinToString(", ")
        val texto = tx(R.string.recordatorio_su_vuelo_sale) + (hora?.let { tx(R.string.a_las_x, it) } ?: "") + tx(R.string.dentro_de_x, dentro) +
            (if (donde.isNotEmpty()) tx(R.string.salida_por_x, donde) else "")
        Avisos.avisar(applicationContext, v, listOf(Avisos.Cambio(texto)))
        Almacen.cambiar { d -> d.copy(vuelos = d.vuelos.map { if (it.clave == s.clave) it.copy(recordado = true) else it }) }
    }

    /** Cerca de la salida o en vuelo: cada 30 min. Si falta mucho: cada 3 h. */
    private fun intervaloClave(v: Vuelo, ahora: Long): Long {
        val sale = v.salida.utcPrevista ?: v.salida.utc ?: return TimeUnit.HOURS.toMillis(3)
        val llega = v.llegada.utcPrevista ?: v.llegada.utc ?: (sale + TimeUnit.HOURS.toMillis(3))
        val activo = ahora > sale - TimeUnit.HOURS.toMillis(4) && ahora < llega + TimeUnit.HOURS.toMillis(1)
        val base = when {
            activo -> TimeUnit.MINUTES.toMillis(30)
            // Vuelos lejanos (p. ej. dentro de un mes): una consulta al día basta para ver cambios de horario.
            sale - ahora > TimeUnit.DAYS.toMillis(2) -> TimeUnit.HOURS.toMillis(24)
            else -> TimeUnit.HOURS.toMillis(3)
        }
        // Con pocas consultas en el mes: cuatro veces más espaciadas; el radar gratuito cubre el resto.
        return if (Almacen.cuotaBaja) base * 4 else base
    }

    companion object {
        fun programar(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<Comprobador>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("comprobar", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
