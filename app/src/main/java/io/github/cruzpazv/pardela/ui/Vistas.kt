package io.github.cruzpazv.pardela.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.cruzpazv.pardela.Idioma
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Aerolineas
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Fabricantes
import io.github.cruzpazv.pardela.data.Formato
import io.github.cruzpazv.pardela.data.Fotos
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.tx
import org.osmdroid.util.GeoPoint
import java.text.Normalizer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Vistas del modo pantalla para "aviones cerca". */
object VistasPantalla {
    const val ROTAR = "rotar"; const val RADAR = "radar"; const val TABLA = "tabla"; const val CERCANO = "cercano"
    const val LED = "led"; const val RUTA = "ruta"; const val MAPA = "mapa"; const val PISTA = "pista"

    /** Vistas posibles; la pista solo tiene sentido alrededor de un aeropuerto. */
    fun disponibles(conAeropuerto: Boolean) =
        listOfNotNull(RADAR, CERCANO, TABLA, LED, RUTA, MAPA, if (conAeropuerto) PISTA else null)
}

// ---------- Utilidades ----------

/** Rumbo (0 = norte, 90 = este) de un punto a otro, en grados. */
internal fun rumbo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val f1 = Math.toRadians(lat1); val f2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
    val y = sin(dl) * cos(f2); val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

internal fun cardinal(grados: Double): String = tx(R.string.cardinales).split(",")[((grados + 22.5) / 45).toInt() % 8]

private fun clave(v: Vuelo) = v.avion.hex ?: v.numero

/** "sube" / "baja" comparando con la lectura anterior del radar. */
private fun tendencia(v: Vuelo, anteriores: Map<String, Int>): String? {
    val ahora = v.directo?.altitudPies ?: return null
    val antes = anteriores[clave(v)] ?: return null
    return when { ahora - antes > 150 -> tx(R.string.sube); antes - ahora > 150 -> tx(R.string.baja); else -> null }
}

private fun conRuta(v: Vuelo) = v.salida.lugar.iata.isNotBlank() && v.llegada.lugar.iata.isNotBlank()
private fun ciudad(iata: String) = Aeropuertos.iata(iata)?.ciudad ?: iata
private fun kmTexto(km: Double) = String.format(Idioma.locale(), "%.1f", km)

// ---------- RADAR ----------

/** Pantalla de radar: anillos de 20/40/60 km, norte arriba, cada avión con una raya hacia donde va. */
@Composable
internal fun VistaRadar(centro: GeoPoint, aviones: List<Repositorio.Cercano>, abrir: (Vuelo) -> Unit) {
    val medidor = rememberTextMeasurer()
    val ambar = C.ambar; val gris = C.gris; val texto = C.texto; val verde = C.verde
    val u = Almacen.ajustes.unidades
    Column(Modifier.fillMaxSize()) {
        Canvas(Modifier.weight(1f).fillMaxWidth().clickable { aviones.firstOrNull()?.let { abrir(it.vuelo) } }) {
            val radio = minOf(size.width, size.height) / 2f * 0.92f
            val c = Offset(size.width / 2f, size.height / 2f)
            val alcance = 60.0
            listOf(20, 40, 60).forEach { km ->
                val r = radio * km / alcance.toFloat()
                drawCircle(gris.copy(alpha = if (km == 60) 0.8f else 0.4f), r, c, style = Stroke(2f))
                drawText(medidor, "$km km", Offset(c.x + 6f, c.y - r + 4f), TextStyle(color = gris, fontSize = 13.sp))
            }
            drawLine(gris.copy(alpha = .3f), Offset(c.x - radio, c.y), Offset(c.x + radio, c.y), 1.5f)
            drawLine(gris.copy(alpha = .3f), Offset(c.x, c.y - radio), Offset(c.x, c.y + radio), 1.5f)
            // Casa / aeropuerto en el centro
            drawCircle(texto, 9f, c)
            val coslat = cos(Math.toRadians(centro.latitude))
            aviones.forEachIndexed { i, a ->
                val d = a.vuelo.directo ?: return@forEachIndexed
                val dx = (d.lon - centro.longitude) * 111.32 * coslat
                val dy = (d.lat - centro.latitude) * 110.57
                if (dx * dx + dy * dy > alcance * alcance) return@forEachIndexed
                val p = Offset(c.x + (dx / alcance * radio).toFloat(), c.y - (dy / alcance * radio).toFloat())
                d.rumbo?.let { g ->
                    val rad = Math.toRadians(g)
                    drawLine(verde, p, Offset(p.x + (sin(rad) * 22).toFloat(), p.y - (cos(rad) * 22).toFloat()), 4f)
                }
                drawCircle(Color.White, 11f, p)
                drawCircle(verde, 8f, p)
                // Etiquetas solo para los 8 más cercanos (si no, se pisan).
                if (i < 8) {
                    drawText(medidor, a.vuelo.numero, Offset(p.x + 14f, p.y - 26f), TextStyle(color = texto, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    d.altitudPies?.let { drawText(medidor, Formato.altura(it, u), Offset(p.x + 14f, p.y - 4f), TextStyle(color = gris, fontSize = 13.sp)) }
                }
            }
            drawText(medidor, "N ↑", Offset(c.x - 14f, c.y - radio - 30f), TextStyle(color = ambar, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        aviones.firstOrNull()?.let { a ->
            Texto(tx(R.string.x_aviones_mas_cercano, aviones.size, a.vuelo.numero, kmTexto(a.km)), 16, C.gris, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------- TABLA ----------

@Composable
internal fun VistaTabla(aviones: List<Repositorio.Cercano>, abrir: (Vuelo) -> Unit) {
    val u = Almacen.ajustes.unidades
    @Composable
    fun Fila(c1: String, c2: String, c3: String, c4: String, c5: String, c6: String, cab: Boolean, onClick: (() -> Unit)? = null) {
        val color = if (cab) C.gris else C.texto
        val tam = if (cab) 13 else 18
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                .background(if (cab) Color.Transparent else C.panel)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Texto(c1, tam, if (cab) C.gris else C.ambar, FontWeight.Bold, mono = true, lineas = 1, modifier = Modifier.weight(1.4f))
            Texto(c2, tam, color, mono = true, lineas = 1, modifier = Modifier.weight(0.9f))
            Texto(c3, tam, color, mono = true, lineas = 1, modifier = Modifier.weight(0.8f))
            Texto(c4, tam, color, mono = true, lineas = 1, modifier = Modifier.weight(0.8f))
            Texto(c5, tam, color, mono = true, lineas = 1, modifier = Modifier.weight(1.2f))
            Texto(c6, tam, color, mono = true, lineas = 1, modifier = Modifier.weight(0.8f))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Fila(tx(R.string.col_vuelo), tx(R.string.col_tipo), tx(R.string.col_de), tx(R.string.col_a), tx(R.string.col_alt), tx(R.string.col_km), cab = true)
        LazyColumn {
            items(aviones) { a ->
                val v = a.vuelo
                Fila(
                    v.numero, v.avion.tipoIcao ?: "—", v.salida.lugar.iata.ifBlank { "—" }, v.llegada.lugar.iata.ifBlank { "—" },
                    v.directo?.altitudPies?.let { Formato.altura(it, u) } ?: "—", kmTexto(a.km), cab = false,
                ) { abrir(v) }
            }
        }
    }
}

// ---------- EL MÁS CERCANO ----------

@Composable
internal fun VistaCercano(centro: GeoPoint, aviones: List<Repositorio.Cercano>, anteriores: Map<String, Int>, abrir: (Vuelo) -> Unit) {
    val a = aviones.firstOrNull { conRuta(it.vuelo) } ?: aviones.firstOrNull()
    if (a == null) { Texto(tx(R.string.ahora_mismo_no_hay_aviones_a_la_vista), 20, C.gris); return }
    val v = a.vuelo; val d = v.directo; val u = Almacen.ajustes.unidades
    val foto by produceState<String?>(null, v.avion.hex) { value = v.avion.hex?.let { Fotos.deHex(it)?.first } }
    Column(Modifier.fillMaxSize().clickable { abrir(v) }) {
        if (conRuta(v)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column { Paletas(v.salida.lugar.iata, 40); Texto(ciudad(v.salida.lugar.iata), 17, C.texto, FontWeight.Bold) }
                Texto("  →  ", 30, C.texto, FontWeight.Bold)
                Column { Paletas(v.llegada.lugar.iata, 40); Texto(ciudad(v.llegada.lugar.iata), 17, C.texto, FontWeight.Bold) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Paletas(v.numero, 22)
            Spacer(Modifier.width(12.dp))
            Texto(listOfNotNull(v.aerolineaNombre, Fabricantes.modelo(v.avion.tipoIcao) ?: v.avion.tipoIcao, v.avion.matricula).joinToString(", "), 17, C.texto, lineas = 2)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val f = foto
            if (f != null) AsyncImage(f, tx(R.string.foto_del_avion), Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
            else Icon(painterResource(R.drawable.ic_avion), null, tint = C.gris, modifier = Modifier.size(120.dp).rotate(90f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            d?.altitudPies?.let { alt ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Paletas(Formato.altura(alt, u), 22); tendencia(v, anteriores)?.let { Texto("  $it", 16, C.gris) }
                }
            }
            d?.velocidadNudos?.let { Paletas(Formato.velocidad(it, u), 22) }
            d?.let { p ->
                val g = rumbo(centro.latitude, centro.longitude, p.lat, p.lon)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Paletas("${kmTexto(a.km)} KM", 22)
                    Texto("  ${cardinal(g)}", 18, C.texto, FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Brujula(g)
                }
            }
        }
    }
}

/** Círculo con una flecha hacia donde está el avión (norte arriba). */
@Composable
private fun Brujula(grados: Double) {
    val color = C.texto
    Canvas(Modifier.size(44.dp)) {
        val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2 - 3
        drawCircle(color, r, c, style = Stroke(3f))
        rotate(grados.toFloat(), c) {
            val punta = Path().apply { moveTo(c.x, c.y - r + 6); lineTo(c.x - 7, c.y + 4); lineTo(c.x + 7, c.y + 4); close() }
            drawPath(punta, color)
        }
    }
}

// ---------- LETRERO LED ----------

/** Tipografía de puntos 5×7 (mayúsculas sin tildes, números y unos pocos signos). */
private val LED: Map<Char, List<String>> = mapOf(
    'A' to listOf(" ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"),
    'B' to listOf("#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "),
    'C' to listOf(" ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### "),
    'D' to listOf("#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "),
    'E' to listOf("#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"),
    'F' to listOf("#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    "),
    'G' to listOf(" ### ", "#   #", "#    ", "# ###", "#   #", "#   #", " ####"),
    'H' to listOf("#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"),
    'I' to listOf(" ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "),
    'J' to listOf("  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  "),
    'K' to listOf("#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"),
    'L' to listOf("#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"),
    'M' to listOf("#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #"),
    'N' to listOf("#   #", "#   #", "##  #", "# # #", "#  ##", "#   #", "#   #"),
    'O' to listOf(" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "),
    'P' to listOf("#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "),
    'Q' to listOf(" ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #"),
    'R' to listOf("#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"),
    'S' to listOf(" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "),
    'T' to listOf("#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "),
    'U' to listOf("#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "),
    'V' to listOf("#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  "),
    'W' to listOf("#   #", "#   #", "#   #", "# # #", "# # #", "# # #", " # # "),
    'X' to listOf("#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"),
    'Y' to listOf("#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  "),
    'Z' to listOf("#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####"),
    '0' to listOf(" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "),
    '1' to listOf("  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "),
    '2' to listOf(" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"),
    '3' to listOf("#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### "),
    '4' to listOf("   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "),
    '5' to listOf("#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "),
    '6' to listOf("  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "),
    '7' to listOf("#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "),
    '8' to listOf(" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "),
    '9' to listOf(" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "),
    '.' to listOf("     ", "     ", "     ", "     ", "     ", " ##  ", " ##  "),
    ',' to listOf("     ", "     ", "     ", "     ", " ##  ", "  #  ", " #   "),
    '-' to listOf("     ", "     ", "     ", "#####", "     ", "     ", "     "),
    '/' to listOf("     ", "    #", "   # ", "  #  ", " #   ", "#    ", "     "),
    ':' to listOf("     ", " ##  ", " ##  ", "     ", " ##  ", " ##  ", "     "),
    '→' to listOf("     ", "  #  ", "   # ", "#####", "   # ", "  #  ", "     "),
    '↑' to listOf("  #  ", " ### ", "# # #", "  #  ", "  #  ", "  #  ", "  #  "),
    '↓' to listOf("  #  ", "  #  ", "  #  ", "  #  ", "# # #", " ### ", "  #  "),
    '(' to listOf("   # ", "  #  ", " #   ", " #   ", " #   ", "  #  ", "   # "),
    ')' to listOf(" #   ", "  #  ", "   # ", "   # ", "   # ", "  #  ", " #   "),
    '&' to listOf(" ##  ", "#  # ", "# #  ", " #   ", "# # #", "#  # ", " ## #"),
)

private fun paraLed(s: String) = Normalizer.normalize(s.uppercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Texto en puntos luminosos. [punto] = tamaño de cada punto. */
@Composable
private fun Led(texto: String, color: Color, punto: Dp, modifier: Modifier = Modifier) {
    val t = paraLed(texto)
    Canvas(modifier.width(punto * (t.length * 6)).height(punto * 7)) {
        val p = punto.toPx(); val r = p * 0.42f
        t.forEachIndexed { i, ch ->
            val g = LED[ch] ?: return@forEachIndexed
            for (fila in 0 until 7) for (col in 0 until 5) if (g[fila][col] == '#')
                drawCircle(color, r, Offset((i * 6 + col) * p + p / 2, fila * p + p / 2))
        }
    }
}

/** El avión más cercano en un letrero LED: aerolínea en naranja y el resto en blanco, sobre negro. */
@Composable
internal fun VistaLed(centro: GeoPoint, aviones: List<Repositorio.Cercano>, anteriores: Map<String, Int>) {
    val a = aviones.firstOrNull { conRuta(it.vuelo) } ?: aviones.firstOrNull()
    val naranja = Color(0xFFFF7A1A); val blanco = Color(0xFFEDEDED)
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black).padding(12.dp)) {
        val u = minOf(maxWidth.value / 150f, maxHeight.value / 72f).dp
        if (a == null) { Led(tx(R.string.pantalla_cerca), blanco, u); return@BoxWithConstraints }
        val v = a.vuelo; val d = v.directo; val un = Almacen.ajustes.unidades
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Led((v.aerolineaNombre ?: v.numero).take(12), naranja, u * 1.4f)
                Led(v.numero.take(8), blanco, u)
            }
            if (conRuta(v)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Led(v.salida.lugar.iata, blanco, u * 2.2f); Spacer(Modifier.width(u * 4)); Led("→", blanco, u); Spacer(Modifier.width(u * 4))
                    Led(v.llegada.lugar.iata, blanco, u * 2.2f)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Led(ciudad(v.salida.lugar.iata).take(12), blanco, u * 0.9f); Led(ciudad(v.llegada.lugar.iata).take(12), blanco, u * 0.9f)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(u)) {
                var x = 0f; while (x < size.width) { drawCircle(blanco.copy(alpha = .6f), u.toPx() * .3f, Offset(x, size.height / 2)); x += u.toPx() * 1.2f }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Led(v.avion.tipoIcao ?: "", blanco, u * 0.9f)
                d?.altitudPies?.let { alt ->
                    val flecha = when (tendencia(v, anteriores)) { tx(R.string.sube) -> "↑ "; tx(R.string.baja) -> "↓ "; else -> "" }
                    Led(flecha + Formato.altura(alt, un), blanco, u * 0.9f)
                }
                d?.velocidadNudos?.let { Led(Formato.velocidad(it, un), blanco, u * 0.9f) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                d?.let { p -> Led("${kmTexto(a.km)} KM ${cardinal(rumbo(centro.latitude, centro.longitude, p.lat, p.lon))}", blanco, u * 0.9f) }
                Led(v.avion.matricula ?: "", blanco, u * 0.9f)
            }
        }
    }
}

// ---------- RUTA ----------

/** Arco de la ruta del avión más cercano: origen, destino, por dónde va y dónde está usted. */
@Composable
internal fun VistaRuta(centro: GeoPoint, aviones: List<Repositorio.Cercano>, nombreSitio: String) {
    val a = aviones.firstOrNull { conRuta(it.vuelo) }
    if (a == null) { Texto(tx(R.string.sin_avion_con_ruta), 20, C.gris); return }
    val v = a.vuelo
    val o = Aeropuertos.iata(v.salida.lugar.iata); val de = Aeropuertos.iata(v.llegada.lugar.iata); val d = v.directo
    if (o == null || de == null || d == null) { Texto(tx(R.string.sin_avion_con_ruta), 20, C.gris); return }
    val hechos = Repositorio.km(o.lat, o.lon, d.lat, d.lon); val quedan = Repositorio.km(d.lat, d.lon, de.lat, de.lon)
    val fAvion = (hechos / (hechos + quedan)).toFloat().coerceIn(0f, 1f)
    val dCasaO = Repositorio.km(o.lat, o.lon, centro.latitude, centro.longitude); val dCasaD = Repositorio.km(centro.latitude, centro.longitude, de.lat, de.lon)
    val fCasa = (dCasaO / (dCasaO + dCasaD)).toFloat().coerceIn(0f, 1f)
    val medidor = rememberTextMeasurer()
    val texto = C.texto; val gris = C.gris; val rojo = C.rojo; val azul = C.azul; val verde = C.verde
    Column(Modifier.fillMaxSize()) {
        Texto(tx(R.string.ruta_cabecera, v.numero, hechos.roundToInt(), quedan.roundToInt(), de.ciudad), 18, C.texto, mono = true, modifier = Modifier.align(Alignment.CenterHorizontally))
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val margen = size.width * 0.08f
            val base = size.height * 0.62f
            val p0 = Offset(margen, base); val p2 = Offset(size.width - margen, base); val ctrl = Offset(size.width / 2, base - size.height * 0.55f)
            fun bezier(t: Float) = Offset(
                (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * ctrl.x + t * t * p2.x,
                (1 - t) * (1 - t) * p0.y + 2 * (1 - t) * t * ctrl.y + t * t * p2.y,
            )
            val arco = Path().apply { moveTo(p0.x, p0.y); quadraticBezierTo(ctrl.x, ctrl.y, p2.x, p2.y) }
            drawPath(arco, gris, style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))))
            drawCircle(Color.White, 15f, p0); drawCircle(azul, 12f, p0)
            drawCircle(Color.White, 15f, p2); drawCircle(verde, 12f, p2)
            val estilo = TextStyle(color = texto, fontSize = 26.sp, fontWeight = FontWeight.Black)
            drawText(medidor, o.iata, Offset(p0.x - 30f, p0.y + 22f), estilo)
            drawText(medidor, de.iata, Offset(p2.x - 30f, p2.y + 22f), estilo)
            drawText(medidor, o.ciudad.take(16), Offset(p0.x - 30f, p0.y + 62f), TextStyle(color = gris, fontSize = 14.sp))
            drawText(medidor, de.ciudad.take(16), Offset(p2.x - 60f, p2.y + 62f), TextStyle(color = gris, fontSize = 14.sp))
            // Usted, proyectado sobre la ruta
            val casa = bezier(fCasa)
            drawLine(rojo, casa, Offset(casa.x, size.height - 40f), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            drawText(medidor, tx(R.string.usted_esta_aqui), Offset(casa.x - 60f, size.height - 34f), TextStyle(color = rojo, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            // El avión
            val av = bezier(fAvion)
            drawCircle(rojo.copy(alpha = .35f), 26f, av, style = Stroke(4f))
            drawCircle(Color.White, 15f, av); drawCircle(rojo, 12f, av)
        }
        if (nombreSitio.isNotEmpty()) Texto(nombreSitio, 14, C.gris)
    }
}

// ---------- PISTA ----------

private enum class Fase(val texto: Int) { EN_PISTA(R.string.fase_en_pista), RODANDO(R.string.fase_rodando), DESPEGANDO(R.string.fase_despegando), ATERRIZANDO(R.string.fase_aterrizando) }

private fun fase(a: Repositorio.Cercano, iata: String, anteriores: Map<String, Int>): Fase? {
    val d = a.vuelo.directo ?: return null
    val alt = d.altitudPies ?: 0; val gs = d.velocidadNudos ?: 0
    val antes = anteriores[clave(a.vuelo)]
    return when {
        d.enTierra && gs >= 40 -> Fase.EN_PISTA
        d.enTierra && gs >= 3 -> Fase.RODANDO
        d.enTierra -> null // parado en el estacionamiento
        a.km < 12 && alt < 5000 && a.vuelo.salida.lugar.iata == iata -> Fase.DESPEGANDO
        a.km < 20 && alt < 6000 && a.vuelo.llegada.lugar.iata == iata -> Fase.ATERRIZANDO
        a.km < 8 && alt < 3000 && antes != null -> if (alt > antes) Fase.DESPEGANDO else Fase.ATERRIZANDO
        else -> null
    }
}

/** Una pista dibujada con los aviones en tierra y los que despegan o aterrizan, y la lista debajo. */
@Composable
internal fun VistaPista(iata: String, aviones: List<Repositorio.Cercano>, anteriores: Map<String, Int>, abrir: (Vuelo) -> Unit) {
    val conFase = aviones.mapNotNull { a -> fase(a, iata, anteriores)?.let { a to it } }
    val colores = mapOf(Fase.EN_PISTA to C.rojo, Fase.RODANDO to C.verde, Fase.DESPEGANDO to C.ambar, Fase.ATERRIZANDO to C.azul)
    val avion = painterResource(R.drawable.ic_avion)
    val medidor = rememberTextMeasurer()
    val texto = C.texto; val u = Almacen.ajustes.unidades
    Column(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val y0 = size.height * 0.30f; val alto = size.height * 0.26f
            drawRect(Color(0xFF222222), Offset(0f, y0), Size(size.width, alto))
            // Umbrales y eje
            for (i in 0 until 6) {
                drawRect(Color.White, Offset(14f, y0 + 8f + i * (alto - 16f) / 6f), Size(36f, (alto - 16f) / 12f))
                drawRect(Color.White, Offset(size.width - 50f, y0 + 8f + i * (alto - 16f) / 6f), Size(36f, (alto - 16f) / 12f))
            }
            var x = 80f
            while (x < size.width - 100f) { drawRect(Color.White, Offset(x, y0 + alto / 2 - 3f), Size(40f, 6f)); x += 80f }
            fun dibujar(centro: Offset, color: Color, giro: Float, etiqueta: String) {
                val t = 46f
                translate(centro.x - t / 2, centro.y - t / 2) {
                    rotate(giro, Offset(t / 2, t / 2)) { with(avion) { draw(Size(t, t), colorFilter = ColorFilter.tint(color)) } }
                }
                drawText(medidor, etiqueta, Offset(centro.x - 30f, centro.y + 26f), TextStyle(color = texto, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
            val porFase = conFase.groupBy { it.second }
            porFase[Fase.EN_PISTA]?.forEachIndexed { i, (a, _) -> dibujar(Offset(size.width * (0.45f + 0.15f * i), y0 + alto / 2), Color.White, 90f, a.vuelo.numero) }
            porFase[Fase.RODANDO]?.take(5)?.forEachIndexed { i, (a, _) -> dibujar(Offset(110f + i * 150f, y0 + alto + 34f), colores.getValue(Fase.RODANDO), 90f, a.vuelo.numero) }
            porFase[Fase.DESPEGANDO]?.take(2)?.forEachIndexed { i, (a, _) -> dibujar(Offset(size.width * (0.82f - 0.12f * i), y0 - 32f), colores.getValue(Fase.DESPEGANDO), 70f, a.vuelo.numero) }
            porFase[Fase.ATERRIZANDO]?.take(2)?.forEachIndexed { i, (a, _) -> dibujar(Offset(size.width * (0.14f + 0.12f * i), y0 - 32f), colores.getValue(Fase.ATERRIZANDO), 110f, a.vuelo.numero) }
        }
        if (conFase.isEmpty()) { Texto(tx(R.string.pista_vacia), 17, C.gris, modifier = Modifier.padding(top = 8.dp)); return }
        LazyColumn(Modifier.weight(1f)) {
            items(conFase) { (a, f) ->
                val v = a.vuelo; val color = colores.getValue(f)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(C.panel).clickable { abrir(v) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(150.dp).background(color).padding(vertical = 16.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Texto(tx(f.texto), 14, Color.White, FontWeight.Black, lineas = 1)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Texto(v.numero, 19, C.texto, FontWeight.Bold, mono = true, lineas = 1)
                        Texto(listOfNotNull(v.indicativo, v.avion.matricula, v.avion.tipoIcao).joinToString(" · "), 13, C.gris, lineas = 1)
                    }
                    val otro = if (v.salida.lugar.iata == iata) v.llegada else v.salida
                    Texto(
                        if (otro.lugar.iata.isBlank()) "" else (if (v.salida.lugar.iata == iata) tx(R.string.a) else tx(R.string.de)) + ciudad(otro.lugar.iata).uppercase(),
                        16, C.texto, FontWeight.Bold, lineas = 1, modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                        v.directo?.velocidadNudos?.let { Texto(Formato.velocidad(it, u), 15, C.texto) }
                        Texto(tx(R.string.a_x_km, kmTexto(a.km)), 13, C.gris)
                    }
                }
            }
        }
        val n = conFase.groupingBy { it.second }.eachCount()
        Texto(
            tx(R.string.pista_resumen, iata, (n[Fase.EN_PISTA] ?: 0) + (n[Fase.RODANDO] ?: 0), n[Fase.DESPEGANDO] ?: 0, n[Fase.ATERRIZANDO] ?: 0),
            14, C.gris, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
