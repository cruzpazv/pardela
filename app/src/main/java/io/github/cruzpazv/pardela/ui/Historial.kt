package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.cruzpazv.pardela.data.Avion
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.data.intentar

/** Vuelos anteriores de un avión (últimos 7 días). */
@Composable
fun HistorialAvion(avion: Avion, atras: () -> Unit, abrir: (Vuelo) -> Unit) {
    var lista by remember { mutableStateOf<List<Vuelo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        intentar { Repositorio.historialAvion(avion) }
            .onSuccess { lista = it }.onFailure { error = it.message ?: tx(R.string.sin_conexion) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera(tx(R.string.avion_x, avion.matricula ?: avion.hex?.uppercase() ?: ""), atras)
        Texto(tx(R.string.vuelos_de_los_ultimos_7_dias_del_mas_r), 17, C.gris)
        Spacer(Modifier.height(8.dp))
        // Copias locales: el contenido de LazyColumn se evalúa más tarde.
        val err = error
        val vuelos = lista
        when {
            err != null -> AvisoError(err)
            vuelos == null -> Cargando()
            vuelos.isEmpty() -> Texto(tx(R.string.no_consta_ningun_vuelo_de_este_avion_e), 18, C.gris, modifier = Modifier.padding(16.dp))
            else -> LazyColumn { items(vuelos) { v -> FilaVuelo(v, salidas = true, conOrigen = true) { abrir(v) } } }
        }
    }
}
