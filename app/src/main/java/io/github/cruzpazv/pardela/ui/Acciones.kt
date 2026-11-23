package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

/** Lector en voz alta con la voz del propio móvil (sin internet ni permisos). */
class Voz(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var lista = false
    private var pendiente: String? = null
    var falla = false; private set

    override fun onInit(estado: Int) {
        if (estado != TextToSpeech.SUCCESS) { falla = true; return }
        val idioma = Locale.getDefault()
        val r = tts.setLanguage(idioma)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.forLanguageTag(idioma.language))
        tts.setSpeechRate(0.9f) // un poco más despacio que lo normal
        lista = true
        pendiente?.let { decir(it) }
        pendiente = null
    }

    fun decir(texto: String) {
        if (!lista) { pendiente = texto; return }
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "pardela")
    }

    fun callar() { if (lista) tts.stop() }
    fun cerrar() = tts.shutdown()
}

@Composable
fun rememberVoz(): Voz {
    val ctx = LocalContext.current
    val voz = remember { Voz(ctx) }
    DisposableEffect(Unit) { onDispose { voz.cerrar() } }
    return voz
}
