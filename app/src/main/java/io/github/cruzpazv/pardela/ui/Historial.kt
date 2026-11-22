package io.github.cruzpazv.pardela.ui

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
            .onSuccess { lista = it }.onFailure { error = it.message ?: "Sin conexión" }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera("AVIÓN ${avion.matricula ?: avion.hex?.uppercase() ?: ""}", atras)
        Texto("Vuelos de los últimos 7 días, del más reciente al más antiguo.", 17, C.gris)
        Spacer(Modifier.height(8.dp))
        // Copias locales: el contenido de LazyColumn se evalúa más tarde.
        val err = error
        val vuelos = lista
        when {
            err != null -> AvisoError(err)
            vuelos == null -> Cargando()
            vuelos.isEmpty() -> Texto("No consta ningún vuelo de este avión en los últimos 7 días.", 18, C.gris, modifier = Modifier.padding(16.dp))
            else -> LazyColumn { items(vuelos) { v -> FilaVuelo(v, salidas = true, conOrigen = true) { abrir(v) } } }
        }
    }
}
