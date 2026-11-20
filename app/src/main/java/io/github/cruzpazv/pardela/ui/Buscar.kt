package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.data.intentar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.cruzpazv.pardela.data.Aeropuerto
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Cabecera(titulo: String, atras: () -> Unit, derecha: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(C.panel).clickable(onClick = atras),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = C.ambar, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { Texto(titulo, 22, C.ambar, FontWeight.Bold, mono = true, lineas = 2) }
        derecha()
    }
}

@Composable
fun Campo(valor: String, cambia: (String) -> Unit, pista: String, alEnviar: () -> Unit) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { intentar { foco.requestFocus() } }
    OutlinedTextField(
        value = valor, onValueChange = cambia, singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(foco),
        placeholder = { Texto(pista, 20, C.gris, mono = true) },
        textStyle = TextStyle(fontSize = t(28), fontFamily = Mono, fontWeight = FontWeight.Bold, color = C.ambar),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = { alEnviar() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.ambar, unfocusedBorderColor = C.gris, cursorColor = C.ambar,
            focusedContainerColor = C.panel, unfocusedContainerColor = C.panel,
        ),
    )
}

@Composable
fun Cargando(texto: String = "BUSCANDO…") {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = C.ambar, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(16.dp))
        Texto(texto, 20, C.ambar, mono = true)
    }
}

@Composable
fun AvisoError(texto: String) = Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(12.dp)).background(C.rojo.copy(alpha = .15f)).padding(14.dp)) {
    Texto("⚠ $texto", 18, C.rojo)
}

@Composable
fun BuscarVuelo(atras: () -> Unit, abrir: (Vuelo) -> Unit) {
    var texto by remember { mutableStateOf("") }
    val hoy = LocalDate.now()
    var fecha by remember { mutableStateOf(hoy) }
    var calendario by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultados by remember { mutableStateOf<List<Vuelo>?>(null) }
    val scope = rememberCoroutineScope()
    val teclado = LocalSoftwareKeyboardController.current

    fun buscar() {
        if (texto.isBlank() || cargando) return
        teclado?.hide()
        cargando = true; error = null; resultados = null
        scope.launch {
            intentar { Repositorio.buscarVuelo(texto, fecha) }
                .onSuccess { r -> if (r.size == 1) abrir(r[0]) else resultados = r }
                .onFailure { error = it.message ?: "Sin conexión" }
            cargando = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera("BUSCAR UN VUELO", atras)
        Texto("Escriba el número que aparece en el billete:", 18)
        Spacer(Modifier.height(8.dp))
        Campo(texto, { texto = it.uppercase().take(10) }, "Ej: IB3166", ::buscar)
        if (Almacen.tieneClave) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1L to "AYER", 0L to "HOY", 1L to "MAÑANA").forEach { (d, nombre) ->
                    val sel = fecha == hoy.plusDays(d)
                    Box(
                        Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) C.boton else C.panel).border(2.dp, C.ambar, RoundedCornerShape(12.dp))
                            .clickable { fecha = hoy.plusDays(d) },
                        contentAlignment = Alignment.Center,
                    ) { Texto(nombre, 18, if (sel) C.sobreBoton else C.ambar, FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Otra fecha: hasta un año vista (límite de AeroDataBox), para guardar el vuelo de un viaje.
            val otra = fecha < hoy.minusDays(1) || fecha > hoy.plusDays(1)
            if (otra) Boton(fecha.format(FECHA_LARGA).uppercase(), icono = Icons.Default.DateRange) { calendario = true }
            else BotonSecundario("OTRA FECHA", icono = Icons.Default.DateRange) { calendario = true }
            if (calendario) ElegirFecha(fecha, { fecha = it; resultados = null; error = null }) { calendario = false }
        }
        Spacer(Modifier.height(12.dp))
        Boton("BUSCAR", icono = Icons.Default.Search, onClick = ::buscar)
        if (!Almacen.tieneClave) {
            Spacer(Modifier.height(8.dp))
            Texto("Sin clave API solo se encuentran vuelos que están en el aire ahora o su ruta habitual. Ver AJUSTES.", 15, C.gris)
        }
        when {
            cargando -> Cargando()
            error != null -> AvisoError(error!!)
            resultados?.isEmpty() == true -> AvisoError("No se ha encontrado ese vuelo. Compruebe el número.")
        }
        resultados?.takeIf { it.isNotEmpty() }?.let { lista ->
            Seccion("ELIJA SU VUELO")
            LazyColumn { items(lista) { v -> FilaVuelo(v, salidas = true) { abrir(v) } } }
        }
    }
}

/**
 * Elegir aeropuerto.
 * [origen] null → ver tablón. "" → elegir origen de un trayecto. "MAD" → elegir destino desde MAD.
 */
@Composable
fun BuscarAeropuerto(origen: String?, atras: () -> Unit, elegido: (Aeropuerto) -> Unit) {
    var texto by remember { mutableStateOf("") }
    val lista = remember(texto) { Aeropuertos.buscar(texto) }
    val titulo = when {
        origen == null -> "VER UN AEROPUERTO"
        origen.isEmpty() -> "TRAYECTO: ¿DESDE DÓNDE SALE?"
        else -> "TRAYECTO DESDE $origen: ¿A DÓNDE VA?"
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera(titulo, atras)
        Texto("Escriba la ciudad o el código (MAD, BCN…):", 18)
        Spacer(Modifier.height(8.dp))
        Campo(texto, { texto = it.take(30) }, "Ej: MÁLAGA", { lista.firstOrNull()?.let(elegido) })
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(lista, key = { it.iata }) { a ->
                Tarjeta({ elegido(a) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Paletas(a.iata, 22)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Texto(a.ciudad.uppercase(), 19, C.texto, FontWeight.Bold, lineas = 1)
                            Texto("${a.nombre} · ${a.pais}", 15, C.gris, lineas = 1)
                        }
                    }
                }
            }
        }
    }
}

private val FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", Locale.forLanguageTag("es-ES"))

private fun aMillis(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun deMillis(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

/** Calendario para elegir el día del vuelo: de ayer a un año vista. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElegirFecha(inicial: LocalDate, elegir: (LocalDate) -> Unit, cerrar: () -> Unit) {
    val desde = LocalDate.now().minusDays(1)
    val hasta = LocalDate.now().plusDays(365)
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = aMillis(inicial),
        yearRange = desde.year..hasta.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = deMillis(utcTimeMillis).let { it >= desde && it <= hasta }
            override fun isSelectableYear(year: Int) = year in desde.year..hasta.year
        },
    )
    val colores = DatePickerDefaults.colors(
        containerColor = C.panel,
        titleContentColor = C.texto, headlineContentColor = C.texto, weekdayContentColor = C.gris,
        subheadContentColor = C.texto, navigationContentColor = C.texto,
        yearContentColor = C.texto, currentYearContentColor = C.ambar,
        selectedYearContainerColor = C.boton, selectedYearContentColor = C.sobreBoton,
        dayContentColor = C.texto, disabledDayContentColor = C.gris.copy(alpha = .4f),
        selectedDayContainerColor = C.boton, selectedDayContentColor = C.sobreBoton,
        todayContentColor = C.ambar, todayDateBorderColor = C.ambar,
    )
    DatePickerDialog(
        onDismissRequest = cerrar,
        colors = colores,
        confirmButton = {
            TextButton(onClick = { estado.selectedDateMillis?.let { elegir(deMillis(it)) }; cerrar() }) {
                Texto("ACEPTAR", 18, C.ambar, FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = cerrar) { Texto("CANCELAR", 18, C.gris, FontWeight.Bold) } },
    ) {
        DatePicker(estado, colors = colores, showModeToggle = false,
            title = { Texto("¿Qué día sale el vuelo?", 20, C.texto, FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
