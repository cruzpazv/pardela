package io.github.cruzpazv.pardela.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

@Composable
fun Tablon(iata: String, atras: () -> Unit, abrir: (Vuelo) -> Unit) {
    val datos by Almacen.datos.collectAsState()
    val ap = Aeropuertos.iata(iata)
    var salidas by remember { mutableStateOf(true) }
    var lista by remember { mutableStateOf<List<Vuelo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var vuelta by remember { mutableIntStateOf(0) }
    val guardado = iata in datos.aeropuertos

    LaunchedEffect(salidas, vuelta) {
        lista = null; error = null
        runCatching { Repositorio.tablon(iata, salidas) }
            .onSuccess { lista = it }.onFailure { error = it.message ?: "Sin conexión" }
        // Sin clave el radar cambia rápido: se refresca solo cada 2 min.
        if (!Almacen.tieneClave) { delay(120_000); vuelta++ }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera("$iata · ${ap?.ciudad?.uppercase() ?: ""}", atras) {
            IconButton(onClick = { Almacen.alternarAeropuerto(iata) }) {
                Icon(Icons.Default.Star, if (guardado) "Quitar de mis aeropuertos" else "Guardar aeropuerto",
                    tint = if (guardado) C.ambar else C.gris, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { vuelta++ }) { Icon(Icons.Default.Refresh, "Actualizar", tint = C.ambar, modifier = Modifier.size(34.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "SALIDAS", false to "LLEGADAS").forEach { (s, nombre) ->
                val sel = s == salidas
                Box(
                    Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) C.boton else C.panel).border(2.dp, C.ambar, RoundedCornerShape(12.dp))
                        .clickable { salidas = s },
                    contentAlignment = Alignment.Center,
                ) { Texto(nombre, 20, if (sel) C.sobreBoton else C.ambar, FontWeight.Black, mono = true) }
            }
        }
        if (!guardado) Texto("Pulse ★ para guardar este aeropuerto en el inicio.", 15, C.gris, modifier = Modifier.padding(top = 8.dp))
        if (!Almacen.tieneClave) Texto("Sin clave API: solo aviones en el aire cerca del aeropuerto ahora mismo.", 15, C.gris, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(6.dp))
        when {
            error != null -> AvisoError(error!!)
            lista == null -> Cargando()
            lista!!.isEmpty() -> Texto("No hay vuelos que mostrar ahora.", 18, C.gris, modifier = Modifier.padding(16.dp))
            else -> LazyColumn { items(lista!!) { v -> FilaVuelo(v, salidas) { abrir(v) } } }
        }
    }
}

@Composable
fun AjustesPantalla(atras: () -> Unit) {
    val ctx = LocalContext.current
    val datos by Almacen.datos.collectAsState()
    val aj = datos.ajustes
    var clave by remember { mutableStateOf(aj.claveApi) }
    var borrar by remember { mutableStateOf(false) }
    var importarUri by remember { mutableStateOf<Uri?>(null) }
    var mensaje by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    fun ajustar(f: (Ajustes) -> Ajustes) = Almacen.ajustar(f)

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) mensaje = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Almacen.exportar().toByteArray()) } ?: error("sin acceso")
        }.fold({ "✔ Copia guardada" to true }, { "⚠ No se ha podido guardar la copia" to false })
    }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importarUri = uri }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Cabecera("AJUSTES", atras)

        // ---------- Aspecto ----------
        Seccion("ASPECTO")
        Interruptor("LETRA GRANDE", aj.textoGrande) { v -> ajustar { it.copy(textoGrande = v) } }
        Interruptor("ANIMACIONES", aj.animaciones, "Las letras del tablón giran al cambiar.") { v -> ajustar { it.copy(animaciones = v) } }
        Interruptor("COLORES PARA DALTONISMO", aj.daltonismo, "Azul y naranja en vez de verde y rojo.") { v -> ajustar { it.copy(daltonismo = v) } }
        Spacer(Modifier.height(6.dp))
        Elegir(Tema.entries, aj.tema, { it.texto }) { tm -> ajustar { it.copy(tema = tm) } }
        Texto("AUTO: claro u oscuro según el móvil.", 15, C.gris, modifier = Modifier.padding(top = 6.dp))

        Seccion("UNIDADES")
        Elegir(Unidades.entries, aj.unidades, { it.texto }) { u -> ajustar { it.copy(unidades = u) } }

        // ---------- Avisos ----------
        Seccion("QUÉ AVISOS RECIBIR")
        Interruptor("EMBARQUE", aj.avisoEmbarque, "Facturación, embarque y puerta cerrada.") { v -> ajustar { it.copy(avisoEmbarque = v) } }
        Interruptor("DESPEGUE", aj.avisoDespegue) { v -> ajustar { it.copy(avisoDespegue = v) } }
        Interruptor("ATERRIZAJE", aj.avisoAterrizaje, "También cuando está llegando.") { v -> ajustar { it.copy(avisoAterrizaje = v) } }
        Interruptor("RETRASOS", aj.avisoRetraso) { v -> ajustar { it.copy(avisoRetraso = v) } }
        Interruptor("CAMBIO DE PUERTA", aj.avisoPuerta) { v -> ajustar { it.copy(avisoPuerta = v) } }
        Interruptor("CINTA DE EQUIPAJE", aj.avisoCinta) { v -> ajustar { it.copy(avisoCinta = v) } }
        Texto("Las cancelaciones y los desvíos se avisan siempre.", 15, C.gris, modifier = Modifier.padding(top = 4.dp))

        Seccion("HORAS DE SILENCIO")
        Interruptor("SILENCIO POR LA NOCHE", aj.silencio, "Los avisos llegan sin sonido. Las cancelaciones sí suenan.") { v -> ajustar { it.copy(silencio = v) } }
        if (aj.silencio) {
            Hora("DESDE", aj.silencioDesde) { h -> ajustar { it.copy(silencioDesde = h) } }
            Hora("HASTA", aj.silencioHasta) { h -> ajustar { it.copy(silencioHasta = h) } }
        }

        Spacer(Modifier.height(10.dp))
        Boton("PROBAR UN AVISO") {
            Avisos.avisar(ctx, Vuelo("PRUEBA", fecha = "prueba", salida = Extremo(Lugar("LPA")), llegada = Extremo(Lugar("MAD"))), listOf(Avisos.Cambio("✔ Los avisos funcionan correctamente")))
        }
        if (!Avisos.puede(ctx)) Texto("⚠ Los avisos están desactivados para esta aplicación.", 16, C.rojo, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        BotonSecundario("AVISOS EN LOS AJUSTES DEL MÓVIL", Modifier.padding(top = 10.dp)) { Avisos.abrirAjustesDelMovil(ctx) }

        // ---------- Clave API ----------
        Seccion("CLAVE API (OPCIONAL)")
        Texto(
            "Sin clave la aplicación funciona con radar gratuito: ve los aviones en el aire, su ruta y sus datos.\n\n" +
                "Con una clave gratuita de AeroDataBox verá además horarios, retrasos, puertas, cintas de equipaje y el tablón completo del aeropuerto.\n\n" +
                "Cómo conseguirla (una sola vez, la puede hacer un familiar):\n" +
                "1. Entre en rapidapi.com y cree una cuenta gratuita.\n" +
                "2. Busque «AeroDataBox» y suscríbase al plan BASIC (gratis).\n" +
                "3. Copie la «X-RapidAPI-Key» y péguela aquí.\n\n" +
                "La clave solo se guarda en este móvil.",
            16, C.gris,
        )
        Spacer(Modifier.height(10.dp))
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
            Boton("GUARDAR", Modifier.weight(1f)) { ajustar { it.copy(claveApi = clave) } }
            if (aj.claveApi.isNotEmpty()) BotonSecundario("QUITAR", Modifier.weight(1f)) {
                clave = ""; ajustar { it.copy(claveApi = "") }
            }
        }
        if (aj.claveApi.isNotEmpty()) Texto("✔ Clave guardada", 18, C.verde, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        BotonSecundario("ABRIR RAPIDAPI.COM", Modifier.padding(top = 10.dp)) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rapidapi.com/aedbx-aedbx/api/aerodatabox"))) }
        }

        // ---------- Copia de seguridad ----------
        Seccion("COPIA DE SEGURIDAD")
        Texto("Guarde sus vuelos, aeropuertos, trayectos y ajustes en un fichero para pasarlos a otro móvil. La clave API no se incluye.", 16, C.gris)
        Spacer(Modifier.height(10.dp))
        Boton("GUARDAR COPIA") { exportar.launch("pardela-${LocalDate.now()}.json") }
        Spacer(Modifier.height(10.dp))
        BotonSecundario("RECUPERAR COPIA") { abrir.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        mensaje?.let { (txt, ok) -> Texto(txt, 18, if (ok) C.verde else C.rojo, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }

        // ---------- Datos ----------
        Seccion("DATOS")
        Texto("Todo se guarda solo en este móvil. Sin cuentas. Datos: adsb.fi, adsb.lol, adsbdb.com, planespotters.net, OurAirports y AeroDataBox (si pone clave).", 15, C.gris)
        Spacer(Modifier.height(10.dp))
        BotonSecundario("BORRAR TODO", icono = Icons.Default.Delete) { borrar = true }
        Spacer(Modifier.height(30.dp))
    }
    if (borrar) Confirmar("¿Borrar vuelos, aeropuertos, trayectos y clave?", "BORRAR", { Almacen.cambiar { Datos() }; clave = ""; borrar = false }) { borrar = false }
    importarUri?.let { uri ->
        Confirmar("¿Sustituir todo lo de este móvil por la copia? La clave API se conserva.", "SUSTITUIR", {
            val ok = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                ?.let { Almacen.importar(it) } ?: false
            mensaje = if (ok) "✔ Copia recuperada" to true else "⚠ Ese fichero no es una copia de Pardela" to false
            importarUri = null
        }) { importarUri = null }
    }
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
