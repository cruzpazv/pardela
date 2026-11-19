package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.data.intentar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Trayecto
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.notif.VigilanciaService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun Reloj() {
    var hora by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { hora = LocalTime.now(); delay(1000) } }
    Paletas(hora.format(DateTimeFormatter.ofPattern("HH:mm")), 20, C.texto)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Inicio(ir: (Pantalla) -> Unit) {
    val datos by Almacen.datos.collectAsState()
    val vigilando by VigilanciaService.vigilando.collectAsState()
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(false) }
    var borrarTrayecto by remember { mutableStateOf<Trayecto?>(null) }

    fun refrescarTodo() {
        if (cargando) return
        cargando = true
        scope.launch {
            Almacen.datos.value.vuelos.mapNotNull { it.ultimo }.filterNot { it.estado.terminado }.forEach { v ->
                intentar { Repositorio.refrescar(v, conClave = false) }.getOrNull()?.let { Almacen.actualizar(it) }
            }
            cargando = false
        }
    }
    LaunchedEffect(Unit) { refrescarTodo() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Paletas("PARDELA", 24)
            Reloj()
        }

        vigilando?.let { clave ->
            val v = datos.vuelos.firstOrNull { it.clave == clave }?.ultimo ?: VigilanciaService.ultimo.collectAsState().value
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.azul).clickable { v?.let { ir(Pantalla.Detalle(it)) } }.padding(14.dp)) {
                Texto("👁  VIGILANDO ${v?.numero ?: ""}  ${v?.ruta ?: ""}", 18, C.sobreAzul, FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))
        Boton("BUSCAR UN VUELO", icono = Icons.Default.Search) { ir(Pantalla.BuscarVuelo) }
        Spacer(Modifier.height(10.dp))
        Boton("VER UN AEROPUERTO", icono = Icons.Default.Place) { ir(Pantalla.BuscarAeropuerto(null)) }
        Spacer(Modifier.height(10.dp))
        BotonSecundario("AVISARME DE UN TRAYECTO", icono = Icons.Default.Notifications) { ir(Pantalla.BuscarAeropuerto("")) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Seccion("MIS VUELOS") }
            if (datos.vuelos.isNotEmpty()) IconButton(onClick = ::refrescarTodo, modifier = Modifier.padding(top = 14.dp)) {
                Icon(Icons.Default.Refresh, "Actualizar", tint = if (cargando) C.gris else C.ambar, modifier = Modifier.size(32.dp))
            }
        }
        if (datos.vuelos.isEmpty()) Texto("Aún no sigue ningún vuelo. Pulse «BUSCAR UN VUELO» y después «SEGUIR».", 17, C.gris)
        datos.vuelos.forEach { s -> s.ultimo?.let { v -> FilaVuelo(v, salidas = true) { ir(Pantalla.Detalle(v)) } } }

        Seccion("MIS AEROPUERTOS")
        if (datos.aeropuertos.isEmpty()) Texto("Guarde aquí los aeropuertos que mira a menudo.", 17, C.gris)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            datos.aeropuertos.forEach { iata ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(C.panel).clickable { ir(Pantalla.Tablon(iata)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Paletas(iata, 22)
                    Texto(Aeropuertos.iata(iata)?.ciudad ?: "", 14, C.gris, lineas = 1)
                }
            }
        }

        Seccion("MIS TRAYECTOS (AVISOS)")
        if (datos.trayectos.isEmpty()) Texto("Reciba un aviso de cada vuelo entre dos ciudades.", 17, C.gris)
        datos.trayectos.forEach { tr ->
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Paletas("${tr.origen}→${tr.destino}", 22)
                        Texto("${Aeropuertos.iata(tr.origen)?.ciudad ?: ""} → ${Aeropuertos.iata(tr.destino)?.ciudad ?: ""}", 15, C.gris, lineas = 1)
                        Texto("${tr.vistos.size} vuelos vistos", 14, C.gris)
                    }
                    IconButton(onClick = { borrarTrayecto = tr }) { Icon(Icons.Default.Delete, "Quitar", tint = C.rojo, modifier = Modifier.size(32.dp)) }
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        BotonSecundario("AJUSTES", icono = Icons.Default.Settings) { ir(Pantalla.Ajustes) }
        Spacer(Modifier.height(30.dp))
    }

    borrarTrayecto?.let { tr ->
        Confirmar("¿Dejar de avisar del trayecto ${tr.origen} → ${tr.destino}?", "QUITAR", { Almacen.quitarTrayecto(tr); borrarTrayecto = null }) { borrarTrayecto = null }
    }
}

@Composable
fun Confirmar(pregunta: String, si: String, alAceptar: () -> Unit, alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        containerColor = C.panel,
        text = { Texto(pregunta, 20) },
        confirmButton = { TextButton(onClick = alAceptar) { Texto(si, 20, C.rojo, FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = alCancelar) { Texto("NO", 20, C.ambar, FontWeight.Bold) } },
    )
}

/** Fila de tablón: hora | logo + número | ciudad | estado. */
@Composable
fun FilaVuelo(v: Vuelo, salidas: Boolean, onClick: () -> Unit) {
    val ext = if (salidas) v.salida else v.llegada
    val otro = if (salidas) v.llegada else v.salida
    Tarjeta(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Paletas(ext.hora ?: "--:--", 20)
            Spacer(Modifier.width(10.dp))
            LogoAerolinea(v.aerolineaIata, 30)
            Spacer(Modifier.width(10.dp))
            Texto(v.numero, 20, C.texto, FontWeight.Bold, mono = true, lineas = 1)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Texto((if (salidas) "A " else "DE ") + (otro.lugar.ciudad.ifBlank { otro.lugar.iata.ifBlank { "?" } }).uppercase(), 19, C.ambar, FontWeight.Bold, mono = true, lineas = 1)
                val extra = buildList {
                    v.fecha?.let { add(it.takeLast(5).split("-").reversed().joinToString("/")) }
                    ext.horaPrevista?.takeIf { it != ext.hora }?.let { add("PREVISTO $it") }
                    ext.puerta?.let { add("PUERTA $it") }
                }
                if (extra.isNotEmpty()) Texto(extra.joinToString("  ·  "), 15, C.gris, lineas = 1)
            }
            EtiquetaEstado(v.estado)
        }
    }
}
