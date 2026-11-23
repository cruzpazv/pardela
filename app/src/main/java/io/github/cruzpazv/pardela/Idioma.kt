package io.github.cruzpazv.pardela

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import io.github.cruzpazv.pardela.data.Almacen
import java.util.Locale

/**
 * Idioma de la app: "" = el del móvil, "es" o "en".
 * Android 13+: lo gestiona el sistema (es el mismo ajuste que Ajustes del móvil → Apps → Pardela → Idioma).
 * Antes: se guarda en Ajustes y se aplica envolviendo el contexto.
 */
object Idioma {
    val disponibles = listOf("", "es", "en")

    fun actual(ctx: Context): String =
        if (Build.VERSION.SDK_INT >= 33) ctx.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language.orEmpty()
        else Almacen.ajustes.idioma

    fun cambiar(ctx: Context, codigo: String) {
        Almacen.ajustar { it.copy(idioma = codigo) }
        if (Build.VERSION.SDK_INT >= 33) {
            // El sistema guarda la elección y vuelve a abrir la pantalla en el idioma nuevo.
            ctx.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (codigo.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(codigo)
        } else {
            App.reaplicarIdioma()
            actividad(ctx)?.recreate()
        }
    }

    /** Android 12 o anterior: contexto con el idioma elegido (sin cambio, el del móvil). */
    fun envolver(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val codigo = Almacen.ajustes.idioma
        val locale = if (codigo.isEmpty()) Configuration(base.resources.configuration).locales.get(0) else Locale.forLanguageTag(codigo)
        Locale.setDefault(locale) // fechas, números y voz
        if (codigo.isEmpty()) return base
        val conf = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(conf)
    }

    private fun actividad(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
