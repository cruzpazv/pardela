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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.cruzpazv.pardela.ui.AvisoFallo
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.notif.Avisos
import io.github.cruzpazv.pardela.notif.VigilanciaService
import io.github.cruzpazv.pardela.ui.AjustesPantalla
import io.github.cruzpazv.pardela.ui.AjustesSeccion
import io.github.cruzpazv.pardela.ui.Bienvenida
import io.github.cruzpazv.pardela.ui.BuscarAeropuerto
import io.github.cruzpazv.pardela.ui.BuscarVuelo
import io.github.cruzpazv.pardela.ui.C
import io.github.cruzpazv.pardela.ui.Detalle
import io.github.cruzpazv.pardela.ui.Guia
import io.github.cruzpazv.pardela.ui.HistorialAvion
import io.github.cruzpazv.pardela.ui.MapaPantalla
import io.github.cruzpazv.pardela.ui.AvionesCerca
import io.github.cruzpazv.pardela.ui.Inicio
import io.github.cruzpazv.pardela.ui.Pantalla
import io.github.cruzpazv.pardela.ui.TemaTablon
import io.github.cruzpazv.pardela.ui.Tablon

class MainActivity : ComponentActivity() {
    private val pila = mutableStateListOf<Pantalla>(Pantalla.Inicio)
    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Android 12 o anterior: la pantalla usa el idioma elegido en Ajustes.
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Idioma.envolver(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Idioma.refrescar(this)
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
                    if (!datos.ajustes.bienvenidaVista) Bienvenida()
                    // Guía rápida: una vez, justo después de la bienvenida.
                    else if (!datos.ajustes.guiaVista) Guia { Almacen.ajustar { it.copy(guiaVista = true) } }
                    else when (actual) {
                        Pantalla.Inicio -> Inicio(ir)
                        is Pantalla.BuscarVuelo -> BuscarVuelo(actual.estado, atras) { v -> pila.add(Pantalla.Detalle(v)) }
                        is Pantalla.BuscarAeropuerto -> BuscarAeropuerto(actual.origen, atras) { a ->
                            when {
                                actual.origen == null -> { pila.removeAt(pila.lastIndex); pila.add(Pantalla.Tablon(a.iata)) }
                                actual.origen.isEmpty() -> { pila.removeAt(pila.lastIndex); pila.add(Pantalla.BuscarAeropuerto(a.iata)) }
                                else -> { Almacen.anadirTrayecto(actual.origen, a.iata); pila.removeAt(pila.lastIndex) }
                            }
                        }
                        is Pantalla.Detalle -> key(actual) { Detalle(actual.vuelo, atras, { a -> pila.add(Pantalla.HistorialAvion(a)) }) { v -> pila.add(Pantalla.Mapa(v)) } }
                        is Pantalla.Cerca -> key(actual) { AvionesCerca(actual.lat, actual.lon, actual.titulo, atras) { v -> pila.add(Pantalla.Detalle(v)) } }
                        is Pantalla.Mapa -> key(actual) { MapaPantalla(actual.vuelo, atras) }
                        is Pantalla.HistorialAvion -> key(actual) { HistorialAvion(actual.avion, atras) { v -> pila.add(Pantalla.Detalle(v)) } }
                        is Pantalla.Tablon -> Tablon(actual.iata, actual.estado, atras, { c -> pila.add(c) }) { v -> pila.add(Pantalla.Detalle(v)) }
                        Pantalla.Guia -> Guia(atras)
                        Pantalla.Ajustes -> AjustesPantalla(atras, { pila.add(Pantalla.Guia) }) { s -> pila.add(Pantalla.AjustesDe(s)) }
                        is Pantalla.AjustesDe -> key(actual) { AjustesSeccion(actual.seccion, atras) }
                    }
                }
                var fallo by remember { mutableStateOf(App.ficheroFallo(this@MainActivity).takeIf { it.exists() }?.readText()) }
                fallo?.let { AvisoFallo(it) { App.ficheroFallo(this@MainActivity).delete(); fallo = null } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesdeAviso(intent)
    }

    private fun abrirDesdeAviso(i: Intent?) {
        val clave = i?.getStringExtra(EXTRA_VUELO) ?: return
        val v = Avisos.vueloDe(clave) ?: return
        pila.add(Pantalla.Detalle(v))
    }

    companion object {
        const val EXTRA_VUELO = "vuelo"
    }
}
