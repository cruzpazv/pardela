package io.github.cruzpazv.pardela.ui

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.EnDirecto
import io.github.cruzpazv.pardela.data.Formato
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.data.intentar
import io.github.cruzpazv.pardela.data.reciente
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/** ¿Hay algo que pintar? Hacen falta los dos aeropuertos o una posición del avión. */
fun hayMapa(v: Vuelo) = v.directo != null ||
    (Aeropuertos.iata(v.salida.lugar.iata) != null && Aeropuertos.iata(v.llegada.lugar.iata) != null)

/** Mapa pequeño para la ficha del vuelo. Al tocarlo se abre a pantalla completa. */
@Composable
fun MapaPequeno(v: Vuelo, abrir: () -> Unit) {
    Seccion("EN EL MAPA")
    Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp))) {
        MapaVuelo(v, interactivo = false, modifier = Modifier.matchParentSize())
        // Capa encima: el mapa pequeño no se mueve, solo se abre (así no estorba al deslizar la ficha).
        Box(Modifier.matchParentSize().clickable(onClickLabel = "Ver mapa grande", onClick = abrir))
        Box(
            Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(C.boton)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Texto("VER GRANDE", 15, C.sobreBoton, FontWeight.Bold) }
    }
}

/** Mapa a pantalla completa, con zoom y la posición actualizada cada 30 s. */
@Composable
fun MapaPantalla(inicial: Vuelo, atras: () -> Unit) {
    var v by remember { mutableStateOf(inicial) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (Repositorio.radarUtil(v)) intentar { Repositorio.refrescar(v, conClave = false) }.getOrNull()?.let { v = it }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera("VUELO ${v.numero} · MAPA", atras)
        val d = v.directo
        Texto(
            when {
                d == null -> "${v.salida.lugar.iata.ifBlank { "?" }} → ${v.llegada.lugar.iata.ifBlank { "?" }} · sin posición del radar"
                !d.reciente -> "Última posición conocida (el avión está fuera de la cobertura del radar)"
                d.enTierra -> "En tierra"
                else -> listOfNotNull(d.altitudPies?.let { Formato.altura(it) }, d.velocidadNudos?.let { Formato.velocidad(it) }).joinToString("  ·  ")
            },
            16, C.gris,
        )
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(12.dp))) {
            MapaVuelo(v, interactivo = true, modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * Mapa de OpenStreetMap con la ruta prevista (curva gris discontinua), el recorrido visto (ámbar)
 * y el avión orientado según su rumbo.
 */
@Composable
private fun MapaVuelo(v: Vuelo, interactivo: Boolean, modifier: Modifier) {
    val oscuro = C.fondo.luminance() < 0.5f
    val colores = Colores(C.ambar.toArgb(), C.gris.toArgb(), C.azul.toArgb())
    AndroidView(
        modifier = modifier,
        factory = { c ->
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(interactivo)
                isTilesScaledToDpi = true
                minZoomLevel = 2.0
                maxZoomLevel = 14.0
                isHorizontalMapRepetitionEnabled = true
                isVerticalMapRepetitionEnabled = false
            }
        },
        update = { m -> dibujar(m, v, Repositorio.rastro(v), oscuro, colores) },
        onRelease = { it.onDetach() },
    )
}

private data class Colores(val ambar: Int, val gris: Int, val azul: Int)

private fun dibujar(m: MapView, v: Vuelo, rastro: List<EnDirecto>, oscuro: Boolean, col: Colores) {
    val c = m.context
    m.overlayManager.tilesOverlay.setColorFilter(if (oscuro) TilesOverlay.INVERT_COLORS else null)
    m.overlays.clear()
    m.overlays.add(CopyrightOverlay(c))

    val origen = Aeropuertos.iata(v.salida.lugar.iata)?.let { GeoPoint(it.lat, it.lon) }
    val destino = Aeropuertos.iata(v.llegada.lugar.iata)?.let { GeoPoint(it.lat, it.lon) }
    val d = v.directo
    val avion = d?.let { GeoPoint(it.lat, it.lon) }

    fun linea(puntos: List<GeoPoint>, color: Int, ancho: Float, discontinua: Boolean) {
        if (puntos.size < 2) return
        m.overlays.add(Polyline(m).apply {
            setPoints(puntos)
            isGeodesic = true // ruta en curva (círculo máximo), como las de los aviones de verdad
            outlinePaint.color = color
            outlinePaint.strokeWidth = ancho * c.resources.displayMetrics.density
            if (discontinua) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
            setOnClickListener { _, _, _ -> true }
        })
    }

    if (d != null && avion != null) {
        // Recorrido: salida → posiciones vistas → ahora. Lo que falta: ahora → llegada.
        linea(listOfNotNull(origen) + rastro.map { GeoPoint(it.lat, it.lon) } + avion, col.ambar, 4f, false)
        if (!d.enTierra && destino != null) linea(listOf(avion, destino), col.gris, 3f, true)
    } else if (origen != null && destino != null) {
        linea(listOf(origen, destino), col.gris, 3f, true)
    }

    listOfNotNull(origen, destino).forEach { p -> m.overlays.add(punto(m, p, col.azul)) }

    if (d != null && avion != null) m.overlays.add(Marker(m).apply {
        position = avion
        icon = ContextCompat.getDrawable(c, R.drawable.ic_avion)?.mutate()?.apply { setTint(col.ambar) }
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        // osmdroid gira en sentido contrario a las agujas del reloj; el rumbo va en el sentido de las agujas.
        rotation = -(d.rumbo ?: 0.0).toFloat()
        isFlat = true
        setInfoWindow(null)
        setOnMarkerClickListener { _, _ -> true }
    })

    // Encuadre: solo la primera vez, para no deshacer el zoom que haga el usuario.
    if (m.tag != v.clave) {
        m.tag = v.clave
        val todos = listOfNotNull(origen, destino, avion)
        val encuadrar: () -> Unit = {
            if (todos.size == 1) { m.controller.setZoom(8.0); m.controller.setCenter(todos[0]) }
            else if (todos.isNotEmpty()) m.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(todos).increaseByScale(1.4f), false)
        }
        if (m.width > 0) encuadrar() else m.addOnFirstLayoutListener { _, _, _, _, _ -> encuadrar() }
    }
    m.invalidate()
}

/** Círculo pequeño para marcar un aeropuerto. */
private fun punto(m: MapView, p: GeoPoint, color: Int) = Marker(m).apply {
    position = p
    val tam = (14 * m.context.resources.displayMetrics.density).toInt()
    icon = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(tam / 5, android.graphics.Color.WHITE)
        setSize(tam, tam)
    }
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    setInfoWindow(null)
    setOnMarkerClickListener { _, _ -> true }
}

/** Prepara osmdroid: identificación ante OpenStreetMap y caché dentro de la app (se puede borrar). */
fun prepararMapa(ctx: Context, agente: String) {
    org.osmdroid.config.Configuration.getInstance().apply {
        load(ctx, ctx.getSharedPreferences("mapa", Context.MODE_PRIVATE))
        userAgentValue = agente
        osmdroidBasePath = java.io.File(ctx.cacheDir, "mapa")
        osmdroidTileCache = java.io.File(ctx.cacheDir, "mapa/teselas")
    }
}
