package io.github.cruzpazv.pardela.notif

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Resumen
import io.github.cruzpazv.pardela.tx
import io.github.cruzpazv.pardela.ui.Voz

/** Botón ESCUCHAR de los avisos: lee el vuelo en voz alta sin abrir la app y se cierra al terminar. */
class LecturaService : Service() {
    private var voz: Voz? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Almacen.iniciar(this)
        val v = intent?.getStringExtra(EXTRA)?.let { Avisos.vueloDe(it) } ?: run { stopSelf(); return START_NOT_STICKY }
        voz?.cerrar()
        voz = Voz(this) { falta ->
            // Puede llegar desde otro hilo: el aviso y el cierre, en el principal.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (falta != null) Toast.makeText(this, tx(R.string.falta_voz_x, falta.getDisplayLanguage(falta)), Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }.also { it.decir(Resumen.voz(v)) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        voz?.cerrar()
        super.onDestroy()
    }

    companion object { const val EXTRA = "vuelo" }
}
