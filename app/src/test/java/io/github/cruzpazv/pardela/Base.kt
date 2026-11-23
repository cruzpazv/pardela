package io.github.cruzpazv.pardela

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Datos
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base de las pruebas: Android simulado (Robolectric) con una Application vacía
 * (sin WorkManager ni canales), textos en español y datos limpios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
abstract class Base {
    @Before
    fun preparar() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        App.contexto = ctx
        Almacen.iniciar(ctx)
        Almacen.cambiar { Datos() }
        Aeropuertos.cargar(ctx)
        Idioma.fijarParaPruebas("es")
    }

    protected fun enIngles() = Idioma.fijarParaPruebas("en")
}
