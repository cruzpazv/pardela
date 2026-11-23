package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.tx

import io.github.cruzpazv.pardela.data.Aerolineas
import io.github.cruzpazv.pardela.data.intentar

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import io.github.cruzpazv.pardela.data.Resumen
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.notif.VigilanciaService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cuándo se gastó la clave por última vez en cada vuelo (solo en esta sesión). */
private val ultimaClave = HashMap<String, Long>()

@Composable
fun Detalle(inicial: Vuelo, atras: () -> Unit, verHistorial: (Avion) -> Unit, verMapa: (Vuelo) -> Unit) {
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

    val voz = rememberVoz()
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { VigilanciaService.empezar(ctx, v) }

    var primera by remember { mutableStateOf(true) }

    suspend fun refrescar() {
        cargando = true
        val ahora = System.currentTimeMillis()
        // La primera vez solo radar: el vuelo llega recién buscado o actualizado por el comprobador.
        if (primera) { primera = false; ultimaClave.putIfAbsent(v.clave, ahora) }
        val cada = if (Almacen.cuotaBaja) 40 * 60_000L else 10 * 60_000L // con pocas consultas, más espaciadas
        val usarClave = v.conHorarios && Almacen.tieneClave && ahora - (ultimaClave[v.clave] ?: 0L) > cada
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
        Cabecera(tx(R.string.vuelo_x_2, v.numero), atras) {
            IconButton(onClick = { scope.launch { refrescar() } }) {
                Icon(Icons.Default.Refresh, tx(R.string.actualizar), tint = if (cargando) C.gris else C.ambar, modifier = Modifier.size(34.dp))
            }
        }

        if (Aerolineas.posicionamiento(v.numero)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(C.ambar.copy(alpha = .15f)).padding(14.dp)) {
                Texto(tx(R.string.i_seguramente_es_un_vuelo_de_posiciona) +
                    tx(R.string.no_se_vende_al_publico_por_eso_puede_f), 17, C.texto)
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
                if (v.estado == Estado.DESCONOCIDO) Texto(tx(R.string.sin_datos_ahora_mismo), 18, C.gris, mono = true)
                else Paletas(v.estado.texto.replace("Ó", "O").replace("Ñ", "N"), 20, colorEstado(v.estado))
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonSecundario(if (voz.hablando) tx(R.string.parar) else tx(R.string.escuchar), Modifier.weight(1f)) { voz.alternar(Resumen.voz(v)) }
            BotonSecundario(tx(R.string.compartir), Modifier.weight(1f), icono = Icons.Default.Share) { compartirVuelo(ctx, v) }
        }
        voz.falta?.let { idioma ->
            Texto(tx(R.string.falta_voz_x, idioma.getDisplayLanguage(idioma)), 16, C.rojo, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            BotonSecundario(tx(R.string.instalar_la_voz), Modifier.padding(top = 6.dp)) { instalarVoz(ctx) }
        }

        Progreso(v)
        val sencillo = datos.ajustes.modoSencillo
        if (hayMapa(v) && !sencillo) MapaPequeno(v) { verMapa(v) }

        if (v.conHorarios) {
            Seccion(tx(R.string.salida))
            Horario(v.salida, salida = true)
            Seccion(tx(R.string.llegada))
            Horario(v.llegada, salida = false)
        } else {
            Spacer(Modifier.height(10.dp))
            Texto(tx(R.string.i_horarios_puertas_y_retrasos_anada_un), 15, C.gris)
        }
        EnDestino(v)

        Spacer(Modifier.height(22.dp))
        if (seguido) BotonSecundario(tx(R.string.dejar_de_seguir), icono = Icons.Default.Close) { Almacen.dejar(v.numero, v.fecha) }
        else Boton(tx(R.string.seguir_este_vuelo), icono = Icons.Default.Star) { Almacen.seguir(v) }
        Texto(tx(R.string.le_avisaremos_de_despegue_aterrizaje_r), 15, C.gris, modifier = Modifier.padding(4.dp))
        if (puedeIrAlCalendario(v)) {
            Spacer(Modifier.height(8.dp))
            BotonSecundario(tx(R.string.anadir_al_calendario), icono = Icons.Default.DateRange) { anadirAlCalendario(ctx, v) }
        }

        Spacer(Modifier.height(12.dp))
        if (esteVigilado) BotonSecundario(tx(R.string.dejar_de_vigilar), icono = Icons.Default.Close) { VigilanciaService.parar(ctx) }
        else if (!Repositorio.radarUtil(v) && !v.estado.terminado) Texto(tx(R.string.podra_vigilarlo_en_directo_el_dia_del), 16, C.gris, modifier = Modifier.padding(4.dp))
        else Boton(tx(R.string.vigilar_ahora), icono = Icons.Default.Notifications, fondo = C.azul, letra = C.sobreAzul) {
            if (Build.VERSION.SDK_INT >= 33) permiso.launch(Manifest.permission.POST_NOTIFICATIONS) else VigilanciaService.empezar(ctx, v)
        }
        if (esteVigilado || Repositorio.radarUtil(v) || v.estado.terminado) Texto(tx(R.string.aviso_fijo_en_la_pantalla_del_movil_qu), 15, C.gris, modifier = Modifier.padding(4.dp))

        // Modo sencillo: sin ficha del avión ni enlaces a otras webs.
        if (!sencillo) {
        Spacer(Modifier.height(12.dp))
        BotonSecundario(if (verAvion) tx(R.string.ocultar_el_avion) else tx(R.string.ver_el_avion), icono = Icons.Default.Info) { verAvion = !verAvion }
        if (verAvion) FichaAvion(v, conAvion, verHistorial)

        Seccion(tx(R.string.ver_en_otras_webs))
        Texto(tx(R.string.se_abre_en_el_navegador_del_movil), 15, C.gris, modifier = Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            enlacesExternos(v).forEach { (nombre, url) ->
                BotonSecundario(nombre, icono = Icons.AutoMirrored.Filled.ExitToApp) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }
        }
        }
        Spacer(Modifier.height(30.dp))
    }
}

/**
 * El mismo vuelo en webs conocidas. Flightradar24 y FlightAware buscan por número;
 * adsb.lol (la fuente de radar de Pardela) por el código del avión, y muestra su recorrido.
 */
private fun enlacesExternos(v: Vuelo): List<Pair<String, String>> {
    val numero = v.numero.lowercase()
    val indicativo = v.indicativo ?: Aerolineas.indicativo(v.numero) ?: v.numero
    return listOfNotNull(
        "FLIGHTRADAR24" to "https://www.flightradar24.com/data/flights/$numero",
        "FLIGHTAWARE" to "https://www.flightaware.com/live/flight/${indicativo.uppercase()}",
        v.avion.hex?.let { tx(R.string.adsb_lol_mapa_en_directo) to "https://adsb.lol/?icao=${it.lowercase()}" },
    )
}

@Composable
private fun Progreso(v: Vuelo) {
    val d = v.directo ?: return
    val p = Repositorio.progreso(v)
    Seccion(tx(R.string.en_directo))
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
            Texto(tx(R.string.sin_senal_de_radar_desde_hace_x_es_nor, if (minutos >= 60) "${minutos / 60} h ${minutos % 60} min" else "$minutos min"), 16, C.gris)
            return@Tarjeta
        }
        val filas = buildList {
            if (d.enTierra) add(tx(R.string.en_tierra) to "")
            d.altitudPies?.takeIf { !d.enTierra }?.let { add(tx(R.string.altura) to Formato.altura(it, Almacen.ajustes.unidades)) }
            d.velocidadNudos?.let { add(tx(R.string.velocidad) to Formato.velocidad(it, Almacen.ajustes.unidades)) }
            Repositorio.minutosRestantes(v)?.takeIf { !d.enTierra }?.let {
                add(tx(R.string.llega_en) to if (it >= 60) tx(R.string.x_h_x_min, it / 60, it % 60) else tx(R.string.x_min, it))
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
                Texto(tx(R.string.hora), 15, C.gris, mono = true)
                Paletas(e.hora ?: "--:--", 26)
            }
            val r = e.retraso
            if (e.horaPrevista != null && e.horaPrevista != e.hora) Column(horizontalAlignment = Alignment.End) {
                Texto(if (salida) tx(R.string.sale_a_las) else tx(R.string.llega_a_las), 15, C.gris, mono = true)
                Paletas(e.horaPrevista, 26, if ((r ?: 0) >= 15) C.rojo else C.verde)
            }
        }
        // La hora nueva siempre va con texto, no solo con color.
        val r2 = e.retraso
        when {
            r2 != null && r2 >= 5 -> Texto(tx(R.string.retraso_x_min, r2), 18, C.rojo, FontWeight.Bold)
            r2 != null && r2 <= -5 -> Texto(tx(R.string.adelantado_x_min, -r2), 18, C.verde, FontWeight.Bold)
            e.horaPrevista != null -> Texto(tx(R.string.a_su_hora), 18, C.verde, FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Dato(tx(R.string.terminal), e.terminal)
        Dato(if (salida) tx(R.string.puerta) else tx(R.string.puerta_llegada), e.puerta)
        if (!salida) Dato(tx(R.string.cinta_equipaje), e.cinta)
    }
}

@Composable
private fun FichaAvion(v: Vuelo, completo: Boolean, verHistorial: (Avion) -> Unit) {
    val a = v.avion
    Seccion(tx(R.string.el_avion))
    Tarjeta {
        if (!completo) { Cargando(tx(R.string.cargando)); return@Tarjeta }
        if (a.hex == null && a.matricula == null && a.modelo == null) {
            Texto(tx(R.string.todavia_no_hay_informacion_del_avion_d), 18, C.texto, FontWeight.Bold)
            Texto(tx(R.string.la_aerolinea_suele_asignar_el_avion_el), 16, C.gris, modifier = Modifier.padding(top = 4.dp))
            return@Tarjeta
        }
        a.foto?.let { url ->
            AsyncImage(
                model = url, contentDescription = tx(R.string.foto_del_avion),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(10.dp)),
            )
            Texto(tx(R.string.foto_x_planespotters_net, a.fotoAutor ?: "—"), 13, C.gris, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        } ?: Box(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(C.celda).padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_avion), null, tint = C.gris, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(6.dp))
                // Solo se sabe el modelo: aún no hay un avión concreto (matrícula) del que buscar foto.
                val sinAvion = a.matricula == null && a.hex == null
                Texto(if (sinAvion) tx(R.string.aun_no_se_sabe_que_avion_hara_este_vue) else tx(R.string.no_hay_foto_de_este_avion), 18, Oscura.texto, FontWeight.Bold)
                Texto(
                    if (sinAvion) tx(R.string.la_aerolinea_solo_ha_indicado_el_model)
                    else tx(R.string.planespotters_net_todavia_no_tiene_nin),
                    15, Oscura.gris,
                )
            }
        }
        DistintivoFabricante(a.tipoIcao, a.fabricante)
        Spacer(Modifier.height(10.dp))
        val nombre = Fabricantes.modelo(a.tipoIcao) ?: listOfNotNull(a.fabricante, a.modelo).joinToString(" ").ifBlank { null }
        nombre?.let { Texto(it, 22, C.texto, FontWeight.Bold) }
        Spacer(Modifier.height(6.dp))
        Dato(tx(R.string.matricula), a.matricula)
        Dato(tx(R.string.modelo_exacto), a.modelo)
        Dato(tx(R.string.codigo), a.tipoIcao)
        Dato(tx(R.string.propietario), a.propietario)
        Dato(tx(R.string.codigo_radar), a.hex?.uppercase())
        if (a.matricula != null || a.hex != null) {
            Spacer(Modifier.height(12.dp))
            BotonSecundario(tx(R.string.vuelos_anteriores_de_este_avion), icono = Icons.Default.DateRange) { verHistorial(a) }
        }
    }
}
