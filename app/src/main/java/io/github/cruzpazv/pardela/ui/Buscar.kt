package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

import io.github.cruzpazv.pardela.data.Aerolineas
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tx(R.string.volver), tint = C.ambar, modifier = Modifier.size(36.dp)) }
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
fun Cargando(texto: String = tx(R.string.buscando)) {
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

/** Estado del buscador: vive mientras la pantalla esté en la pila (al volver de un resultado sigue la lista). */
class EstadoBusqueda {
    val porTrayecto = mutableStateOf(false)
    val texto = mutableStateOf("")
    val origen = mutableStateOf(Almacen.datos.value.aeropuertos.firstOrNull()?.let { Aeropuertos.iata(it) })
    val destino = mutableStateOf<Aeropuerto?>(null)
    val fecha = mutableStateOf(LocalDate.now())
    val error = mutableStateOf<String?>(null)
    val resultados = mutableStateOf<List<Vuelo>?>(null)
    val scroll = LazyListState()
}

@Composable
fun BuscarVuelo(e: EstadoBusqueda, atras: () -> Unit, abrir: (Vuelo) -> Unit) {
    var porTrayecto by e.porTrayecto
    var texto by e.texto
    var origen by e.origen
    var destino by e.destino
    var eligiendo by remember { mutableStateOf<Boolean?>(null) } // true = origen, false = destino
    val hoy = LocalDate.now()
    var fecha by e.fecha
    var calendario by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by e.error
    var resultados by e.resultados
    val scope = rememberCoroutineScope()
    val teclado = LocalSoftwareKeyboardController.current

    fun limpiar() { resultados = null; error = null }

    fun buscar() {
        if (cargando) return
        val o = origen; val d = destino
        if (porTrayecto) {
            if (o == null || d == null) { error = tx(R.string.elija_el_aeropuerto_de_salida_y_el_de); return }
        } else if (texto.isBlank()) return
        teclado?.hide()
        cargando = true; error = null; resultados = null
        scope.launch {
            if (porTrayecto) intentar { Repositorio.buscarTrayecto(o!!.iata, d!!.iata, fecha) }
                .onSuccess { resultados = it }
                .onFailure { error = it.message ?: tx(R.string.sin_conexion) }
            else intentar { Repositorio.buscarVuelo(texto, fecha) }
                .onSuccess { r -> if (r.size == 1) abrir(r[0]) else resultados = r }
                .onFailure { error = it.message ?: tx(R.string.sin_conexion) }
            cargando = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera(tx(R.string.buscar_un_vuelo), atras)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to tx(R.string.por_numero), true to tx(R.string.por_trayecto)).forEach { (t, nombre) ->
                val sel = t == porTrayecto
                Box(
                    Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) C.boton else C.panel).border(2.dp, C.ambar, RoundedCornerShape(12.dp))
                        .clickable { porTrayecto = t; limpiar() },
                    contentAlignment = Alignment.Center,
                ) { Texto(nombre, 17, if (sel) C.sobreBoton else C.ambar, FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!porTrayecto) {
            Texto(tx(R.string.escriba_el_numero_que_aparece_en_el_bi), 18)
            Spacer(Modifier.height(8.dp))
            Campo(texto, { texto = it.uppercase().take(10) }, tx(R.string.ej_ib3166), ::buscar)
        } else {
            Texto(tx(R.string.todos_los_vuelos_directos_de_un_dia_en), 18)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CasillaAeropuerto(tx(R.string.desde), origen) { eligiendo = true }
                    CasillaAeropuerto(tx(R.string.hasta), destino) { eligiendo = false }
                }
                IconButton(onClick = { val o = origen; origen = destino; destino = o; limpiar() }) {
                    Texto("⇅", 34, C.ambar, FontWeight.Black, modifier = Modifier.semantics { contentDescription = tx(R.string.intercambiar_salida_y_llegada) })
                }
            }
        }
        if (Almacen.tieneClave) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1L to tx(R.string.ayer), 0L to tx(R.string.hoy), 1L to tx(R.string.manana)).forEach { (d, nombre) ->
                    val sel = fecha == hoy.plusDays(d)
                    Box(
                        Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) C.boton else C.panel).border(2.dp, C.ambar, RoundedCornerShape(12.dp))
                            .clickable { fecha = hoy.plusDays(d); limpiar() },
                        contentAlignment = Alignment.Center,
                    ) { Texto(nombre, 18, if (sel) C.sobreBoton else C.ambar, FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Otra fecha: hasta un año vista (límite de AeroDataBox), para guardar el vuelo de un viaje.
            val otra = fecha < hoy.minusDays(1) || fecha > hoy.plusDays(1)
            if (otra) Boton(fecha.format(FECHA_LARGA).uppercase(), icono = Icons.Default.DateRange) { calendario = true }
            else BotonSecundario(tx(R.string.otra_fecha), icono = Icons.Default.DateRange) { calendario = true }
            if (calendario) ElegirFecha(fecha, { fecha = it; limpiar() }) { calendario = false }
        }
        Spacer(Modifier.height(12.dp))
        Boton(tx(R.string.buscar), icono = Icons.Default.Search, onClick = ::buscar)
        if (!Almacen.tieneClave) {
            Spacer(Modifier.height(8.dp))
            Texto(
                if (porTrayecto) tx(R.string.buscar_por_trayecto_necesita_la_clave)
                else tx(R.string.sin_clave_api_solo_se_encuentran_vuelo),
                15, C.gris,
            )
        }
        val err = error
        val lista = resultados?.let { if (porTrayecto) Aerolineas.visibles(it) else it }
        when {
            cargando -> Cargando()
            err != null -> AvisoError(err)
            lista?.isEmpty() == true -> AvisoError(
                if (porTrayecto) tx(R.string.no_hay_vuelos_directos_de_x_a_x_ese_di, origen?.iata, destino?.iata)
                else tx(R.string.no_se_ha_encontrado_ese_vuelo_comprueb)
            )
        }
        if (!lista.isNullOrEmpty()) {
            Seccion(if (porTrayecto) tx(R.string.x_vuelos_elija_el_suyo, lista.size) else tx(R.string.elija_su_vuelo))
            LazyColumn(state = e.scroll) { items(lista) { v -> FilaVuelo(v, salidas = true) { abrir(v) } } }
        }
    }

    eligiendo?.let { esOrigen ->
        Dialog(onDismissRequest = { eligiendo = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(C.fondo).safeDrawingPadding()) {
                ListaAeropuertos(if (esOrigen) tx(R.string.desde_donde_sale) else tx(R.string.a_donde_va), { eligiendo = null }) { a ->
                    if (esOrigen) origen = a else destino = a
                    eligiendo = null; limpiar()
                }
            }
        }
    }
}

/** Casilla con el aeropuerto elegido; al pulsarla se abre la lista para cambiarlo. */
@Composable
private fun CasillaAeropuerto(etiqueta: String, a: Aeropuerto?, onClick: () -> Unit) {
    Tarjeta(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Texto(etiqueta, 15, C.gris, FontWeight.Bold, mono = true, modifier = Modifier.width((64 * Escala.current).dp))
            if (a == null) Texto(tx(R.string.elegir_aeropuerto), 18, C.ambar, FontWeight.Bold)
            else {
                Paletas(a.iata, 20)
                Spacer(Modifier.width(10.dp))
                Texto(a.ciudad.uppercase(), 17, C.texto, FontWeight.Bold, lineas = 1)
            }
        }
    }
}

/**
 * Elegir aeropuerto.
 * [origen] null → ver tablón. "" → elegir origen de un trayecto. "MAD" → elegir destino desde MAD.
 */
@Composable
fun BuscarAeropuerto(origen: String?, atras: () -> Unit, elegido: (Aeropuerto) -> Unit) {
    val titulo = when {
        origen == null -> tx(R.string.ver_un_aeropuerto)
        origen.isEmpty() -> tx(R.string.trayecto_desde_donde_sale)
        else -> tx(R.string.trayecto_desde_x_a_donde_va, origen)
    }
    ListaAeropuertos(titulo, atras, elegido)
}

/** Buscador de aeropuertos por ciudad o código. */
@Composable
private fun ListaAeropuertos(titulo: String, atras: () -> Unit, elegido: (Aeropuerto) -> Unit) {
    var texto by remember { mutableStateOf("") }
    val lista = remember(texto) { Aeropuertos.buscar(texto) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera(titulo, atras)
        Texto(tx(R.string.escriba_la_ciudad_o_el_codigo_mad_bcn), 18)
        Spacer(Modifier.height(8.dp))
        Campo(texto, { texto = it.take(30) }, tx(R.string.ej_malaga), { lista.firstOrNull()?.let(elegido) })
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

private val FECHA_LARGA get() = DateTimeFormatter.ofPattern(tx(R.string.formato_fecha_larga), io.github.cruzpazv.pardela.Idioma.locale())

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
                Texto(tx(R.string.aceptar), 18, C.ambar, FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = cerrar) { Texto(tx(R.string.cancelar), 18, C.gris, FontWeight.Bold) } },
    ) {
        DatePicker(estado, colors = colores, showModeToggle = false,
            title = { Texto(tx(R.string.que_dia_sale_el_vuelo), 20, C.texto, FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
