package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx
import io.github.cruzpazv.pardela.Idioma

import io.github.cruzpazv.pardela.data.intentar
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import io.github.cruzpazv.pardela.data.Ajustes
import io.github.cruzpazv.pardela.data.Extremo
import io.github.cruzpazv.pardela.data.Lugar
import io.github.cruzpazv.pardela.data.Unidades
import java.time.LocalDate
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Datos
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Tema
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.notif.Avisos
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector

/** Apartados de Ajustes: cada uno en su propia pantalla, para que no haya demasiado a la vez. */
enum class SeccionAjustes { ASPECTO, AVISOS, CLAVE, COPIA, DATOS }

/** Menú principal de Ajustes: un apartado por fila, con un resumen de cómo está. */
@Composable
fun AjustesPantalla(atras: () -> Unit, abrir: (SeccionAjustes) -> Unit) {
    val ctx = LocalContext.current
    val datos by Almacen.datos.collectAsState()
    val aj = datos.ajustes
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Cabecera(tx(R.string.ajustes), atras)
        Apartado(tx(R.string.aspecto_y_unidades), Icons.Default.Edit, listOf(
            if (aj.textoGrande) tx(R.string.letra_grande) else tx(R.string.letra_normal),
            tx(R.string.tema_x, aj.tema.texto.lowercase()),
            if (aj.unidades == Unidades.METRICO) tx(R.string.metros_y_km_h) else tx(R.string.pies_y_nudos),
        ).joinToString(" · ") + if (aj.daltonismo) tx(R.string.colores_para_daltonismo) else "") { abrir(SeccionAjustes.ASPECTO) }

        val tipos = listOf(aj.avisoEmbarque, aj.avisoDespegue, aj.avisoAterrizaje, aj.avisoRetraso, aj.avisoPuerta, aj.avisoCinta).count { it }
        val resumenAvisos = if (!Avisos.puede(ctx)) tx(R.string.desactivados_en_el_movil) else listOfNotNull(
            tx(R.string.x_de_6_tipos, tipos),
            if (aj.recordatorio == 0) null else if (aj.recordatorio == 24) tx(R.string.recordatorio_1_dia_antes) else tx(R.string.recordatorio_x_h_antes, aj.recordatorio),
            if (aj.silencio) tx(R.string.silencio_de_02d_00_a_02d_00, aj.silencioDesde, aj.silencioHasta) else null,
        ).joinToString(" · ")
        Apartado(tx(R.string.avisos), Icons.Default.Notifications, resumenAvisos, alerta = !Avisos.puede(ctx)) { abrir(SeccionAjustes.AVISOS) }

        Apartado(tx(R.string.clave_api), Icons.Default.Lock,
            if (aj.claveApi.isEmpty()) tx(R.string.sin_clave_funciona_con_radar_gratuito)
            else tx(R.string.guardada) + (aj.cuotaRestante?.let { tx(R.string.quedan_x_consultas, it) } ?: ""),
        ) { abrir(SeccionAjustes.CLAVE) }
        Apartado(tx(R.string.copia_de_seguridad), Icons.Default.Share, tx(R.string.pasar_sus_datos_a_otro_movil)) { abrir(SeccionAjustes.COPIA) }
        Apartado(tx(R.string.datos_y_privacidad), Icons.Default.Info, tx(R.string.de_donde_salen_los_datos_borrar_todo)) { abrir(SeccionAjustes.DATOS) }
    }
}

@Composable
private fun Apartado(titulo: String, icono: ImageVector, resumen: String, alerta: Boolean = false, onClick: () -> Unit) {
    Tarjeta(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, null, tint = C.ambar, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Texto(titulo, 20, C.texto, FontWeight.Bold)
                Texto(resumen, 15, if (alerta) C.rojo else C.gris)
            }
            Texto("›", 34, C.ambar, FontWeight.Bold)
        }
    }
}

/** Pantalla de un apartado de Ajustes. */
@Composable
fun AjustesSeccion(seccion: SeccionAjustes, atras: () -> Unit) {
    val datos by Almacen.datos.collectAsState()
    val aj = datos.ajustes
    fun ajustar(f: (Ajustes) -> Ajustes) { Almacen.ajustar(f) }
    val titulo = when (seccion) {
        SeccionAjustes.ASPECTO -> tx(R.string.aspecto)
        SeccionAjustes.AVISOS -> tx(R.string.avisos)
        SeccionAjustes.CLAVE -> tx(R.string.clave_api)
        SeccionAjustes.COPIA -> tx(R.string.copia_de_seguridad)
        SeccionAjustes.DATOS -> tx(R.string.datos)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Cabecera(titulo, atras)
        when (seccion) {
            SeccionAjustes.ASPECTO -> {
                Interruptor(tx(R.string.modo_sencillo), aj.modoSencillo, tx(R.string.modo_sencillo_detalle)) { v -> ajustar { it.copy(modoSencillo = v) } }
                Interruptor(tx(R.string.letra_grande_2), aj.textoGrande) { v -> ajustar { it.copy(textoGrande = v) } }
                Interruptor(tx(R.string.animaciones), aj.animaciones, tx(R.string.las_letras_del_tablon_giran_al_cambiar)) { v -> ajustar { it.copy(animaciones = v) } }
                Interruptor(tx(R.string.colores_para_daltonismo_2), aj.daltonismo, tx(R.string.azul_y_naranja_en_vez_de_verde_y_rojo)) { v -> ajustar { it.copy(daltonismo = v) } }
                Interruptor(tx(R.string.ocultar_posicionamientos), aj.ocultarPosicionamiento, tx(R.string.ocultar_posicionamientos_detalle)) { v -> ajustar { it.copy(ocultarPosicionamiento = v) } }
                Seccion(tx(R.string.tema))
                Elegir(Tema.entries, aj.tema, { it.texto }) { tm -> ajustar { it.copy(tema = tm) } }
                Texto(tx(R.string.auto_claro_u_oscuro_segun_el_movil), 15, C.gris, modifier = Modifier.padding(top = 6.dp))
                Seccion(tx(R.string.unidades))
                Elegir(Unidades.entries, aj.unidades, { it.texto }) { u -> ajustar { it.copy(unidades = u) } }
                Seccion(tx(R.string.idioma))
                val ctx = LocalContext.current
                // Los nombres de los idiomas van siempre en su propio idioma, para encontrarlos aunque no se entienda el actual.
                Elegir(Idioma.disponibles, Idioma.actual(ctx), {
                    when (it) { "es" -> "ESPAÑOL"; "en" -> "ENGLISH"; else -> tx(R.string.tema_auto) }
                }) { codigo -> Idioma.cambiar(ctx, codigo) }
                Texto(tx(R.string.idioma_auto), 15, C.gris, modifier = Modifier.padding(top = 6.dp))
            }
            SeccionAjustes.AVISOS -> AjustesAvisos(aj, ::ajustar)
            SeccionAjustes.CLAVE -> AjustesClave(aj, ::ajustar)
            SeccionAjustes.COPIA -> AjustesCopia()
            SeccionAjustes.DATOS -> AjustesDatos()
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun AjustesAvisos(aj: Ajustes, ajustar: ((Ajustes) -> Ajustes) -> Unit) {
    val ctx = LocalContext.current
    if (!Avisos.puede(ctx)) {
        Texto(tx(R.string.los_avisos_estan_desactivados_para_est), 17, C.rojo, FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        Boton(tx(R.string.activarlos_en_el_movil)) { Avisos.abrirAjustesDelMovil(ctx) }
    }
    Seccion(tx(R.string.que_avisos_recibir))
    Interruptor(tx(R.string.embarque), aj.avisoEmbarque, tx(R.string.facturacion_embarque_y_puerta_cerrada)) { v -> ajustar { it.copy(avisoEmbarque = v) } }
    Interruptor(tx(R.string.despegue), aj.avisoDespegue) { v -> ajustar { it.copy(avisoDespegue = v) } }
    Interruptor(tx(R.string.aterrizaje), aj.avisoAterrizaje, tx(R.string.tambien_cuando_esta_llegando)) { v -> ajustar { it.copy(avisoAterrizaje = v) } }
    Interruptor(tx(R.string.retrasos), aj.avisoRetraso) { v -> ajustar { it.copy(avisoRetraso = v) } }
    Interruptor(tx(R.string.cambio_de_puerta), aj.avisoPuerta) { v -> ajustar { it.copy(avisoPuerta = v) } }
    Interruptor(tx(R.string.cinta_de_equipaje), aj.avisoCinta) { v -> ajustar { it.copy(avisoCinta = v) } }
    Texto(tx(R.string.las_cancelaciones_y_los_desvios_se_avi), 15, C.gris, modifier = Modifier.padding(top = 4.dp))

    Seccion(tx(R.string.recordatorio_antes_de_salir))
    Elegir(listOf(0, 2, 3, 24), aj.recordatorio, { if (it == 0) tx(R.string.no) else if (it == 24) tx(R.string.t_1_dia) else tx(R.string.x_h, it) }) { h -> ajustar { it.copy(recordatorio = h) } }
    Texto(tx(R.string.un_aviso_para_cada_vuelo_que_sigue_con), 15, C.gris, modifier = Modifier.padding(top = 6.dp))

    Seccion(tx(R.string.horas_de_silencio))
    Interruptor(tx(R.string.silencio_por_la_noche), aj.silencio, tx(R.string.los_avisos_llegan_sin_sonido_las_cance)) { v -> ajustar { it.copy(silencio = v) } }
    if (aj.silencio) {
        Hora(tx(R.string.desde), aj.silencioDesde) { h -> ajustar { it.copy(silencioDesde = h) } }
        Hora(tx(R.string.hasta), aj.silencioHasta) { h -> ajustar { it.copy(silencioHasta = h) } }
    }

    Seccion(tx(R.string.comprobar))
    BotonSecundario(tx(R.string.probar_un_aviso), icono = Icons.Default.Notifications) {
        Avisos.avisar(ctx, Vuelo("PRUEBA", fecha = "prueba", salida = Extremo(Lugar("LPA")), llegada = Extremo(Lugar("MAD"))), listOf(Avisos.Cambio(tx(R.string.los_avisos_funcionan_correctamente))))
    }
    if (Avisos.puede(ctx)) BotonSecundario(tx(R.string.avisos_en_los_ajustes_del_movil), Modifier.padding(top = 10.dp)) { Avisos.abrirAjustesDelMovil(ctx) }
}

@Composable
private fun AjustesClave(aj: Ajustes, ajustar: ((Ajustes) -> Ajustes) -> Unit) {
    val ctx = LocalContext.current
    var clave by remember { mutableStateOf(aj.claveApi) }
    var comoConseguirla by remember { mutableStateOf(false) }
    Texto(
        tx(R.string.opcional_sin_clave_la_aplicacion_funci) +
            tx(R.string.horarios_retrasos_puertas_cintas_y_el),
        16, C.gris,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = clave, onValueChange = { clave = it.trim() }, singleLine = true,
        modifier = Modifier.padding(bottom = 8.dp),
        placeholder = { Texto(tx(R.string.pegue_aqui_la_clave), 18, C.gris) },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        textStyle = TextStyle(fontSize = t(16), fontFamily = Mono, color = C.ambar),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.ambar, unfocusedBorderColor = C.gris, cursorColor = C.ambar),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Al cambiar de clave, la cuenta de consultas anterior ya no vale.
        Boton(tx(R.string.guardar), Modifier.weight(1f)) { ajustar { it.copy(claveApi = clave, cuotaRestante = null, cuotaLimite = null) } }
        if (aj.claveApi.isNotEmpty()) BotonSecundario(tx(R.string.quitar), Modifier.weight(1f)) {
            clave = ""; ajustar { it.copy(claveApi = "", cuotaRestante = null, cuotaLimite = null) }
        }
    }
    if (aj.claveApi.isNotEmpty()) {
        Texto(tx(R.string.clave_guardada), 18, C.verde, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        aj.cuotaRestante?.let { queda ->
            val limite = aj.cuotaLimite
            val poco = limite != null && queda < limite / 10
            Texto(
                tx(R.string.consultas_que_quedan_este_mes_x, queda) + (limite?.let { tx(R.string.de_x, it) } ?: ""),
                18, if (poco) C.rojo else C.texto, FontWeight.Bold, modifier = Modifier.padding(top = 6.dp),
            )
            Texto(
                tx(R.string.segun_la_ultima_consulta) + haceCuanto(aj.cuotaMomento) + "." +
                    if (poco) tx(R.string.quedan_pocas_la_app_usara_mas_el_radar) else "",
                15, C.gris,
            )
        }
    }

    // Las instrucciones, plegadas: solo hacen falta una vez.
    Spacer(Modifier.height(14.dp))
    BotonSecundario(if (comoConseguirla) tx(R.string.ocultar_como_conseguirla) else tx(R.string.como_conseguir_una_clave_gratis)) { comoConseguirla = !comoConseguirla }
    if (comoConseguirla) {
        Texto(
            tx(R.string.una_sola_vez_lo_puede_hacer_un_familia) +
                tx(R.string.t_1_entre_en_rapidapi_com_y_cree_una_cue) +
                tx(R.string.t_2_busque_aerodatabox_y_suscribase_al_p) +
                tx(R.string.t_3_copie_la_x_rapidapi_key_y_peguela_ar),
            16, C.texto, modifier = Modifier.padding(vertical = 10.dp),
        )
        BotonSecundario(tx(R.string.abrir_rapidapi_com)) {
            intentar { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rapidapi.com/aedbx-aedbx/api/aerodatabox"))) }
        }
    }
}

@Composable
private fun AjustesCopia() {
    val ctx = LocalContext.current
    var importarUri by remember { mutableStateOf<Uri?>(null) }
    var mensaje by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) mensaje = intentar {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Almacen.exportar().toByteArray()) } ?: error("sin acceso")
        }.fold({ tx(R.string.copia_guardada) to true }, { tx(R.string.no_se_ha_podido_guardar_la_copia) to false })
    }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importarUri = uri }

    Texto(tx(R.string.guarde_sus_vuelos_aeropuertos_trayecto), 16, C.gris)
    Spacer(Modifier.height(12.dp))
    Boton(tx(R.string.guardar_copia)) { exportar.launch("pardela-${LocalDate.now()}.json") }
    Spacer(Modifier.height(10.dp))
    BotonSecundario(tx(R.string.recuperar_copia)) { abrir.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    mensaje?.let { (txt, ok) -> Texto(txt, 18, if (ok) C.verde else C.rojo, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }

    importarUri?.let { uri ->
        Confirmar(tx(R.string.sustituir_todo_lo_de_este_movil_por_la), tx(R.string.sustituir), {
            val ok = intentar { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                ?.let { Almacen.importar(it) } ?: false
            mensaje = if (ok) tx(R.string.copia_recuperada) to true else tx(R.string.ese_fichero_no_es_una_copia_de_pardela) to false
            importarUri = null
        }) { importarUri = null }
    }
}

@Composable
private fun AjustesDatos() {
    var borrar by remember { mutableStateOf(false) }
    Texto(tx(R.string.todo_se_guarda_solo_en_este_movil_sin), 17, C.texto)
    Seccion(tx(R.string.de_donde_salen_los_datos))
    Texto(
        tx(R.string.radar_adsb_fi_y_adsb_lol_n_rutas_y_avi) +
            tx(R.string.mapas_openstreetmap_n_tiempo_open_mete),
        16, C.gris,
    )
    Seccion(tx(R.string.borrar))
    BotonSecundario(tx(R.string.borrar_todo), icono = Icons.Default.Delete) { borrar = true }
    if (borrar) Confirmar(tx(R.string.borrar_vuelos_aeropuertos_trayectos_y), tx(R.string.borrar), { Almacen.cambiar { Datos() }; borrar = false }) { borrar = false }
}
/** Botones grandes para elegir una opción entre varias. */
@Composable
private fun <T> Elegir(opciones: List<T>, actual: T, nombre: (T) -> String, elige: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { o ->
            val sel = o == actual
            Box(
                Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) C.boton else C.panel).border(2.dp, C.ambar, RoundedCornerShape(12.dp))
                    .clickable { elige(o) }.padding(6.dp),
                contentAlignment = Alignment.Center,
            ) { Texto((if (sel) "✔ " else "") + nombre(o), 15, if (sel) C.sobreBoton else C.ambar, FontWeight.Bold, lineas = 2) }
        }
    }
}

/** Elegir una hora con botones − y + (sin relojes pequeños). */
@Composable
private fun Hora(titulo: String, hora: Int, cambia: (Int) -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Texto(titulo, 20, C.texto, FontWeight.Bold, modifier = Modifier.weight(1f))
            BotonRedondo("−", tx(R.string.una_hora_antes)) { cambia((hora + 23) % 24) }
            Spacer(Modifier.width(10.dp))
            Paletas("%02d:00".format(hora), 22)
            Spacer(Modifier.width(10.dp))
            BotonRedondo("+", tx(R.string.una_hora_despues)) { cambia((hora + 1) % 24) }
        }
    }
}

@Composable
private fun BotonRedondo(signo: String, descripcion: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(C.boton)
            .clickable(onClickLabel = descripcion, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Texto(signo, 26, C.sobreBoton, FontWeight.Black) }
}

@Composable
private fun Interruptor(titulo: String, activo: Boolean, detalle: String? = null, cambia: (Boolean) -> Unit) {
    Tarjeta({ cambia(!activo) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Texto(titulo, 20, C.texto, FontWeight.Bold)
                detalle?.let { Texto(it, 15, C.gris) }
            }
            Switch(
                checked = activo, onCheckedChange = cambia,
                colors = SwitchDefaults.colors(checkedTrackColor = C.boton, checkedThumbColor = C.sobreBoton),
            )
        }
    }
}

/** "hace 5 min", "hace 3 h", "hace 2 días". */
fun haceCuanto(momento: Long): String {
    val min = (System.currentTimeMillis() - momento) / 60_000
    return when {
        min < 1 -> tx(R.string.ahora_mismo)
        min < 60 -> tx(R.string.hace_x_min, min)
        min < 24 * 60 -> tx(R.string.hace_x_h, min / 60)
        else -> tx(R.string.hace_x_dias, min / (24 * 60))
    }
}
