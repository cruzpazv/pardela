package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.tx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import io.github.cruzpazv.pardela.data.Aerolineas
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Formato
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.data.intentar
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.coroutines.resume

/**
 * Aviones en el aire cerca de un punto: un aeropuerto ([lat]/[lon] dados) o la posición del móvil (null).
 * Solo radar gratuito: no gasta la clave.
 */
@Composable
fun AvionesCerca(lat: Double?, lon: Double?, titulo: String, atras: () -> Unit, abrir: (Vuelo) -> Unit) {
    val ctx = LocalContext.current
    var centro by remember { mutableStateOf(if (lat != null && lon != null) GeoPoint(lat, lon) else null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var buscando by remember { mutableStateOf(false) }
    var lista by remember { mutableStateOf<List<Repositorio.Cercano>?>(null) }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) aviso = tx(R.string.sin_permiso_de_ubicacion_puede_elegir)
        else buscando = true
    }
    // Pedir la posición (aproximada basta: solo hace falta saber la zona).
    LaunchedEffect(buscando) {
        if (!buscando) return@LaunchedEffect
        val l = ubicacion(ctx)
        if (l != null) centro = GeoPoint(l.latitude, l.longitude) else aviso = tx(R.string.no_se_ha_podido_saber_donde_esta_tiene)
        buscando = false
    }
    LaunchedEffect(Unit) {
        if (centro == null) {
            if (tienePermiso(ctx)) buscando = true else permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    // Radar cada 30 s.
    LaunchedEffect(centro) {
        val c = centro ?: return@LaunchedEffect
        while (true) {
            intentar { Repositorio.cerca(c.latitude, c.longitude) }.getOrNull()?.let { lista = it }
            delay(30_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera(titulo, atras)
        val c = centro
        when {
            c != null -> {
                Texto(tx(R.string.aviones_en_el_aire_a_menos_de_unos_50), 16, C.gris)
                Spacer(Modifier.height(8.dp))
                val visibles = lista?.let { l -> l.filter { it.vuelo in Aerolineas.visibles(l.map { a -> a.vuelo }) } }
                MapaCerca(c, visibles.orEmpty(), Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))) { abrir(it) }
                Spacer(Modifier.height(8.dp))
                val l = visibles
                when {
                    l == null -> Cargando()
                    l.isEmpty() -> Texto(tx(R.string.ahora_mismo_no_hay_aviones_a_la_vista), 17, C.gris)
                    else -> LazyColumn(Modifier.weight(1f)) { items(l) { a -> FilaCercano(a) { abrir(a.vuelo) } } }
                }
            }
            buscando -> Cargando(tx(R.string.buscando_su_posicion))
            else -> {
                aviso?.let { AvisoError(it) }
                Boton(tx(R.string.usar_mi_ubicacion), icono = Icons.Default.LocationOn) {
                    aviso = null
                    if (tienePermiso(ctx)) buscando = true else permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                val datos by Almacen.datos.collectAsState()
                if (datos.aeropuertos.isNotEmpty()) {
                    Seccion(tx(R.string.o_cerca_de_un_aeropuerto))
                    datos.aeropuertos.mapNotNull { Aeropuertos.iata(it) }.forEach { a ->
                        BotonSecundario("${a.iata} · ${a.ciudad.uppercase()}", Modifier.padding(bottom = 8.dp), icono = Icons.Default.Place) {
                            centro = GeoPoint(a.lat, a.lon)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaCercano(a: Repositorio.Cercano, onClick: () -> Unit) {
    val v = a.vuelo
    val d = v.directo
    val u = Almacen.ajustes.unidades
    Tarjeta(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoAerolinea(v.aerolineaIata, 28, nombre = v.aerolineaNombre)
            Spacer(Modifier.width(10.dp))
            Texto(v.numero, 20, C.texto, FontWeight.Bold, mono = true, lineas = 1)
        }
        Spacer(Modifier.height(6.dp))
        val ruta = if (v.salida.lugar.iata.isBlank() && v.llegada.lugar.iata.isBlank()) tx(R.string.ruta_desconocida)
        else "${v.salida.lugar.ciudad.ifBlank { v.salida.lugar.iata.ifBlank { "?" } }} → ${v.llegada.lugar.ciudad.ifBlank { v.llegada.lugar.iata.ifBlank { "?" } }}"
        Texto(ruta.uppercase(), 18, C.ambar, FontWeight.Bold, mono = true)
        Texto(
            listOfNotNull(
                d?.altitudPies?.let { Formato.altura(it, u) },
                d?.velocidadNudos?.let { Formato.velocidad(it, u) },
                tx(R.string.a_x_km, Math.round(a.km)),
            ).joinToString("  ·  "),
            16, C.gris,
        )
    }
}

/** Mapa con los aviones cercanos, girados según su rumbo. Tocar uno abre su vuelo. */
@Composable
internal fun MapaCerca(centro: GeoPoint, aviones: List<Repositorio.Cercano>, modifier: Modifier, abrir: (Vuelo) -> Unit) {
    val oscuro = C.fondo.luminance() < 0.5f
    val ambar = C.ambar.toArgb()
    val azul = C.azul.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { c ->
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                minZoomLevel = 5.0
                maxZoomLevel = 14.0
            }
        },
        update = { m ->
            m.overlayManager.tilesOverlay.setColorFilter(if (oscuro) TilesOverlay.INVERT_COLORS else null)
            m.overlays.clear()
            m.overlays.add(CopyrightOverlay(m.context))
            m.overlays.add(Marker(m).apply {
                position = centro
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(azul); setStroke(4, android.graphics.Color.WHITE)
                    val t = (16 * m.context.resources.displayMetrics.density).toInt(); setSize(t, t)
                }
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> true }
            })
            aviones.forEach { a ->
                val d = a.vuelo.directo ?: return@forEach
                m.overlays.add(Marker(m).apply {
                    position = GeoPoint(d.lat, d.lon)
                    icon = ContextCompat.getDrawable(m.context, R.drawable.ic_avion)?.mutate()?.apply { setTint(ambar) }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    rotation = -(d.rumbo ?: 0.0).toFloat()
                    isFlat = true
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> abrir(a.vuelo); true }
                })
            }
            // Encuadre solo al cambiar de sitio, para no deshacer el zoom del usuario.
            val clave = "${centro.latitude},${centro.longitude}"
            if (m.tag != clave) {
                m.tag = clave
                m.controller.setZoom(9.0)
                m.controller.setCenter(centro)
            }
            m.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

internal fun tienePermiso(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Última posición conocida o, si no hay, una nueva (red o GPS, sin Google Play Services). */
@Suppress("MissingPermission")
internal suspend fun ubicacion(ctx: Context): Location? {
    if (!tienePermiso(ctx)) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val reciente = intentar {
        lm.getProviders(true).mapNotNull { p -> intentar { lm.getLastKnownLocation(p) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 30 * 60_000L }.maxByOrNull { it.time }
    }.getOrNull()
    if (reciente != null) return reciente
    val proveedor = when {
        Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> return null
    }
    return kotlinx.coroutines.withTimeoutOrNull(20_000) {
        suspendCancellableCoroutine { cont ->
            val fin: (Location?) -> Unit = { l -> if (cont.isActive) cont.resume(l) }
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    lm.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(ctx)) { fin(it) }
                } else {
                    // Android 8-10: todos los métodos implementados (en esas versiones no tienen cuerpo por defecto).
                    val oyente = object : LocationListener {
                        override fun onLocationChanged(l: Location) = fin(l)
                        @Deprecated("Android < 10") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) = fin(null)
                    }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(proveedor, oyente, Looper.getMainLooper())
                    cont.invokeOnCancellation { lm.removeUpdates(oyente) }
                }
            } catch (e: Exception) {
                fin(null)
            }
        }
    }
}
