package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

import io.github.cruzpazv.pardela.data.Aerolineas
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Delete
import io.github.cruzpazv.pardela.data.Extremo
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
                Texto(tx(R.string.vigilando_x_x, v?.numero ?: "", v?.ruta ?: ""), 18, C.sobreAzul, FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))
        Boton(tx(R.string.buscar_un_vuelo), icono = Icons.Default.Search) { ir(Pantalla.BuscarVuelo()) }
        Spacer(Modifier.height(10.dp))
        Boton(tx(R.string.ver_un_aeropuerto), icono = Icons.Default.Place) { ir(Pantalla.BuscarAeropuerto(null)) }
        Spacer(Modifier.height(10.dp))
        BotonSecundario(tx(R.string.avisarme_de_un_trayecto), icono = Icons.Default.Notifications) { ir(Pantalla.BuscarAeropuerto("")) }
        Spacer(Modifier.height(10.dp))
        BotonSecundario(tx(R.string.que_avion_pasa_por_aqui), icono = Icons.Default.LocationOn) { ir(Pantalla.Cerca(null, null, tx(R.string.aviones_cerca_de_usted))) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Seccion(tx(R.string.mis_vuelos)) }
            if (datos.vuelos.isNotEmpty()) IconButton(onClick = ::refrescarTodo, modifier = Modifier.padding(top = 14.dp)) {
                Icon(Icons.Default.Refresh, tx(R.string.actualizar), tint = if (cargando) C.gris else C.ambar, modifier = Modifier.size(32.dp))
            }
        }
        if (datos.vuelos.isEmpty()) Texto(tx(R.string.aun_no_sigue_ningun_vuelo_pulse_buscar), 17, C.gris)
        datos.vuelos.forEach { s -> s.ultimo?.let { v -> FilaVuelo(v, salidas = true) { ir(Pantalla.Detalle(v)) } } }

        Seccion(tx(R.string.mis_aeropuertos))
        if (datos.aeropuertos.isEmpty()) Texto(tx(R.string.guarde_aqui_los_aeropuertos_que_mira_a), 17, C.gris)
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

        Seccion(tx(R.string.mis_trayectos_avisos))
        if (datos.trayectos.isEmpty()) Texto(tx(R.string.reciba_un_aviso_de_cada_vuelo_entre_do), 17, C.gris)
        datos.trayectos.forEach { tr ->
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Paletas("${tr.origen}→${tr.destino}", 22)
                        Texto("${Aeropuertos.iata(tr.origen)?.ciudad ?: ""} → ${Aeropuertos.iata(tr.destino)?.ciudad ?: ""}", 15, C.gris, lineas = 1)
                        Texto(tx(R.string.x_vuelos_vistos, tr.vistos.size), 14, C.gris)
                    }
                    IconButton(onClick = { borrarTrayecto = tr }) { Icon(Icons.Default.Delete, tx(R.string.quitar_2), tint = C.rojo, modifier = Modifier.size(32.dp)) }
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        BotonSecundario(tx(R.string.ajustes), icono = Icons.Default.Settings) { ir(Pantalla.Ajustes) }
        Spacer(Modifier.height(30.dp))
    }

    borrarTrayecto?.let { tr ->
        Confirmar(tx(R.string.dejar_de_avisar_del_trayecto_x_x, tr.origen, tr.destino), tx(R.string.quitar), { Almacen.quitarTrayecto(tr); borrarTrayecto = null }) { borrarTrayecto = null }
    }
}

@Composable
fun Confirmar(pregunta: String, si: String, alAceptar: () -> Unit, alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        containerColor = C.panel,
        text = { Texto(pregunta, 20) },
        confirmButton = { TextButton(onClick = alAceptar) { Texto(si, 20, C.rojo, FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = alCancelar) { Texto(tx(R.string.no), 20, C.ambar, FontWeight.Bold) } },
    )
}

/** Fila de tablón: hora | logo + número | ciudad | estado. */
@Composable
fun FilaVuelo(v: Vuelo, salidas: Boolean, conOrigen: Boolean = false, onClick: () -> Unit) {
    val ext = if (salidas) v.salida else v.llegada
    val otro = if (salidas) v.llegada else v.salida
    Tarjeta(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Paletas(ext.hora ?: "--:--", 20)
            Spacer(Modifier.width(10.dp))
            LogoAerolinea(v.aerolineaIata, 30, nombre = v.aerolineaNombre)
            Spacer(Modifier.width(10.dp))
            Texto(v.numero, 20, C.texto, FontWeight.Bold, mono = true, lineas = 1)
        }
        Spacer(Modifier.height(8.dp))
        fun nombre(l: Extremo) = l.lugar.ciudad.ifBlank { l.lugar.iata.ifBlank { "?" } }.uppercase()
        // Con origen (historial del avión): la ruta entera ocupa su propia línea, sin cortar.
        if (conOrigen) Texto("${nombre(v.salida)} → ${nombre(v.llegada)}", 19, C.ambar, FontWeight.Bold, mono = true, modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!conOrigen) Texto((if (salidas) tx(R.string.a) else tx(R.string.de)) + nombre(otro), 19, C.ambar, FontWeight.Bold, mono = true, lineas = 1)
                val extra = buildList {
                    v.fecha?.let { add(it.takeLast(5).split("-").reversed().joinToString("/")) }
                    ext.horaPrevista?.takeIf { it != ext.hora }?.let { add(tx(R.string.previsto_x, it)) }
                    ext.puerta?.let { add(tx(R.string.puerta_x, it)) }
                }
                if (extra.isNotEmpty()) Texto(extra.joinToString("  ·  "), 15, C.gris, lineas = if (conOrigen) 2 else 1)
                if (Aerolineas.posicionamiento(v.numero)) Texto(tx(R.string.i_probable_posicionamiento_sin_pasajer), 15, C.gris, lineas = 1)
            }
            EtiquetaEstado(v.estado)
        }
    }
}
