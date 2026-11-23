package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.tx

import io.github.cruzpazv.pardela.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.data.Almacen
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Logo: fondo de atardecer + estela recortados, y la pardela encima sin recortar para que pueda salir volando.
 * [vuelo] va de 0 (posada en el logo) a 1 (fuera de la pantalla). [estela] = opacidad de las ondas del agua.
 */
@Composable
fun LogoPardela(tam: Dp, estela: () -> Float = { 1f }, vuelo: () -> Float = { 0f }) {
    val conf = LocalConfiguration.current
    val d = LocalDensity.current
    val ancho = with(d) { conf.screenWidthDp.dp.toPx() }
    val alto = with(d) { conf.screenHeightDp.dp.toPx() }
    // Las capas son de 108 con la zona visible de 72 en el centro, como el icono del lanzador.
    val capa = tam * 1.5f
    Box(Modifier.size(tam), contentAlignment = Alignment.Center) {
        Box(Modifier.size(tam).clip(RoundedCornerShape(tam * 0.24f)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_bg), null, Modifier.requiredSize(capa))
            Image(painterResource(R.drawable.ic_logo_estela), null, Modifier.requiredSize(capa).graphicsLayer { alpha = estela() })
        }
        Image(
            painterResource(R.drawable.ic_logo_ave), "Pardela",
            Modifier.requiredSize(capa).graphicsLayer {
                val p = vuelo()
                // Despega hacia arriba a la derecha, gana tamaño y bate las alas (cada vez menos).
                translationX = p * ancho * 0.85f
                translationY = -p * alto * 0.6f
                val crece = 1f + p * 1.3f
                val aleteo = 1f - 0.45f * abs(sin(p * PI.toFloat() * 6f)) * (1f - p * 0.6f)
                scaleX = crece
                scaleY = crece * aleteo
                rotationZ = p * 10f
            },
        )
    }
}

/** Primera vez: pide la clave API (opcional). Se guarda solo en el móvil. */
@Composable
fun Bienvenida() {
    var clave by remember { mutableStateOf("") }
    var saliendo by remember { mutableStateOf(false) }
    val vuelo = remember { Animatable(0f) }
    val estela = remember { Animatable(1f) }
    val animar = Animar.current
    val scope = rememberCoroutineScope()

    fun empezar(guardarClave: Boolean) {
        if (saliendo) return
        saliendo = true
        scope.launch {
            if (animar) {
                // Las ondas se apagan suavemente mientras la pardela despega.
                launch { estela.animateTo(0f, tween(600, easing = LinearOutSlowInEasing)) }
                vuelo.animateTo(1f, tween(1500, easing = CubicBezierEasing(0.45f, 0f, 0.85f, 0.6f)))
            }
            Almacen.ajustar { it.copy(claveApi = if (guardarClave) clave else it.claveApi, bienvenidaVista = true) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LogoPardela(132.dp, estela = { estela.value }) { vuelo.value } }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Paletas("PARDELA", 26) }
        Spacer(Modifier.height(10.dp))
        Texto(tx(R.string.sus_vuelos_como_en_el_tablon_del_aerop), 20, C.texto)
        BuildConfig.DEDICATORIA.takeIf { it.isNotBlank() }?.let { mensaje ->
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.ambar.copy(alpha = .15f))
                    .border(2.dp, C.ambar, RoundedCornerShape(14.dp)).padding(18.dp),
                contentAlignment = Alignment.Center,
            ) { Texto("♥  $mensaje", 21, C.texto, FontWeight.Bold) }
        }
        Seccion(tx(R.string.clave_de_aerodatabox))
        Texto(
            tx(R.string.opcional_con_ella_vera_horarios_retras) +
                tx(R.string.sin_ella_la_aplicacion_funciona_igual) +
                tx(R.string.la_clave_se_guarda_solo_en_este_movil) +
                tx(R.string.puede_ponerla_o_cambiarla_despues_en_a),
            17, C.gris,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = clave, onValueChange = { clave = it.trim() }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Texto(tx(R.string.pegue_aqui_su_x_rapidapi_key), 17, C.gris) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            textStyle = TextStyle(fontSize = t(18), fontFamily = Mono, color = C.ambar),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.ambar, unfocusedBorderColor = C.gris, cursorColor = C.ambar),
        )
        Spacer(Modifier.height(16.dp))
        Boton(if (clave.isBlank()) tx(R.string.empezar_sin_clave) else tx(R.string.guardar_y_empezar)) { empezar(guardarClave = true) }
        Spacer(Modifier.height(10.dp))
        if (clave.isNotBlank()) BotonSecundario(tx(R.string.ahora_no)) { empezar(guardarClave = false) }
        Texto(tx(R.string.como_conseguir_la_clave_gratis_vea_aju), 15, C.gris, FontWeight.Normal, modifier = Modifier.padding(top = 14.dp))
    }
}
