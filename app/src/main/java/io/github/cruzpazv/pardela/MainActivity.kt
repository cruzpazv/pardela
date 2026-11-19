package io.github.cruzpazv.pardela

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import io.github.cruzpazv.pardela.data.Tema
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.notif.Avisos
import io.github.cruzpazv.pardela.notif.VigilanciaService
import io.github.cruzpazv.pardela.ui.AjustesPantalla
import io.github.cruzpazv.pardela.ui.Bienvenida
import io.github.cruzpazv.pardela.ui.BuscarAeropuerto
import io.github.cruzpazv.pardela.ui.BuscarVuelo
import io.github.cruzpazv.pardela.ui.C
import io.github.cruzpazv.pardela.ui.Detalle
import io.github.cruzpazv.pardela.ui.Inicio
import io.github.cruzpazv.pardela.ui.Pantalla
import io.github.cruzpazv.pardela.ui.TemaTablon
import io.github.cruzpazv.pardela.ui.Tablon

class MainActivity : ComponentActivity() {
    private val pila = mutableStateListOf<Pantalla>(Pantalla.Inicio)
    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        abrirDesdeAviso(intent)

        setContent {
            val datos by Almacen.datos.collectAsState()
            val sistemaOscuro = isSystemInDarkTheme()
            val oscuro = when (datos.ajustes.tema) { Tema.AUTO -> sistemaOscuro; Tema.CLARO -> false; Tema.OSCURO -> true }
            LaunchedEffect(oscuro) {
                val barras = if (oscuro) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(barras, barras)
            }
            // El permiso de avisos se pide al terminar la bienvenida, no encima de ella.
            LaunchedEffect(datos.ajustes.bienvenidaVista) {
                if (datos.ajustes.bienvenidaVista && Build.VERSION.SDK_INT >= 33 && !Avisos.puede(this@MainActivity))
                    pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            TemaTablon(datos.ajustes.textoGrande, oscuro, datos.ajustes.animaciones, datos.ajustes.daltonismo) {
                val actual = pila.last()
                BackHandler(enabled = pila.size > 1) { pila.removeAt(pila.lastIndex) }
                val ir: (Pantalla) -> Unit = { pila.add(it) }
                val atras: () -> Unit = { if (pila.size > 1) pila.removeAt(pila.lastIndex) }
                Box(Modifier.fillMaxSize().background(C.fondo).safeDrawingPadding()) {
                    if (!datos.ajustes.bienvenidaVista) Bienvenida() else when (actual) {
                        Pantalla.Inicio -> Inicio(ir)
                        Pantalla.BuscarVuelo -> BuscarVuelo(atras) { v -> pila.add(Pantalla.Detalle(v)) }
                        is Pantalla.BuscarAeropuerto -> BuscarAeropuerto(actual.origen, atras) { a ->
                            when {
                                actual.origen == null -> { pila.removeAt(pila.lastIndex); pila.add(Pantalla.Tablon(a.iata)) }
                                actual.origen.isEmpty() -> { pila.removeAt(pila.lastIndex); pila.add(Pantalla.BuscarAeropuerto(a.iata)) }
                                else -> { Almacen.anadirTrayecto(actual.origen, a.iata); pila.removeAt(pila.lastIndex) }
                            }
                        }
                        is Pantalla.Detalle -> key(actual) { Detalle(actual.vuelo, atras) }
                        is Pantalla.Tablon -> Tablon(actual.iata, atras) { v -> pila.add(Pantalla.Detalle(v)) }
                        Pantalla.Ajustes -> AjustesPantalla(atras)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesdeAviso(intent)
    }

    private fun abrirDesdeAviso(i: Intent?) {
        val clave = i?.getStringExtra(EXTRA_VUELO) ?: return
        val d = Almacen.datos.value
        val v = d.vuelos.firstOrNull { it.clave == clave || it.ultimo?.clave == clave }?.ultimo
            ?: VigilanciaService.ultimo.value?.takeIf { it.clave == clave }
            ?: d.trayectos.firstNotNullOfOrNull { t -> t.vistos.values.firstOrNull { it.clave == clave } }
            ?: return
        pila.add(Pantalla.Detalle(v))
    }

    companion object {
        const val EXTRA_VUELO = "vuelo"
    }
}
