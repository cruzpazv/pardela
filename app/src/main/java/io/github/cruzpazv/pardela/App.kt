package io.github.cruzpazv.pardela

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import android.os.Build
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.notif.Avisos
import io.github.cruzpazv.pardela.notif.Comprobador
import io.github.cruzpazv.pardela.ui.prepararMapa
import io.github.cruzpazv.pardela.data.intentar
import io.github.cruzpazv.pardela.notif.Widget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/** Texto en el idioma del móvil: inglés por defecto, español si el móvil está en español. */
fun tx(@StringRes id: Int, vararg args: Any?): String =
    Idioma.contexto(App.contexto).let { c -> if (args.isEmpty()) c.getString(id) else c.getString(id, *args) }

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        contexto = this
        guardarFallos()
        prepararMapa(this, "Pardela/${BuildConfig.VERSION_NAME} (Android; +${BuildConfig.CONTACTO})")
        Almacen.iniciar(this)
        Aeropuertos.cargar(this)
        Avisos.crearCanales(this)
        Comprobador.programar(this)
        // El widget se redibuja cada vez que cambian los vuelos seguidos (al consultar, seguir o dejar).
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            Almacen.datos.map { it.vuelos to it.ajustes.daltonismo }.distinctUntilChanged().collect {
                intentar { Widget.actualizar(this@App) }
            }
        }
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
        /** Contexto de la aplicación, para los textos traducidos fuera de las pantallas. */
        lateinit var contexto: Context
            private set
        fun ficheroFallo(app: android.content.Context) = File(app.filesDir, "fallo.txt")
    }
}
