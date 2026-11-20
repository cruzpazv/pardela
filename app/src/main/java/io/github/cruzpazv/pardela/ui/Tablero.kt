package io.github.cruzpazv.pardela.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Aerolineas
import io.github.cruzpazv.pardela.data.Estado
import io.github.cruzpazv.pardela.data.Fabricantes
import kotlinx.coroutines.delay

/** Colores de un tema. "texto" = color principal de letra; "acento" = ámbar legible sobre el fondo. */
data class Paleta(
    val fondo: Color, val panel: Color, val celda: Color, val raya: Color,
    val ambar: Color, val texto: Color, val gris: Color,
    val verde: Color, val rojo: Color, val azul: Color,
    val boton: Color, val sobreBoton: Color, val sobreAzul: Color,
)

val Oscura = Paleta(
    fondo = Color(0xFF050505), panel = Color(0xFF161616), celda = Color(0xFF222222), raya = Color(0xFF000000),
    ambar = Color(0xFFE5B45C), texto = Color(0xFFF2F2F2), gris = Color(0xFFA6A6A6),
    verde = Color(0xFF39D98A), rojo = Color(0xFFFF5A4E), azul = Color(0xFF5AB4FF),
    boton = Color(0xFFE5B45C), sobreBoton = Color(0xFF000000), sobreAzul = Color(0xFF000000),
)

/** Claro: ámbar tostado y estados oscurecidos para contraste ≥ 4.5:1 sobre blanco. */
val Clara = Paleta(
    fondo = Color(0xFFE8E6E0), panel = Color(0xFFFFFFFF), celda = Color(0xFF2A2A2A), raya = Color(0xFF000000),
    ambar = Color(0xFF8A5300), texto = Color(0xFF151515), gris = Color(0xFF555555),
    verde = Color(0xFF17703F), rojo = Color(0xFFB3261E), azul = Color(0xFF0B5CAD),
    boton = Color(0xFFE5B45C), sobreBoton = Color(0xFF000000), sobreAzul = Color(0xFFFFFFFF),
)

/**
 * Daltonismo (paleta Okabe-Ito): "bien" en azul, "mal" en naranja bermellón y "en vuelo" en el color del texto.
 * Evita la pareja verde/rojo, la más difícil de distinguir.
 */
fun Paleta.daltonica(oscura: Boolean) = if (oscura)
    copy(verde = Color(0xFF56B4E9), rojo = Color(0xFFFF8C1A), azul = Color(0xFFF2F2F2), sobreAzul = Color(0xFF000000))
else copy(verde = Color(0xFF0068A8), rojo = Color(0xFFB84A00), azul = Color(0xFF151515), sobreAzul = Color(0xFFFFFFFF))

val LocalPaleta = staticCompositionLocalOf { Oscura }

/** Las fichas del tablón son siempre oscuras (como las reales); su letra usa esta paleta. */
val LocalFichas = staticCompositionLocalOf { Oscura }

object C {
    val fondo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.fondo
    val panel: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.panel
    val celda: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.celda
    val raya: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.raya
    val ambar: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.ambar
    val texto: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.texto
    val gris: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.gris
    val verde: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.verde
    val rojo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.rojo
    val azul: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.azul
    val boton: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.boton
    val sobreBoton: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.sobreBoton
    val sobreAzul: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.sobreAzul
}

val Mono = FontFamily.Monospace

/** false = las paletas cambian sin girar (ajuste "animaciones"). */
val Animar = compositionLocalOf { true }

/** Multiplicador de tamaño de letra (ajuste "texto grande"). */
val Escala = compositionLocalOf { 1f }

@Composable
fun t(base: Int): TextUnit = (base * Escala.current).sp

@Composable
fun TemaTablon(grande: Boolean, oscuro: Boolean, animar: Boolean, daltonismo: Boolean, contenido: @Composable () -> Unit) {
    val base = if (oscuro) Oscura else Clara
    val p = if (daltonismo) base.daltonica(oscuro) else base
    val fichas = if (daltonismo) Oscura.daltonica(true) else Oscura
    val esquema = if (oscuro) darkColorScheme(
        primary = p.ambar, onPrimary = p.sobreBoton, background = p.fondo, surface = p.panel,
        onBackground = p.texto, onSurface = p.texto, secondary = p.azul, error = p.rojo,
    ) else lightColorScheme(
        primary = p.ambar, onPrimary = Color.White, background = p.fondo, surface = p.panel,
        onBackground = p.texto, onSurface = p.texto, secondary = p.azul, error = p.rojo,
    )
    MaterialTheme(colorScheme = esquema) {
        CompositionLocalProvider(LocalPaleta provides p, LocalFichas provides fichas, Escala provides if (grande) 1.2f else 1f, Animar provides animar, content = contenido)
    }
}

@Composable
fun Texto(
    s: String, tam: Int = 18, color: Color = C.texto, peso: FontWeight = FontWeight.Normal,
    mono: Boolean = false, lineas: Int = Int.MAX_VALUE, modifier: Modifier = Modifier,
) = Text(
    s, modifier, color = color, maxLines = lineas, overflow = TextOverflow.Ellipsis,
    style = TextStyle(fontSize = t(tam), fontWeight = peso, fontFamily = if (mono) Mono else FontFamily.Default, lineHeight = t((tam * 1.25).toInt())),
)

private const val FICHAS = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:-→"

/** Una ficha del panel de paletas. Al cambiar, gira por varias letras antes de parar. */
@Composable
private fun Ficha(c: Char, tam: Int, color: Color) {
    var mostrado by remember { mutableStateOf(c) }
    val giro = remember { Animatable(0f) }
    val animar = Animar.current
    LaunchedEffect(c) {
        if (mostrado == c) return@LaunchedEffect
        if (!animar) { mostrado = c; return@LaunchedEffect }
        repeat(5) {
            mostrado = FICHAS.random()
            giro.snapTo(0f); giro.animateTo(1f, tween(45))
            delay(10)
        }
        mostrado = c
        giro.snapTo(0f); giro.animateTo(1f, tween(60))
    }
    Box(
        Modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(3.dp)).background(Oscura.celda)
            .width((tam * 0.72f * Escala.current).dp).heightIn(min = (tam * 1.35f * Escala.current).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            mostrado.toString(), color = color,
            modifier = Modifier.graphicsLayer { rotationX = (1f - giro.value) * 70f * if (giro.isRunning) 1f else 0f },
            style = TextStyle(fontSize = t(tam), fontFamily = Mono, fontWeight = FontWeight.Bold),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Oscura.raya))
    }
}

/** Texto en paletas tipo tablón de aeropuerto. */
@Composable
fun Paletas(s: String, tam: Int = 22, color: Color = C.ambar, modifier: Modifier = Modifier) {
    val enFicha = aFicha(color)
    Row(modifier.semantics { contentDescription = s }) {
        s.uppercase().forEach { Ficha(it, tam, enFicha) }
    }
}

/** Las fichas son negras en ambos temas: traduce el color del tema claro a su versión brillante. */
@Composable
@ReadOnlyComposable
private fun aFicha(c: Color): Color {
    val t = LocalPaleta.current
    val f = LocalFichas.current
    if (t == f) return c
    return when (c) {
        t.ambar -> f.ambar; t.texto -> f.texto; t.gris -> f.gris
        t.verde -> f.verde; t.rojo -> f.rojo; t.azul -> f.azul
        else -> c
    }
}

@Composable
fun Boton(
    texto: String, modifier: Modifier = Modifier, icono: ImageVector? = null,
    fondo: Color = C.boton, letra: Color = C.sobreBoton, onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 68.dp).clip(RoundedCornerShape(14.dp)).background(fondo)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        icono?.let { Icon(it, null, tint = letra, modifier = Modifier.size((28 * Escala.current).dp)); Spacer(Modifier.width(12.dp)) }
        Texto(texto, 20, letra, FontWeight.Bold)
    }
}

@Composable
fun BotonSecundario(texto: String, modifier: Modifier = Modifier, icono: ImageVector? = null, onClick: () -> Unit) =
    Boton(texto, modifier.border(2.dp, C.ambar, RoundedCornerShape(14.dp)), icono, C.panel, C.ambar, onClick)

@Composable
fun Seccion(titulo: String) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp, 26.dp).background(C.ambar))
        Spacer(Modifier.width(10.dp))
        Texto(titulo, 18, C.ambar, FontWeight.Bold, mono = true)
    }
}

@Composable
fun Tarjeta(onClick: (() -> Unit)? = null, contenido: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(C.panel)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(14.dp),
    ) { contenido() }
}

@Composable
@ReadOnlyComposable
fun colorEstado(e: Estado): Color = when (e) {
    Estado.CANCELADO, Estado.DESVIADO, Estado.RETRASADO -> C.rojo
    Estado.ATERRIZADO -> C.verde
    Estado.EN_VUELO, Estado.APROXIMANDO -> C.azul
    Estado.EMBARCANDO, Estado.PUERTA_CERRADA, Estado.FACTURACION -> C.ambar
    else -> C.gris
}

@Composable
fun EtiquetaEstado(e: Estado) {
    if (e == Estado.DESCONOCIDO) return
    val c = colorEstado(e)
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.18f)).border(2.dp, c, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Texto(e.etiqueta, 16, c, FontWeight.Bold, mono = true, lineas = 1)
    }
}

/** Logo de la aerolínea sobre fondo blanco; si no hay, sus siglas. */
@Composable
fun LogoAerolinea(iata: String?, alto: Int = 40, modifier: Modifier = Modifier) {
    val caja = modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, C.gris.copy(alpha = .35f), RoundedCornerShape(8.dp)).padding(4.dp)
        .width((alto * 2.3f * Escala.current).dp).height((alto * Escala.current).dp)
    val siglas = @Composable {
        Box(Modifier.width((alto * 2.3f * Escala.current).dp).height((alto * Escala.current).dp), contentAlignment = Alignment.Center) {
            Texto(iata ?: "✈", 18, Color.Black, FontWeight.Bold, mono = true)
        }
    }
    val url = Aerolineas.logo(iata)
    if (url == null) Box(caja, contentAlignment = Alignment.Center) { siglas() }
    else SubcomposeAsyncImage(model = url, contentDescription = Aerolineas.iata(iata)?.nombre ?: iata, modifier = caja,
        error = { siglas() }, loading = { siglas() })
}

/** Distintivo del fabricante: avión + nombre en su color de marca. */
@Composable
fun DistintivoFabricante(tipo: String?, fabricante: String?) {
    val m = Fabricantes.de(tipo, fabricante)
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color(m.color)).border(2.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_avion), null, tint = Color.White, modifier = Modifier.size((26 * Escala.current).dp).graphicsLayer { rotationZ = 90f })
        Spacer(Modifier.width(10.dp))
        Texto(m.nombre, 20, Color.White, FontWeight.Black, mono = true)
        tipo?.let { Spacer(Modifier.width(10.dp)); Texto(it, 16, Color.White.copy(alpha = .8f), mono = true) }
    }
}
