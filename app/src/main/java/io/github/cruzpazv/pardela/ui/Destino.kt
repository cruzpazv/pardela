package io.github.cruzpazv.pardela.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Tiempo
import io.github.cruzpazv.pardela.data.Vuelo

/** Hora local y tiempo en el aeropuerto de llegada (ahora y, si se puede, a la hora de llegar). */
@Composable
fun EnDestino(v: Vuelo) {
    val ap = Aeropuertos.iata(v.llegada.lugar.iata) ?: return
    val info by produceState<Tiempo.Info?>(null, ap.iata) { value = Tiempo.de(ap.lat, ap.lon) }
    val i = info ?: return
    Seccion("EN ${ap.ciudad.uppercase()}")
    Tarjeta {
        Column {
            i.zona?.let { z ->
                Texto("🕐 Allí son las ${Tiempo.horaAhora(z)}", 20, C.texto, FontWeight.Bold)
                Texto(Tiempo.diferencia(z).replaceFirstChar { it.uppercase() }, 16, C.gris, modifier = Modifier.padding(bottom = 10.dp))
            }
            i.ahora?.let { p ->
                val (simbolo, texto) = Tiempo.describir(p)
                Texto("Ahora: $simbolo ${Tiempo.grados(p)}" + if (texto.isNotEmpty()) " · $texto" else "", 19, C.texto)
            }
            val llega = v.llegada.utcPrevista ?: v.llegada.utc
            if (llega != null && llega > System.currentTimeMillis() + 60 * 60_000L) Tiempo.para(i, llega)?.let { p ->
                val (simbolo, texto) = Tiempo.describir(p)
                val hora = v.llegada.horaPrevista ?: v.llegada.hora
                Texto("Al llegar" + (hora?.let { " ($it)" } ?: "") + ": $simbolo ${Tiempo.grados(p)}" + if (texto.isNotEmpty()) " · $texto" else "", 19, C.texto, FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            Texto("Tiempo: Open-Meteo.com", 13, C.gris, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
