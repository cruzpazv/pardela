package io.github.cruzpazv.pardela.ui

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
        Cabecera("AJUSTES", atras)
        Apartado("ASPECTO Y UNIDADES", Icons.Default.Edit, listOf(
            if (aj.textoGrande) "Letra grande" else "Letra normal",
            "tema ${aj.tema.texto.lowercase()}",
            if (aj.unidades == Unidades.METRICO) "metros y km/h" else "pies y nudos",
        ).joinToString(" · ") + if (aj.daltonismo) " · colores para daltonismo" else "") { abrir(SeccionAjustes.ASPECTO) }

        val tipos = listOf(aj.avisoEmbarque, aj.avisoDespegue, aj.avisoAterrizaje, aj.avisoRetraso, aj.avisoPuerta, aj.avisoCinta).count { it }
        val resumenAvisos = if (!Avisos.puede(ctx)) "⚠ Desactivados en el móvil" else listOfNotNull(
            "$tipos de 6 tipos",
            if (aj.recordatorio == 0) null else if (aj.recordatorio == 24) "recordatorio 1 día antes" else "recordatorio ${aj.recordatorio} h antes",
            if (aj.silencio) "silencio de %02d:00 a %02d:00".format(aj.silencioDesde, aj.silencioHasta) else null,
        ).joinToString(" · ")
        Apartado("AVISOS", Icons.Default.Notifications, resumenAvisos, alerta = !Avisos.puede(ctx)) { abrir(SeccionAjustes.AVISOS) }

        Apartado("CLAVE API", Icons.Default.Lock,
            if (aj.claveApi.isEmpty()) "Sin clave: funciona con radar gratuito"
            else "Guardada" + (aj.cuotaRestante?.let { " · quedan $it consultas" } ?: ""),
        ) { abrir(SeccionAjustes.CLAVE) }
        Apartado("COPIA DE SEGURIDAD", Icons.Default.Share, "Pasar sus datos a otro móvil") { abrir(SeccionAjustes.COPIA) }
        Apartado("DATOS Y PRIVACIDAD", Icons.Default.Info, "De dónde salen los datos · borrar todo") { abrir(SeccionAjustes.DATOS) }
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
        SeccionAjustes.ASPECTO -> "ASPECTO"
        SeccionAjustes.AVISOS -> "AVISOS"
        SeccionAjustes.CLAVE -> "CLAVE API"
        SeccionAjustes.COPIA -> "COPIA DE SEGURIDAD"
        SeccionAjustes.DATOS -> "DATOS"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Cabecera(titulo, atras)
        when (seccion) {
            SeccionAjustes.ASPECTO -> {
                Interruptor("LETRA GRANDE", aj.textoGrande) { v -> ajustar { it.copy(textoGrande = v) } }
                Interruptor("ANIMACIONES", aj.animaciones, "Las letras del tablón giran al cambiar.") { v -> ajustar { it.copy(animaciones = v) } }
                Interruptor("COLORES PARA DALTONISMO", aj.daltonismo, "Azul y naranja en vez de verde y rojo.") { v -> ajustar { it.copy(daltonismo = v) } }
                Seccion("TEMA")
                Elegir(Tema.entries, aj.tema, { it.texto }) { tm -> ajustar { it.copy(tema = tm) } }
                Texto("AUTO: claro u oscuro según el móvil.", 15, C.gris, modifier = Modifier.padding(top = 6.dp))
                Seccion("UNIDADES")
                Elegir(Unidades.entries, aj.unidades, { it.texto }) { u -> ajustar { it.copy(unidades = u) } }
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
        Texto("⚠ Los avisos están desactivados para esta aplicación.", 17, C.rojo, FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        Boton("ACTIVARLOS EN EL MÓVIL") { Avisos.abrirAjustesDelMovil(ctx) }
    }
    Seccion("QUÉ AVISOS RECIBIR")
    Interruptor("EMBARQUE", aj.avisoEmbarque, "Facturación, embarque y puerta cerrada.") { v -> ajustar { it.copy(avisoEmbarque = v) } }
    Interruptor("DESPEGUE", aj.avisoDespegue) { v -> ajustar { it.copy(avisoDespegue = v) } }
    Interruptor("ATERRIZAJE", aj.avisoAterrizaje, "También cuando está llegando.") { v -> ajustar { it.copy(avisoAterrizaje = v) } }
    Interruptor("RETRASOS", aj.avisoRetraso) { v -> ajustar { it.copy(avisoRetraso = v) } }
    Interruptor("CAMBIO DE PUERTA", aj.avisoPuerta) { v -> ajustar { it.copy(avisoPuerta = v) } }
    Interruptor("CINTA DE EQUIPAJE", aj.avisoCinta) { v -> ajustar { it.copy(avisoCinta = v) } }
    Texto("Las cancelaciones y los desvíos se avisan siempre.", 15, C.gris, modifier = Modifier.padding(top = 4.dp))

    Seccion("RECORDATORIO ANTES DE SALIR")
    Elegir(listOf(0, 2, 3, 24), aj.recordatorio, { if (it == 0) "NO" else if (it == 24) "1 DÍA" else "$it H" }) { h -> ajustar { it.copy(recordatorio = h) } }
    Texto("Un aviso para cada vuelo que sigue, con tiempo para ir al aeropuerto.", 15, C.gris, modifier = Modifier.padding(top = 6.dp))

    Seccion("HORAS DE SILENCIO")
    Interruptor("SILENCIO POR LA NOCHE", aj.silencio, "Los avisos llegan sin sonido. Las cancelaciones sí suenan.") { v -> ajustar { it.copy(silencio = v) } }
    if (aj.silencio) {
        Hora("DESDE", aj.silencioDesde) { h -> ajustar { it.copy(silencioDesde = h) } }
        Hora("HASTA", aj.silencioHasta) { h -> ajustar { it.copy(silencioHasta = h) } }
    }

    Seccion("COMPROBAR")
    BotonSecundario("PROBAR UN AVISO", icono = Icons.Default.Notifications) {
        Avisos.avisar(ctx, Vuelo("PRUEBA", fecha = "prueba", salida = Extremo(Lugar("LPA")), llegada = Extremo(Lugar("MAD"))), listOf(Avisos.Cambio("✔ Los avisos funcionan correctamente")))
    }
    if (Avisos.puede(ctx)) BotonSecundario("AVISOS EN LOS AJUSTES DEL MÓVIL", Modifier.padding(top = 10.dp)) { Avisos.abrirAjustesDelMovil(ctx) }
}

@Composable
private fun AjustesClave(aj: Ajustes, ajustar: ((Ajustes) -> Ajustes) -> Unit) {
    val ctx = LocalContext.current
    var clave by remember { mutableStateOf(aj.claveApi) }
    var comoConseguirla by remember { mutableStateOf(false) }
    Texto(
        "Opcional. Sin clave la aplicación funciona con radar gratuito. Con una clave gratuita de AeroDataBox verá además " +
            "horarios, retrasos, puertas, cintas y el tablón completo de cada aeropuerto. Solo se guarda en este móvil.",
        16, C.gris,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = clave, onValueChange = { clave = it.trim() }, singleLine = true,
        modifier = Modifier.padding(bottom = 8.dp),
        placeholder = { Texto("Pegue aquí la clave", 18, C.gris) },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        textStyle = TextStyle(fontSize = t(16), fontFamily = Mono, color = C.ambar),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.ambar, unfocusedBorderColor = C.gris, cursorColor = C.ambar),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Al cambiar de clave, la cuenta de consultas anterior ya no vale.
        Boton("GUARDAR", Modifier.weight(1f)) { ajustar { it.copy(claveApi = clave, cuotaRestante = null, cuotaLimite = null) } }
        if (aj.claveApi.isNotEmpty()) BotonSecundario("QUITAR", Modifier.weight(1f)) {
            clave = ""; ajustar { it.copy(claveApi = "", cuotaRestante = null, cuotaLimite = null) }
        }
    }
    if (aj.claveApi.isNotEmpty()) {
        Texto("✔ Clave guardada", 18, C.verde, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        aj.cuotaRestante?.let { queda ->
            val limite = aj.cuotaLimite
            val poco = limite != null && queda < limite / 10
            Texto(
                "Consultas que quedan este mes: $queda" + (limite?.let { " de $it" } ?: ""),
                18, if (poco) C.rojo else C.texto, FontWeight.Bold, modifier = Modifier.padding(top = 6.dp),
            )
            Texto(
                "Según la última consulta, " + haceCuanto(aj.cuotaMomento) + "." +
                    if (poco) " Quedan pocas: la app usará más el radar gratuito hasta el mes que viene." else "",
                15, C.gris,
            )
        }
    }

    // Las instrucciones, plegadas: solo hacen falta una vez.
    Spacer(Modifier.height(14.dp))
    BotonSecundario(if (comoConseguirla) "OCULTAR CÓMO CONSEGUIRLA" else "¿CÓMO CONSEGUIR UNA CLAVE GRATIS?") { comoConseguirla = !comoConseguirla }
    if (comoConseguirla) {
        Texto(
            "Una sola vez (lo puede hacer un familiar):\n" +
                "1. Entre en rapidapi.com y cree una cuenta gratuita.\n" +
                "2. Busque «AeroDataBox» y suscríbase al plan BASIC (gratis).\n" +
                "3. Copie la «X-RapidAPI-Key» y péguela arriba.",
            16, C.texto, modifier = Modifier.padding(vertical = 10.dp),
        )
        BotonSecundario("ABRIR RAPIDAPI.COM") {
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
        }.fold({ "✔ Copia guardada" to true }, { "⚠ No se ha podido guardar la copia" to false })
    }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importarUri = uri }

    Texto("Guarde sus vuelos, aeropuertos, trayectos y ajustes en un fichero para pasarlos a otro móvil. La clave API no se incluye.", 16, C.gris)
    Spacer(Modifier.height(12.dp))
    Boton("GUARDAR COPIA") { exportar.launch("pardela-${LocalDate.now()}.json") }
    Spacer(Modifier.height(10.dp))
    BotonSecundario("RECUPERAR COPIA") { abrir.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    mensaje?.let { (txt, ok) -> Texto(txt, 18, if (ok) C.verde else C.rojo, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }

    importarUri?.let { uri ->
        Confirmar("¿Sustituir todo lo de este móvil por la copia? La clave API se conserva.", "SUSTITUIR", {
            val ok = intentar { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                ?.let { Almacen.importar(it) } ?: false
            mensaje = if (ok) "✔ Copia recuperada" to true else "⚠ Ese fichero no es una copia de Pardela" to false
            importarUri = null
        }) { importarUri = null }
    }
}

@Composable
private fun AjustesDatos() {
    var borrar by remember { mutableStateOf(false) }
    Texto("Todo se guarda solo en este móvil. Sin cuentas, sin publicidad y sin rastreadores.", 17, C.texto)
    Seccion("DE DÓNDE SALEN LOS DATOS")
    Texto(
        "• Radar: adsb.fi y adsb.lol\n• Rutas y aviones: adsbdb.com\n• Fotos: planespotters.net\n• Aeropuertos: OurAirports\n" +
            "• Mapas: OpenStreetMap\n• Tiempo: Open-Meteo\n• Horarios: AeroDataBox (si pone clave)",
        16, C.gris,
    )
    Seccion("BORRAR")
    BotonSecundario("BORRAR TODO", icono = Icons.Default.Delete) { borrar = true }
    if (borrar) Confirmar("¿Borrar vuelos, aeropuertos, trayectos y clave?", "BORRAR", { Almacen.cambiar { Datos() }; borrar = false }) { borrar = false }
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
            BotonRedondo("−", "Una hora antes") { cambia((hora + 23) % 24) }
            Spacer(Modifier.width(10.dp))
            Paletas("%02d:00".format(hora), 22)
            Spacer(Modifier.width(10.dp))
            BotonRedondo("+", "Una hora después") { cambia((hora + 1) % 24) }
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
private fun haceCuanto(momento: Long): String {
    val min = (System.currentTimeMillis() - momento) / 60_000
    return when {
        min < 1 -> "ahora mismo"
        min < 60 -> "hace $min min"
        min < 24 * 60 -> "hace ${min / 60} h"
        else -> "hace ${min / (24 * 60)} días"
    }
}
