package io.github.cruzpazv.pardela.notif

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
            NotificationChannel(CANAL_AVISOS, "Cambios en vuelos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Despegues, aterrizajes, retrasos y cambios de puerta"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_DIRECTO, "Vuelo en directo", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aviso fijo mientras vigila un vuelo"
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
        runCatching { ctx.startActivity(i) }
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
            .setColor(0xFFFFC400.toInt())
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
                    r += Cambio("✈ HA DESPEGADO" + (ahora.llegada.horaPrevista ?: ahora.llegada.hora)?.let { ". Llegada prevista $it" }.orEmpty())
                Estado.APROXIMANDO -> if (a.avisoAterrizaje) r += Cambio("▼ Está llegando a $destino")
                Estado.ATERRIZADO -> if (a.avisoAterrizaje)
                    r += Cambio("✔ HA ATERRIZADO en $destino" + ahora.llegada.cinta?.takeIf { a.avisoCinta }?.let { ". Equipaje: cinta $it" }.orEmpty())
                Estado.EMBARCANDO -> if (a.avisoEmbarque) r += Cambio("» EMBARCANDO" + ahora.salida.puerta?.let { " por la puerta $it" }.orEmpty())
                Estado.PUERTA_CERRADA -> if (a.avisoEmbarque) r += Cambio("■ Puerta cerrada")
                Estado.FACTURACION -> if (a.avisoEmbarque) r += Cambio("Facturación abierta")
                Estado.CANCELADO -> r += Cambio("✕ VUELO CANCELADO", urgente = true)
                Estado.DESVIADO -> r += Cambio("↪ VUELO DESVIADO", urgente = true)
                Estado.RETRASADO -> if (a.avisoRetraso) r += Cambio("⚠ RETRASADO" + ahora.salida.horaPrevista?.let { ". Nueva salida $it" }.orEmpty())
                else -> {}
            }
        }
        val rA = antes.salida.retraso ?: 0
        val rN = ahora.salida.retraso ?: 0
        if (a.avisoRetraso && ahora.estado != Estado.RETRASADO && ahora.estado.ordinal < Estado.EN_VUELO.ordinal && kotlin.math.abs(rN - rA) >= 10) {
            r += Cambio(if (rN > 0) "⚠ Retraso de $rN min. Nueva salida ${ahora.salida.horaPrevista}" else "✔ Sale a su hora: ${ahora.salida.horaPrevista ?: ahora.salida.hora}")
        }
        if (a.avisoPuerta && ahora.salida.puerta != null && ahora.salida.puerta != antes.salida.puerta && ahora.estado != Estado.EMBARCANDO)
            r += Cambio("Puerta de embarque: ${ahora.salida.puerta}")
        if (a.avisoCinta && ahora.llegada.cinta != null && ahora.llegada.cinta != antes.llegada.cinta && ahora.estado != Estado.ATERRIZADO)
            r += Cambio("Equipaje: cinta ${ahora.llegada.cinta}")
        return r
    }
}
