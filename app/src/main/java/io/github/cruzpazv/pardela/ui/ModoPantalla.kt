package io.github.cruzpazv.pardela.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Aerolineas
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Formato
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.data.intentar
import io.github.cruzpazv.pardela.notif.Widget
import io.github.cruzpazv.pardela.tx
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Qué muestra el modo pantalla (se guarda en Ajustes). */
object ModosPantalla { const val CERCA = "cerca"; const val TABLON = "tablon"; const val VUELOS = "vuelos" }

/** Elegir qué mostrar en el modo pantalla y empezar. */
@Composable
fun ModoPantallaElegir(atras: () -> Unit, empezar: () -> Unit) {
    val datos by Almacen.datos.collectAsState()
    val aj = datos.ajustes
    fun ajustar(modo: String = aj.pantallaModo, iata: String = aj.pantallaIata) = Almacen.ajustar { it.copy(pantallaModo = modo, pantallaIata = iata) }
    val listo = when (aj.pantallaModo) {
        ModosPantalla.CERCA, ModosPantalla.VUELOS -> true
        ModosPantalla.TABLON -> aj.pantallaIata in datos.aeropuertos
        else -> false
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Cabecera(tx(R.string.modo_pantalla), atras)
        Texto(tx(R.string.modo_pantalla_intro), 16, C.gris)
        Seccion(tx(R.string.que_mostrar))
        listOf(
            Triple(ModosPantalla.CERCA, R.string.pantalla_cerca, R.string.pantalla_cerca_detalle),
            Triple(ModosPantalla.TABLON, R.string.pantalla_tablon, R.string.pantalla_tablon_detalle),
            Triple(ModosPantalla.VUELOS, R.string.mis_vuelos, R.string.pantalla_vuelos_detalle),
        ).forEach { (modo, titulo, detalle) ->
            Opcion(tx(titulo), tx(detalle), aj.pantallaModo == modo) {
                // El tablón necesita un aeropuerto guardado: se propone el primero.
                val iata = if (modo == ModosPantalla.TABLON && aj.pantallaIata !in datos.aeropuertos) datos.aeropuertos.firstOrNull().orEmpty() else aj.pantallaIata
                ajustar(modo, iata)
            }
        }
        if (aj.pantallaModo == ModosPantalla.CERCA || aj.pantallaModo == ModosPantalla.TABLON) {
            Seccion(tx(R.string.donde))
            if (aj.pantallaModo == ModosPantalla.CERCA) Opcion(tx(R.string.mi_ubicacion), null, aj.pantallaIata.isEmpty()) { ajustar(iata = "") }
            datos.aeropuertos.mapNotNull { Aeropuertos.iata(it) }.forEach { a ->
                Opcion("${a.iata} · ${a.ciudad.uppercase()}", null, aj.pantallaIata == a.iata) { ajustar(iata = a.iata) }
            }
            if (datos.aeropuertos.isEmpty()) Texto(tx(R.string.guarde_antes_aeropuerto), 16, C.gris)
        }
        if (aj.pantallaModo == ModosPantalla.CERCA) {
            Seccion(tx(R.string.vista))
            listOf(
                Triple(VistasPantalla.ROTAR, R.string.vista_rotar, R.string.vista_rotar_detalle),
                Triple(VistasPantalla.RADAR, R.string.vista_radar, R.string.vista_radar_detalle),
                Triple(VistasPantalla.CERCANO, R.string.vista_cercano, R.string.vista_cercano_detalle),
                Triple(VistasPantalla.TABLA, R.string.vista_tabla, R.string.vista_tabla_detalle),
                Triple(VistasPantalla.LED, R.string.vista_led, R.string.vista_led_detalle),
                Triple(VistasPantalla.RUTA, R.string.vista_ruta, R.string.vista_ruta_detalle),
                Triple(VistasPantalla.MAPA, R.string.vista_mapa, R.string.vista_mapa_detalle),
                Triple(VistasPantalla.PISTA, R.string.vista_pista, R.string.vista_pista_detalle),
            ).forEach { (vista, titulo, detalle) ->
                val posible = vista != VistasPantalla.PISTA || aj.pantallaIata.isNotEmpty()
                Opcion(tx(titulo), tx(detalle) + if (posible) "" else " " + tx(R.string.solo_con_aeropuerto), aj.pantallaVista == vista) {
                    if (posible) Almacen.ajustar { it.copy(pantallaVista = vista) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Interruptor(tx(R.string.abrir_siempre_asi), aj.pantallaAlAbrir, tx(R.string.abrir_siempre_asi_detalle)) { v ->
            Almacen.ajustar { it.copy(pantallaAlAbrir = v) }
        }
        Spacer(Modifier.height(12.dp))
        if (listo) Boton(tx(R.string.guia_empezar), onClick = empezar)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun Opcion(titulo: String, detalle: String?, sel: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(if (sel) C.boton else C.panel).border(2.dp, C.ambar, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Texto((if (sel) "✔ " else "") + titulo, 19, if (sel) C.sobreBoton else C.texto, FontWeight.Bold)
        detalle?.let { Texto(it, 15, if (sel) C.sobreBoton else C.gris) }
    }
}

/** Pantalla fija: siempre encendida, sin barras del sistema, sin animaciones y actualizándose sola. */
@Composable
fun ModoPantalla(atras: () -> Unit, abrir: (Vuelo) -> Unit) {
    val datos by Almacen.datos.collectAsState()
    val aj = datos.ajustes
    PantallaFija()
    var salidas by remember { mutableStateOf(true) }
    // Tablón: alterna salidas y llegadas cada 30 s.
    LaunchedEffect(aj.pantallaModo) {
        if (aj.pantallaModo != ModosPantalla.TABLON) return@LaunchedEffect
        while (true) { delay(30_000); salidas = !salidas }
    }
    val titulo = when (aj.pantallaModo) {
        ModosPantalla.CERCA -> if (aj.pantallaIata.isEmpty()) tx(R.string.pantalla_cerca) else tx(R.string.aviones_cerca_de_x, aj.pantallaIata)
        ModosPantalla.TABLON -> "${if (salidas) tx(R.string.salidas) else tx(R.string.llegadas)} · ${aj.pantallaIata}"
        else -> tx(R.string.mis_vuelos)
    }
    CompositionLocalProvider(Animar provides false) {
        Column(Modifier.fillMaxSize().background(C.fondo).padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Texto(titulo, 24, C.ambar, FontWeight.Black, mono = true, lineas = 1, modifier = Modifier.weight(1f))
                Reloj()
                IconButton(onClick = atras) { Icon(Icons.Default.Close, tx(R.string.salir), tint = C.gris, modifier = Modifier.size(28.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            when (aj.pantallaModo) {
                ModosPantalla.CERCA -> PantallaCerca(aj.pantallaIata, aj.pantallaVista, abrir)
                ModosPantalla.TABLON -> PantallaTablon(aj.pantallaIata, salidas, abrir)
                else -> PantallaVuelos(abrir)
            }
        }
    }
}

@Composable
private fun Reloj() {
    var hora by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { hora = LocalTime.now(); delay(5_000) } }
    Paletas(hora.format(DateTimeFormatter.ofPattern("HH:mm")), 24, C.texto)
}

/** Pantalla siempre encendida y a pantalla completa mientras se ve; al salir, todo vuelve a la normalidad. */
@Composable
private fun PantallaFija() {
    val vista = LocalView.current
    DisposableEffect(Unit) {
        vista.keepScreenOn = true
        var c = vista.context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val ventana = (c as? Activity)?.window
        val control = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        control?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        control?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            vista.keepScreenOn = false
            control?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun PantallaCerca(iata: String, vista: String, abrir: (Vuelo) -> Unit) {
    val ctx = LocalContext.current
    var centro by remember(iata) { mutableStateOf(Aeropuertos.iata(iata)?.let { GeoPoint(it.lat, it.lon) }) }
    var sinSitio by remember(iata) { mutableStateOf(false) }
    var lista by remember { mutableStateOf<List<Repositorio.Cercano>>(emptyList()) }
    // Altura de la lectura anterior de cada avión: para saber si sube o baja.
    var anteriores by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(iata) {
        if (centro == null) {
            val l = if (tienePermiso(ctx)) ubicacion(ctx) else null
            if (l != null) centro = GeoPoint(l.latitude, l.longitude) else sinSitio = true
        }
    }
    LaunchedEffect(centro) {
        val c = centro ?: return@LaunchedEffect
        while (true) {
            // Con aeropuerto también los aviones en tierra (para la vista de pista).
            intentar { Repositorio.cerca(c.latitude, c.longitude, conSuelo = iata.isNotEmpty()) }.getOrNull()?.let { l ->
                anteriores = lista.associate { (it.vuelo.avion.hex ?: it.vuelo.numero) to (it.vuelo.directo?.altitudPies ?: 0) }
                val visibles = Aerolineas.visibles(l.map { it.vuelo }).toSet()
                lista = l.filter { it.vuelo in visibles }
            }
            delay(20_000)
        }
    }
    val disponibles = VistasPantalla.disponibles(iata.isNotEmpty())
    var indice by remember { mutableIntStateOf(0) }
    LaunchedEffect(vista) {
        if (vista != VistasPantalla.ROTAR) return@LaunchedEffect
        while (true) { delay(20_000); indice++ }
    }
    val c = centro
    if (c == null) {
        if (sinSitio) Texto(tx(R.string.pantalla_sin_ubicacion), 20, C.gris) else Cargando(tx(R.string.buscando_su_posicion))
        return
    }
    val aire = lista.filter { it.vuelo.directo?.enTierra != true }
    val actual = if (vista == VistasPantalla.ROTAR || vista !in disponibles) disponibles[indice % disponibles.size] else vista
    when (actual) {
        VistasPantalla.RADAR -> VistaRadar(c, aire, abrir)
        VistasPantalla.TABLA -> VistaTabla(aire, abrir)
        VistasPantalla.CERCANO -> VistaCercano(c, aire, anteriores, abrir)
        VistasPantalla.LED -> VistaLed(c, aire, anteriores)
        VistasPantalla.RUTA -> VistaRuta(c, aire, Aeropuertos.iata(iata)?.nombre.orEmpty())
        VistasPantalla.PISTA -> VistaPista(iata, lista, anteriores, abrir)
        else -> VistaMapa(c, aire, abrir)
    }
}

/** Mapa y lista de los aviones cercanos (en horizontal, uno al lado del otro). */
@Composable
private fun VistaMapa(c: GeoPoint, lista: List<Repositorio.Cercano>, abrir: (Vuelo) -> Unit) {
    val u = Almacen.ajustes.unidades
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontal = maxWidth > maxHeight
        val mapa: @Composable (Modifier) -> Unit = { m -> MapaCerca(c, lista, m.clip(RoundedCornerShape(12.dp)), abrir) }
        val filas: @Composable (Modifier) -> Unit = { m ->
            if (lista.isEmpty()) Texto(tx(R.string.ahora_mismo_no_hay_aviones_a_la_vista), 18, C.gris, modifier = m)
            else LazyColumn(m) {
                items(lista) { a ->
                    val v = a.vuelo
                    val ruta = if (v.salida.lugar.iata.isBlank() && v.llegada.lugar.iata.isBlank()) tx(R.string.ruta_desconocida)
                    else "${v.salida.lugar.iata.ifBlank { "?" }} → ${v.llegada.lugar.iata.ifBlank { "?" }}"
                    FilaPantalla(v, ruta, { abrir(v) }) {
                        Column(horizontalAlignment = Alignment.End) {
                            v.directo?.altitudPies?.let { Texto(Formato.altura(it, u), 17, C.texto, FontWeight.Bold) }
                            Texto(tx(R.string.a_x_km, Math.round(a.km)), 15, C.gris)
                        }
                    }
                }
            }
        }
        if (horizontal) Row(Modifier.fillMaxSize()) {
            mapa(Modifier.weight(1f).fillMaxSize()); Spacer(Modifier.width(12.dp)); filas(Modifier.weight(1f).fillMaxSize())
        } else Column(Modifier.fillMaxSize()) {
            mapa(Modifier.weight(0.45f).fillMaxWidth()); Spacer(Modifier.height(10.dp)); filas(Modifier.weight(0.55f).fillMaxWidth())
        }
    }
}

@Composable
private fun PantallaTablon(iata: String, salidas: Boolean, abrir: (Vuelo) -> Unit) {
    var datos by remember { mutableStateOf<Map<Boolean, List<Vuelo>>>(emptyMap()) }
    // Solo radar gratuito: con la clave, una pantalla encendida todo el día agotaría el plan en pocos días.
    LaunchedEffect(iata) {
        while (true) {
            for (s in listOf(true, false)) intentar { Repositorio.tablon(iata, s, conClave = false) }.getOrNull()?.let { l ->
                datos = datos + (s to Aerolineas.visibles(l))
            }
            delay(120_000)
        }
    }
    val lista = datos[salidas]
    when {
        lista == null -> Cargando()
        lista.isEmpty() -> Texto(tx(R.string.no_hay_vuelos_que_mostrar_ahora), 20, C.gris)
        else -> LazyColumn {
            items(lista) { v ->
                val otro = if (salidas) v.llegada else v.salida
                val texto = (if (salidas) tx(R.string.a) else tx(R.string.de)) + otro.lugar.ciudad.ifBlank { otro.lugar.iata.ifBlank { "?" } }.uppercase()
                FilaPantalla(v, texto, { abrir(v) }) { EtiquetaEstado(v.estado) }
            }
        }
    }
}

@Composable
private fun PantallaVuelos(abrir: (Vuelo) -> Unit) {
    val datos by Almacen.datos.collectAsState()
    // Radar cada 2 min (gratis); la clave la sigue usando el comprobador con su ritmo prudente.
    LaunchedEffect(Unit) {
        while (true) {
            Almacen.datos.value.vuelos.mapNotNull { it.ultimo }.filterNot { it.estado.terminado }.forEach { v ->
                intentar { Repositorio.refrescar(v, conClave = false) }.getOrNull()?.let { Almacen.actualizar(it) }
            }
            delay(120_000)
        }
    }
    val todos = datos.vuelos.mapNotNull { it.ultimo }
    val proximo = Widget.elegir(todos)
    val lista = todos.filter { !it.estado.terminado || it == proximo }.sortedBy { it.salida.utcPrevista ?: it.salida.utc ?: Long.MAX_VALUE }
    if (lista.isEmpty()) Texto(tx(R.string.sin_vuelos_proximos), 22, C.gris)
    else LazyColumn { items(lista) { v -> FilaVuelo(v, salidas = true, conActualizado = true) { abrir(v) } } }
}

/** Fila grande y compacta para verla de lejos: logo, número, ruta o destino y, a la derecha, el dato clave. */
@Composable
private fun FilaPantalla(v: Vuelo, linea2: String, onClick: () -> Unit, derecha: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(C.panel)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoAerolinea(v.aerolineaIata, 32, nombre = v.aerolineaNombre)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Texto(v.numero, 22, C.texto, FontWeight.Bold, mono = true, lineas = 1)
            Texto(linea2, 19, C.ambar, FontWeight.Bold, mono = true, lineas = 1)
        }
        Spacer(Modifier.width(8.dp))
        derecha()
    }
}
