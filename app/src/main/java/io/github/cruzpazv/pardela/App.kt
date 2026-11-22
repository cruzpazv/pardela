package io.github.cruzpazv.pardela

import android.app.Application
import android.os.Build
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.notif.Avisos
import io.github.cruzpazv.pardela.notif.Comprobador
import io.github.cruzpazv.pardela.ui.prepararMapa
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        guardarFallos()
        prepararMapa(this, "Pardela/${BuildConfig.VERSION_NAME} (Android; +${BuildConfig.CONTACTO})")
        Almacen.iniciar(this)
        Aeropuertos.cargar(this)
        Avisos.crearCanales(this)
        Comprobador.programar(this)
    }

    /** Si la app se cierra por un error, lo apunta para mostrarlo al volver a abrirla. */
    private fun guardarFallos() {
        val previo = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, e ->
            try {
                ficheroFallo(this).writeText(
                    "Pardela ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}\n\n" +
                        e.stackTraceToString().take(12_000)
                )
            } catch (ignorado: Throwable) {}
            previo?.uncaughtException(hilo, e)
        }
    }

    companion object {
        fun ficheroFallo(app: android.content.Context) = File(app.filesDir, "fallo.txt")
    }
}
