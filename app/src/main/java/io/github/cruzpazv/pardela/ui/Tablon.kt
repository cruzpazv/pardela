package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

import io.github.cruzpazv.pardela.data.intentar

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import io.github.cruzpazv.pardela.data.Ajustes
import io.github.cruzpazv.pardela.data.Extremo
import io.github.cruzpazv.pardela.data.Lugar
import io.github.cruzpazv.pardela.data.Unidades
import java.time.LocalDate
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Datos
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Tema
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.notif.Avisos
import kotlinx.coroutines.delay

/** Estado del tablón: vive mientras la pantalla esté en la pila (al volver de un vuelo sigue igual). */
class EstadoTablon {
    val salidas = mutableStateOf(true)
    val lista = mutableStateOf<List<Vuelo>?>(null)
    val error = mutableStateOf<String?>(null)
    val vuelta = mutableIntStateOf(0)
    var cargado: Pair<Boolean, Int>? = null
    val scroll = LazyListState()
}

@Composable
fun Tablon(iata: String, e: EstadoTablon, atras: () -> Unit, verCerca: (Pantalla.Cerca) -> Unit, abrir: (Vuelo) -> Unit) {
    val datos by Almacen.datos.collectAsState()
    val ap = Aeropuertos.iata(iata)
    var salidas by e.salidas
    var lista by e.lista
    var error by e.error
    var vuelta by e.vuelta
    val guardado = iata in datos.aeropuertos

    LaunchedEffect(salidas, vuelta) {
        val pedido = salidas to vuelta
        // Al volver de un vuelo no se recarga: se mantiene la lista y su posición.
        if (e.cargado != pedido) {
            lista = null; error = null
            intentar { Repositorio.tablon(iata, salidas) }
                .onSuccess { lista = it; e.cargado = pedido }.onFailure { error = it.message ?: tx(R.string.sin_conexion) }
        }
        // Sin clave el radar cambia rápido: se refresca solo cada 2 min.
        if (!Almacen.tieneClave) { delay(120_000); vuelta++ }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera("$iata · ${ap?.ciudad?.uppercase() ?: ""}", atras) {
            if (ap != null) IconButton(onClick = { verCerca(Pantalla.Cerca(ap.lat, ap.lon, tx(R.string.aviones_cerca_de_x, iata))) }) {
                Icon(Icons.Default.LocationOn, tx(R.string.aviones_cerca_en_el_mapa), tint = C.ambar, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = { Almacen.alternarAeropuerto(iata) }) {
                Icon(Icons.Default.Star, if (guardado) tx(R.string.quitar_de_mis_aeropuertos) else tx(R.string.guardar_aeropuerto),
                    tint = if (guardado) C.ambar else C.gris, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { vuelta++ }) { Icon(Icons.Default.Refresh, tx(R.string.actualizar), tint = C.ambar, modifier = Modifier.size(34.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to tx(R.string.salidas), false to tx(R.string.llegadas)).forEach { (s, nombre) ->
                val sel = s == salidas
                Box(
                    Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) C.boton else C.panel).border(2.dp, C.ambar, RoundedCornerShape(12.dp))
                        .clickable { salidas = s },
                    contentAlignment = Alignment.Center,
                ) { Texto(nombre, 20, if (sel) C.sobreBoton else C.ambar, FontWeight.Black, mono = true) }
            }
        }
        if (!guardado) Texto(tx(R.string.pulse_para_guardar_este_aeropuerto_en), 15, C.gris, modifier = Modifier.padding(top = 8.dp))
        if (!Almacen.tieneClave) Texto(tx(R.string.sin_clave_api_solo_aviones_en_el_aire), 15, C.gris, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(6.dp))
        // Copias locales: el contenido de LazyColumn se evalúa más tarde, cuando el estado ya puede ser null.
        val err = error
        val vuelos = lista
        val esSalidas = salidas
        when {
            err != null -> AvisoError(err)
            vuelos == null -> Cargando()
            vuelos.isEmpty() -> Texto(tx(R.string.no_hay_vuelos_que_mostrar_ahora), 18, C.gris, modifier = Modifier.padding(16.dp))
            else -> LazyColumn(state = e.scroll) { items(vuelos) { v -> FilaVuelo(v, esSalidas) { abrir(v) } } }
        }
    }
}
