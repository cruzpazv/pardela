package io.github.cruzpazv.pardela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

/** Guía rápida de 4 pasos: sale una vez tras la bienvenida y se puede volver a ver desde Ajustes. */
@Composable
fun Guia(terminar: () -> Unit) {
    val pasos: List<Triple<ImageVector, Int, Int>> = listOf(
        Triple(Icons.Default.Search, R.string.guia_1_titulo, R.string.guia_1_texto),
        Triple(Icons.Default.Star, R.string.guia_2_titulo, R.string.guia_2_texto),
        Triple(Icons.Default.Notifications, R.string.guia_3_titulo, R.string.guia_3_texto),
        Triple(Icons.Default.Settings, R.string.guia_4_titulo, R.string.guia_4_texto),
    )
    var paso by remember { mutableIntStateOf(0) }
    val (icono, titulo, texto) = pasos[paso]
    val ultimo = paso == pasos.lastIndex
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        // Puntos de avance
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            pasos.indices.forEach { i ->
                Box(Modifier.padding(5.dp).size(12.dp).clip(CircleShape).background(if (i <= paso) C.ambar else C.panel))
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(120.dp).clip(CircleShape).background(C.panel), contentAlignment = Alignment.Center) {
                Icon(icono, null, tint = C.ambar, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(24.dp))
            Texto(tx(titulo), 26, C.texto, FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Texto(tx(texto), 20, C.texto)
        }
        Boton(if (ultimo) tx(R.string.guia_empezar) else tx(R.string.guia_siguiente)) { if (ultimo) terminar() else paso++ }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (paso > 0) BotonSecundario(tx(R.string.guia_atras), Modifier.weight(1f)) { paso-- }
            if (!ultimo) BotonSecundario(tx(R.string.guia_saltar), Modifier.weight(1f)) { terminar() }
        }
    }
}
