package io.github.cruzpazv.pardela

import android.app.Application
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.notif.Avisos
import io.github.cruzpazv.pardela.notif.Comprobador

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Almacen.iniciar(this)
        Aeropuertos.cargar(this)
        Avisos.crearCanales(this)
        Comprobador.programar(this)
    }
}
