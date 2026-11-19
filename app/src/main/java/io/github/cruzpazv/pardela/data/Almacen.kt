package io.github.cruzpazv.pardela.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

/** Todo se guarda en un único JSON privado del móvil. Sin cuentas, sin nube. */
object Almacen {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private lateinit var fichero: File
    private val _datos = MutableStateFlow(Datos())
    val datos: StateFlow<Datos> = _datos

    fun iniciar(ctx: Context) {
        if (::fichero.isInitialized) return
        fichero = File(ctx.filesDir, "pardela.json")
        _datos.value = intentar { json.decodeFromString(Datos.serializer(), fichero.readText()) }.getOrDefault(Datos())
    }

    @Synchronized
    fun cambiar(f: (Datos) -> Datos) {
        val nuevo = f(_datos.value)
        _datos.value = nuevo
        val tmp = File(fichero.parentFile, "pardela.json.tmp")
        tmp.writeText(json.encodeToString(Datos.serializer(), nuevo))
        tmp.renameTo(fichero)
    }

    private val jsonBonito = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Copia de seguridad sin la clave API (es personal y no debe viajar en un fichero). */
    fun exportar(): String = jsonBonito.encodeToString(
        Datos.serializer(), _datos.value.copy(ajustes = _datos.value.ajustes.copy(claveApi = "")),
    )

    /** Sustituye todo por la copia, conservando la clave API de este móvil. */
    fun importar(texto: String): Boolean = intentar {
        val d = json.decodeFromString(Datos.serializer(), texto)
        cambiar { actual -> d.copy(ajustes = d.ajustes.copy(claveApi = actual.ajustes.claveApi, bienvenidaVista = true)) }
    }.isSuccess

    val ajustes get() = _datos.value.ajustes
    val tieneClave get() = _datos.value.ajustes.claveApi.isNotBlank()

    fun sigue(v: Vuelo) = _datos.value.vuelos.any { it.numero == v.numero && it.fecha == v.fecha }

    fun seguir(v: Vuelo) = cambiar { d ->
        if (d.vuelos.any { it.numero == v.numero && it.fecha == v.fecha }) d
        else d.copy(vuelos = d.vuelos + VueloSeguido(v.numero, v.fecha, v.salida.lugar.iata, v))
    }

    fun dejar(numero: String, fecha: String?) = cambiar { d ->
        d.copy(vuelos = d.vuelos.filterNot { it.numero == numero && it.fecha == fecha })
    }

    fun actualizar(v: Vuelo) = cambiar { d ->
        d.copy(vuelos = d.vuelos.map {
            if (it.numero == v.numero && it.fecha == v.fecha) it.copy(ultimo = v, origen = v.salida.lugar.iata.ifBlank { it.origen }) else it
        })
    }

    fun alternarAeropuerto(iata: String) = cambiar { d ->
        d.copy(aeropuertos = if (iata in d.aeropuertos) d.aeropuertos - iata else d.aeropuertos + iata)
    }

    fun anadirTrayecto(o: String, de: String) = cambiar { d ->
        if (d.trayectos.any { it.origen == o && it.destino == de }) d
        else d.copy(trayectos = d.trayectos + Trayecto(o, de))
    }

    fun quitarTrayecto(t: Trayecto) = cambiar { d -> d.copy(trayectos = d.trayectos.filterNot { it.clave == t.clave }) }

    fun ajustar(f: (Ajustes) -> Ajustes) = cambiar { it.copy(ajustes = f(it.ajustes)) }
}
