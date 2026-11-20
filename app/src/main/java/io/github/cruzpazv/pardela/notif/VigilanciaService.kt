package io.github.cruzpazv.pardela.notif

import io.github.cruzpazv.pardela.data.intentar

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Estado
import io.github.cruzpazv.pardela.data.Formato
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.data.reciente
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** "Vigilar ahora": aviso fijo que se actualiza cada minuto con el vuelo que está mirando. */
class VigilanciaService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tarea: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PARAR) { parar(); return START_NOT_STICKY }
        val v = intent?.getStringExtra(EXTRA)?.let { intentar { json.decodeFromString(Vuelo.serializer(), it) }.getOrNull() }
            ?: run { stopSelf(); return START_NOT_STICKY }
        ServiceCompat.startForeground(
            this, ID, aviso(v),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        _vigilando.value = v.clave
        tarea?.cancel()
        tarea = scope.launch { bucle(v) }
        return START_NOT_STICKY
    }

    private suspend fun bucle(inicial: Vuelo) {
        var v = inicial
        var ultimaClave = 0L
        val fin = System.currentTimeMillis() + 8 * 3600_000L
        while (scope.isActive && System.currentTimeMillis() < fin) {
            val ahora = System.currentTimeMillis()
            val usarClave = Almacen.tieneClave && v.conHorarios && ahora - ultimaClave > 15 * 60_000L
            val n = intentar { Repositorio.refrescar(v, usarClave) }.getOrNull()
            if (n != null) {
                if (usarClave) ultimaClave = ahora
                Avisos.avisar(this, n, Avisos.cambios(v, n))
                if (Almacen.sigue(n)) Almacen.actualizar(n)
                v = n
                _ultimo.value = n
                _vigilando.value = n.clave // la clave cambia si se descubre el aeropuerto de origen
                ContextCompat.getSystemService(this, android.app.NotificationManager::class.java)?.notify(ID, aviso(n))
                if (n.estado.terminado) break
            }
            delay(60_000)
        }
        parar()
    }

    private fun aviso(v: Vuelo) = NotificationCompat.Builder(this, Avisos.CANAL_DIRECTO)
        .setSmallIcon(R.drawable.ic_pardela)
        .setContentTitle("${v.numero}   ${v.ruta}")
        .setContentText(linea(v))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setColor(0xFFE5B45C.toInt())
        .setContentIntent(Avisos.abrirApp(this, v.clave))
        .apply { Repositorio.progreso(v)?.let { setProgress(100, (it * 100).toInt(), false) } }
        .addAction(0, "DEJAR DE VIGILAR", PendingIntent.getService(
            this, 1, Intent(this, VigilanciaService::class.java).setAction(PARAR), PendingIntent.FLAG_IMMUTABLE,
        ))
        .build()

    private fun linea(v: Vuelo): String {
        val p = mutableListOf(v.estado.texto)
        v.directo?.takeIf { !it.enTierra && it.reciente }?.let { d ->
            d.altitudPies?.let { p += Formato.altura(it, Almacen.ajustes.unidades) }
            d.velocidadNudos?.let { p += Formato.velocidad(it, Almacen.ajustes.unidades) }
        }
        Repositorio.minutosRestantes(v)?.takeIf { v.estado == Estado.EN_VUELO || v.estado == Estado.APROXIMANDO }
            ?.let { p += "llega en ${if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min"}" }
        return p.joinToString(" · ")
    }

    private fun parar() {
        _vigilando.value = null
        tarea?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int) = parar()

    /** Android 15 limita estos servicios a 6 h al día: al llegar al límite se para limpiamente. */
    override fun onTimeout(startId: Int, fgsType: Int) = parar()

    override fun onDestroy() {
        _vigilando.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ID = 7001
        private const val EXTRA = "vuelo"
        private const val PARAR = "parar"
        private val json = Json { ignoreUnknownKeys = true }
        private val _vigilando = MutableStateFlow<String?>(null)
        private val _ultimo = MutableStateFlow<Vuelo?>(null)
        /** Clave del vuelo vigilado ahora mismo (o null). */
        val vigilando: StateFlow<String?> = _vigilando
        val ultimo: StateFlow<Vuelo?> = _ultimo

        fun empezar(ctx: Context, v: Vuelo) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, VigilanciaService::class.java).putExtra(EXTRA, json.encodeToString(Vuelo.serializer(), v)),
            )
        }

        fun parar(ctx: Context) {
            ctx.startService(Intent(ctx, VigilanciaService::class.java).setAction(PARAR))
        }
    }
}
