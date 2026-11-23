package io.github.cruzpazv.pardela

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import io.github.cruzpazv.pardela.data.Almacen
import java.util.Locale

/**
 * Idioma de la app: "" = el del móvil, "es" o "en".
 * Android 13+: lo guarda el sistema (es el mismo ajuste que Ajustes del móvil → Apps → Pardela → Idioma).
 * Antes: se guarda en los Ajustes de Pardela.
 *
 * Todos los textos, fechas, números y la voz pasan por aquí: el contexto de la aplicación no siempre
 * se entera a tiempo del idioma elegido, así que se crea uno con ese idioma de forma explícita.
 */
object Idioma {
    val disponibles = listOf("", "es", "en")

    @Volatile private var codigo: String? = null
    @Volatile private var cache: Pair<String, Context>? = null

    private fun leer(ctx: Context): String =
        if (Build.VERSION.SDK_INT >= 33) ctx.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language.orEmpty()
        else Almacen.ajustes.idioma

    /** Vuelve a leer el idioma (al abrir la pantalla: puede haberse cambiado en los ajustes del móvil). */
    fun refrescar(ctx: Context) {
        val nuevo = leer(ctx)
        if (nuevo != codigo) { codigo = nuevo; cache = null }
    }

    fun actual(ctx: Context): String = codigo ?: leer(ctx).also { codigo = it }

    /** Idioma para fechas, números y voz. */
    fun locale(): Locale = codigo?.takeIf { it.isNotEmpty() }?.let { Locale.forLanguageTag(it) }
        ?: Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()

    /** Contexto con el que se leen los textos traducidos. */
    fun contexto(app: Context): Context {
        val c = actual(app)
        if (c.isEmpty()) return app
        cache?.let { (k, ctx) -> if (k == c) return ctx }
        val conf = Configuration(app.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(c)) }
        return app.createConfigurationContext(conf).also { cache = c to it }
    }

    fun cambiar(ctx: Context, nuevo: String) {
        Almacen.ajustar { it.copy(idioma = nuevo) }
        codigo = nuevo; cache = null
        if (Build.VERSION.SDK_INT >= 33) {
            // El sistema guarda la elección y vuelve a abrir la pantalla en el idioma nuevo.
            ctx.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (nuevo.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(nuevo)
        } else {
            actividad(ctx)?.recreate()
        }
    }

    /** Android 12 o anterior: la pantalla (calendario, diálogos del sistema) en el idioma elegido. */
    fun envolver(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val c = Almacen.ajustes.idioma
        if (c.isEmpty()) return base
        val conf = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(c)) }
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
