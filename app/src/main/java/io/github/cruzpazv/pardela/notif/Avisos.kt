package io.github.cruzpazv.pardela.notif

import io.github.cruzpazv.pardela.tx

import io.github.cruzpazv.pardela.data.intentar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.cruzpazv.pardela.MainActivity
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Ajustes
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Estado
import io.github.cruzpazv.pardela.data.Vuelo
import java.time.LocalTime

object Avisos {
    const val CANAL_AVISOS = "avisos"
    const val CANAL_DIRECTO = "directo"

    /** Un cambio que contar. [urgente] = suena aunque sea hora de silencio (cancelación, desvío). */
    data class Cambio(val texto: String, val urgente: Boolean = false)

    fun crearCanales(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_AVISOS, tx(R.string.cambios_en_vuelos), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tx(R.string.despegues_aterrizajes_retrasos_y_cambi)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_DIRECTO, tx(R.string.vuelo_en_directo), NotificationManager.IMPORTANCE_LOW).apply {
                description = tx(R.string.aviso_fijo_mientras_vigila_un_vuelo)
                setShowBadge(false)
            }
        )
    }

    fun abrirApp(ctx: Context, clave: String? = null): PendingIntent = PendingIntent.getActivity(
        ctx, clave.hashCode(),
        Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            clave?.let { putExtra(MainActivity.EXTRA_VUELO, it) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun puede(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Pantalla del sistema con los permisos de avisos de esta app. */
    fun abrirAjustesDelMovil(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intentar { ctx.startActivity(i) }
    }

    /** ¿Estamos dentro de las horas de silencio? Admite rangos que cruzan medianoche (22 → 8). */
    fun enSilencio(a: Ajustes, ahora: LocalTime = LocalTime.now()): Boolean {
        if (!a.silencio || a.silencioDesde == a.silencioHasta) return false
        val h = ahora.hour
        return if (a.silencioDesde < a.silencioHasta) h >= a.silencioDesde && h < a.silencioHasta
        else h >= a.silencioDesde || h < a.silencioHasta
    }

    fun avisar(ctx: Context, v: Vuelo, cambios: List<Cambio>) {
        if (cambios.isEmpty() || !puede(ctx)) return
        val callado = enSilencio(Almacen.ajustes) && cambios.none { it.urgente }
        val textos = cambios.map { it.texto }
        val n = NotificationCompat.Builder(ctx, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_pardela)
            .setContentTitle("${v.numero}  ${v.ruta}")
            .setContentText(textos.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(textos.joinToString("\n")))
            .setPriority(if (callado) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setSilent(callado)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(0xFFE5B45C.toInt())
            .setAutoCancel(true)
            .setContentIntent(abrirApp(ctx, v.clave))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(v.clave.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    /** Qué ha cambiado entre dos lecturas del mismo vuelo, filtrado por los avisos que quiere recibir. */
    fun cambios(antes: Vuelo?, ahora: Vuelo, a: Ajustes = Almacen.ajustes): List<Cambio> {
        if (antes == null) return emptyList()
        val r = mutableListOf<Cambio>()
        val destino = ahora.llegada.lugar.ciudad.ifBlank { ahora.llegada.lugar.iata }
        if (ahora.estado != antes.estado && ahora.estado != Estado.DESCONOCIDO) {
            when (ahora.estado) {
                Estado.EN_VUELO -> if (a.avisoDespegue)
                    r += Cambio(tx(R.string.ha_despegado) + (ahora.llegada.horaPrevista ?: ahora.llegada.hora)?.let { tx(R.string.llegada_prevista_x, it) }.orEmpty())
                Estado.APROXIMANDO -> if (a.avisoAterrizaje) r += Cambio(tx(R.string.esta_llegando_a_x, destino))
                Estado.ATERRIZADO -> if (a.avisoAterrizaje)
                    r += Cambio(tx(R.string.ha_aterrizado_en_x, destino) + ahora.llegada.cinta?.takeIf { a.avisoCinta }?.let { tx(R.string.equipaje_cinta_x, it) }.orEmpty())
                Estado.EMBARCANDO -> if (a.avisoEmbarque) r += Cambio(tx(R.string.embarcando) + ahora.salida.puerta?.let { tx(R.string.por_la_puerta_x, it) }.orEmpty())
                Estado.PUERTA_CERRADA -> if (a.avisoEmbarque) r += Cambio(tx(R.string.puerta_cerrada))
                Estado.FACTURACION -> if (a.avisoEmbarque) r += Cambio(tx(R.string.facturacion_abierta))
                Estado.CANCELADO -> r += Cambio(tx(R.string.vuelo_cancelado), urgente = true)
                Estado.DESVIADO -> r += Cambio(tx(R.string.vuelo_desviado), urgente = true)
                Estado.RETRASADO -> if (a.avisoRetraso) r += Cambio(tx(R.string.retrasado) + ahora.salida.horaPrevista?.let { tx(R.string.nueva_salida_x, it) }.orEmpty())
                else -> {}
            }
        }
        val rA = antes.salida.retraso ?: 0
        val rN = ahora.salida.retraso ?: 0
        if (a.avisoRetraso && ahora.estado != Estado.RETRASADO && ahora.estado.ordinal < Estado.EN_VUELO.ordinal && kotlin.math.abs(rN - rA) >= 10) {
            r += Cambio(if (rN > 0) tx(R.string.retraso_de_x_min_nueva_salida_x, rN, ahora.salida.horaPrevista) else tx(R.string.sale_a_su_hora_x, ahora.salida.horaPrevista ?: ahora.salida.hora))
        }
        if (a.avisoPuerta && ahora.salida.puerta != null && ahora.salida.puerta != antes.salida.puerta && ahora.estado != Estado.EMBARCANDO)
            r += Cambio(tx(R.string.puerta_de_embarque_x, ahora.salida.puerta))
        if (a.avisoCinta && ahora.llegada.cinta != null && ahora.llegada.cinta != antes.llegada.cinta && ahora.estado != Estado.ATERRIZADO)
            r += Cambio(tx(R.string.equipaje_cinta_x_2, ahora.llegada.cinta))
        return r
    }
}
