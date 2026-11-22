package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.data.Aerolineas
import io.github.cruzpazv.pardela.data.intentar

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Avion
import io.github.cruzpazv.pardela.data.Estado
import io.github.cruzpazv.pardela.data.Extremo
import io.github.cruzpazv.pardela.data.Fabricantes
import io.github.cruzpazv.pardela.data.Formato
import io.github.cruzpazv.pardela.data.reciente
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.notif.VigilanciaService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cuándo se gastó la clave por última vez en cada vuelo (solo en esta sesión). */
private val ultimaClave = HashMap<String, Long>()

@Composable
fun Detalle(inicial: Vuelo, atras: () -> Unit, verHistorial: (Avion) -> Unit) {
    val ctx = LocalContext.current
    val datos by Almacen.datos.collectAsState()
    val vigilando by VigilanciaService.vigilando.collectAsState()
    var v by remember { mutableStateOf(inicial) }
    var cargando by remember { mutableStateOf(false) }
    var verAvion by remember { mutableStateOf(false) }
    var conAvion by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val seguido = datos.vuelos.any { it.numero == v.numero && it.fecha == v.fecha }
    val esteVigilado = vigilando == v.clave

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { VigilanciaService.empezar(ctx, v) }

    var primera by remember { mutableStateOf(true) }

    suspend fun refrescar() {
        cargando = true
        val ahora = System.currentTimeMillis()
        // La primera vez solo radar: el vuelo llega recién buscado o actualizado por el comprobador.
        if (primera) { primera = false; ultimaClave.putIfAbsent(v.clave, ahora) }
        val usarClave = v.conHorarios && ahora - (ultimaClave[v.clave] ?: 0L) > 10 * 60_000L
        intentar { Repositorio.refrescar(v, usarClave) }.getOrNull()?.let { n ->
            if (usarClave) ultimaClave[v.clave] = ahora
            v = if (conAvion) n.copy(avion = v.avion.copy(hex = n.avion.hex ?: v.avion.hex)) else n
            if (Almacen.sigue(v)) Almacen.actualizar(v)
        }
        cargando = false
    }

    LaunchedEffect(Unit) { while (true) { refrescar(); delay(60_000) } }
    LaunchedEffect(verAvion) {
        // Aunque falle, se deja de mostrar "cargando" y se enseña lo que haya.
        if (verAvion && !conAvion) { intentar { Repositorio.completarAvion(v) }.getOrNull()?.let { v = it }; conAvion = true }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Cabecera("VUELO ${v.numero}", atras) {
            IconButton(onClick = { scope.launch { refrescar() } }) {
                Icon(Icons.Default.Refresh, "Actualizar", tint = if (cargando) C.gris else C.ambar, modifier = Modifier.size(34.dp))
            }
        }

        if (Aerolineas.posicionamiento(v.numero)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(C.ambar.copy(alpha = .15f)).padding(14.dp)) {
                Texto("ⓘ Seguramente es un vuelo de posicionamiento: el avión vuela vacío para empezar el día en otra isla. " +
                    "No se vende al público, por eso puede faltar información.", 17, C.texto)
            }
        }
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoAerolinea(v.aerolineaIata, 44, nombre = v.aerolineaNombre)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Paletas(v.numero, 24)
                    v.aerolineaNombre?.let { Texto(it, 16, C.gris, lineas = 1) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Paletas(v.salida.lugar.iata.ifBlank { "???" }, 30)
                Icon(painterResource(R.drawable.ic_avion), null, tint = C.ambar, modifier = Modifier.size(36.dp).graphicsLayer { rotationZ = 90f })
                Paletas(v.llegada.lugar.iata.ifBlank { "???" }, 30)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Texto(v.salida.lugar.ciudad.uppercase(), 16, C.texto, lineas = 2, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Texto(v.llegada.lugar.ciudad.uppercase(), 16, C.texto, lineas = 2, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (v.estado == Estado.DESCONOCIDO) Texto("SIN DATOS AHORA MISMO", 18, C.gris, mono = true)
                else Paletas(v.estado.texto.replace("Ó", "O").replace("Ñ", "N"), 20, colorEstado(v.estado))
            }
        }

        Progreso(v)

        if (v.conHorarios) {
            Seccion("SALIDA")
            Horario(v.salida, salida = true)
            Seccion("LLEGADA")
            Horario(v.llegada, salida = false)
        } else {
            Spacer(Modifier.height(10.dp))
            Texto("ℹ Horarios, puertas y retrasos: añada una clave API en AJUSTES.", 15, C.gris)
        }

        Spacer(Modifier.height(22.dp))
        if (seguido) BotonSecundario("DEJAR DE SEGUIR", icono = Icons.Default.Close) { Almacen.dejar(v.numero, v.fecha) }
        else Boton("SEGUIR ESTE VUELO", icono = Icons.Default.Star) { Almacen.seguir(v) }
        Texto("Le avisaremos de despegue, aterrizaje, retrasos y puerta.", 15, C.gris, modifier = Modifier.padding(4.dp))

        Spacer(Modifier.height(12.dp))
        if (esteVigilado) BotonSecundario("DEJAR DE VIGILAR", icono = Icons.Default.Close) { VigilanciaService.parar(ctx) }
        else if (!Repositorio.radarUtil(v) && !v.estado.terminado) Texto("Podrá vigilarlo en directo el día del vuelo, unas horas antes de salir.", 16, C.gris, modifier = Modifier.padding(4.dp))
        else Boton("VIGILAR AHORA", icono = Icons.Default.Notifications, fondo = C.azul, letra = C.sobreAzul) {
            if (Build.VERSION.SDK_INT >= 33) permiso.launch(Manifest.permission.POST_NOTIFICATIONS) else VigilanciaService.empezar(ctx, v)
        }
        if (esteVigilado || Repositorio.radarUtil(v) || v.estado.terminado) Texto("Aviso fijo en la pantalla del móvil que se actualiza cada minuto.", 15, C.gris, modifier = Modifier.padding(4.dp))

        Spacer(Modifier.height(12.dp))
        BotonSecundario(if (verAvion) "OCULTAR EL AVIÓN" else "VER EL AVIÓN", icono = Icons.Default.Info) { verAvion = !verAvion }
        if (verAvion) FichaAvion(v, conAvion, verHistorial)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun Progreso(v: Vuelo) {
    val d = v.directo ?: return
    val p = Repositorio.progreso(v)
    Seccion("EN DIRECTO")
    Tarjeta {
        if (p != null && !d.enTierra) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp)) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).background(C.celda))
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(p).height(4.dp).background(C.ambar))
                Icon(
                    painterResource(R.drawable.ic_avion), null, tint = C.ambar,
                    modifier = Modifier.size(36.dp).offset(x = (maxWidth - 36.dp) * p).graphicsLayer { rotationZ = 90f },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        val minutos = ((System.currentTimeMillis() - d.momento) / 60_000L).toInt()
        if (!d.reciente) {
            Texto("Sin señal de radar desde hace ${if (minutos >= 60) "${minutos / 60} h ${minutos % 60} min" else "$minutos min"}. Es normal sobre el mar: se muestra la última posición conocida.", 16, C.gris)
            return@Tarjeta
        }
        val filas = buildList {
            if (d.enTierra) add("EN TIERRA" to "")
            d.altitudPies?.takeIf { !d.enTierra }?.let { add("ALTURA" to Formato.altura(it, Almacen.ajustes.unidades)) }
            d.velocidadNudos?.let { add("VELOCIDAD" to Formato.velocidad(it, Almacen.ajustes.unidades)) }
            Repositorio.minutosRestantes(v)?.takeIf { !d.enTierra }?.let {
                add("LLEGA EN" to if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min")
            }
        }
        filas.forEach { (k, valor) -> Dato(k, valor) }
    }
}

@Composable
private fun Dato(k: String, valor: String?) {
    if (valor == null) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Texto(k, 17, C.gris, mono = true, modifier = Modifier.weight(1f))
        Texto(valor, 21, C.texto, FontWeight.Bold, mono = true)
    }
}

@Composable
private fun Horario(e: Extremo, salida: Boolean) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Texto("HORA", 15, C.gris, mono = true)
                Paletas(e.hora ?: "--:--", 26)
            }
            val r = e.retraso
            if (e.horaPrevista != null && e.horaPrevista != e.hora) Column(horizontalAlignment = Alignment.End) {
                Texto(if (salida) "SALE A LAS" else "LLEGA A LAS", 15, C.gris, mono = true)
                Paletas(e.horaPrevista, 26, if ((r ?: 0) >= 15) C.rojo else C.verde)
            }
        }
        // La hora nueva siempre va con texto, no solo con color.
        val r2 = e.retraso
        when {
            r2 != null && r2 >= 5 -> Texto("⚠ Retraso: $r2 min", 18, C.rojo, FontWeight.Bold)
            r2 != null && r2 <= -5 -> Texto("✔ Adelantado ${-r2} min", 18, C.verde, FontWeight.Bold)
            e.horaPrevista != null -> Texto("✔ A su hora", 18, C.verde, FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Dato("TERMINAL", e.terminal)
        Dato(if (salida) "PUERTA" else "PUERTA LLEGADA", e.puerta)
        if (!salida) Dato("CINTA EQUIPAJE", e.cinta)
    }
}

@Composable
private fun FichaAvion(v: Vuelo, completo: Boolean, verHistorial: (Avion) -> Unit) {
    val a = v.avion
    Seccion("EL AVIÓN")
    Tarjeta {
        if (!completo) { Cargando("CARGANDO…"); return@Tarjeta }
        if (a.hex == null && a.matricula == null && a.modelo == null) {
            Texto("La aerolínea aún no ha asignado avión a este vuelo.", 18, C.gris); return@Tarjeta
        }
        a.foto?.let { url ->
            AsyncImage(
                model = url, contentDescription = "Foto del avión",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(10.dp)),
            )
            Texto("Foto: ${a.fotoAutor ?: "—"} · planespotters.net", 13, C.gris, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        } ?: Box(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(C.celda).padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_avion), null, tint = C.gris, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(6.dp))
                Texto("No hay foto de este avión", 18, Oscura.texto, FontWeight.Bold)
                Texto("planespotters.net todavía no tiene ninguna.", 15, Oscura.gris)
            }
        }
        DistintivoFabricante(a.tipoIcao, a.fabricante)
        Spacer(Modifier.height(10.dp))
        val nombre = Fabricantes.modelo(a.tipoIcao) ?: listOfNotNull(a.fabricante, a.modelo).joinToString(" ").ifBlank { null }
        nombre?.let { Texto(it, 22, C.texto, FontWeight.Bold) }
        Spacer(Modifier.height(6.dp))
        Dato("MATRÍCULA", a.matricula)
        Dato("MODELO EXACTO", a.modelo)
        Dato("CÓDIGO", a.tipoIcao)
        Dato("PROPIETARIO", a.propietario)
        Dato("CÓDIGO RADAR", a.hex?.uppercase())
        if (a.matricula != null || a.hex != null) {
            Spacer(Modifier.height(12.dp))
            BotonSecundario("VUELOS ANTERIORES DE ESTE AVIÓN", icono = Icons.Default.DateRange) { verHistorial(a) }
        }
    }
}
