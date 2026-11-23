package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.cruzpazv.pardela.Idioma
import io.github.cruzpazv.pardela.data.intentar
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Resumen
import io.github.cruzpazv.pardela.data.Vuelo
import java.util.Locale

/** Abre el menú de compartir de Android con el resumen del vuelo. */
fun compartirVuelo(ctx: Context, v: Vuelo) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, tx(R.string.vuelo_x, v.numero))
        .putExtra(Intent.EXTRA_TEXT, Resumen.compartir(v))
    ctx.startActivity(Intent.createChooser(i, tx(R.string.compartir_vuelo_x, v.numero)))
}

/** ¿Se sabe la hora de salida? Sin ella no se puede crear el evento. */
fun puedeIrAlCalendario(v: Vuelo) = (v.salida.utcPrevista ?: v.salida.utc) != null

/** Abre la app de calendario con el vuelo ya rellenado (el usuario confirma al guardar). No necesita permisos. */
fun anadirAlCalendario(ctx: Context, v: Vuelo) {
    val sale = v.salida.utcPrevista ?: v.salida.utc ?: return
    val llega = v.llegada.utcPrevista ?: v.llegada.utc ?: (sale + 2 * 3_600_000L)
    val aeropuerto = Aeropuertos.iata(v.salida.lugar.iata)?.nombre ?: v.salida.lugar.nombre
    val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "✈ ${v.numero} ${v.salida.lugar.iata} → ${v.llegada.lugar.iata}")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, sale)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, llega)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, aeropuerto)
        .putExtra(CalendarContract.Events.DESCRIPTION, Resumen.compartir(v))
    runCatching { ctx.startActivity(i) }.onFailure {
        Toast.makeText(ctx, tx(R.string.no_se_ha_encontrado_una_aplicacion_de), Toast.LENGTH_LONG).show()
    }
}

/**
 * Lector en voz alta con la voz del propio móvil (sin internet ni permisos).
 * Elige expresamente una voz del idioma de la app (español de España / inglés británico si las hay):
 * si el móvil está en otro idioma, Android usaría su voz por defecto y leería con acento extranjero.
 */
class Voz(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var lista = false
    private var pendiente: String? = null

    /** Está hablando: el botón pasa a PARAR. */
    var hablando by mutableStateOf(false); private set
    /** El móvil no tiene la voz del idioma de la app: idioma que falta (para avisar y ofrecer instalarla). */
    var falta by mutableStateOf<Locale?>(null); private set

    override fun onInit(estado: Int) {
        if (estado != TextToSpeech.SUCCESS) { falta = Idioma.locale(); return }
        val base = Idioma.locale()
        val deseado = when (base.language) {
            "es" -> Locale.forLanguageTag("es-ES")
            "en" -> Locale.forLanguageTag("en-GB")
            else -> base
        }
        val instaladas = intentar { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == deseado.language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        val elegida = instaladas.sortedWith(
            compareByDescending<Voice> { it.locale.country == deseado.country }
                .thenBy { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
        ).firstOrNull()
        if (elegida != null) tts.voice = elegida
        else {
            val r = tts.setLanguage(deseado)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) falta = deseado
        }
        tts.setSpeechRate(0.9f) // un poco más despacio que lo normal
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { hablando = true }
            override fun onDone(id: String?) { hablando = false }
            @Deprecated("Android < 5") override fun onError(id: String?) { hablando = false }
            override fun onError(id: String?, codigo: Int) { hablando = false }
            override fun onStop(id: String?, interrumpido: Boolean) { hablando = false }
        })
        lista = true
        pendiente?.let { decir(it) }
        pendiente = null
    }

    fun decir(texto: String) {
        if (falta != null) return // mejor avisar que leer con la voz de otro idioma
        hablando = true
        if (!lista) { pendiente = texto; return }
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "pardela")
    }

    /** El mismo botón empieza y para. */
    fun alternar(texto: String) = if (hablando) callar() else decir(texto)

    fun callar() {
        pendiente = null
        if (lista) tts.stop()
        hablando = false
    }

    fun cerrar() = tts.shutdown()
}

/** Abre la instalación de voces del móvil (o, si no se puede, los ajustes de voz). */
fun instalarVoz(ctx: Context) {
    intentar { ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { intentar { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
fun rememberVoz(): Voz {
    val ctx = LocalContext.current
    val voz = remember { Voz(ctx) }
    DisposableEffect(Unit) { onDispose { voz.cerrar() } }
    return voz
}
