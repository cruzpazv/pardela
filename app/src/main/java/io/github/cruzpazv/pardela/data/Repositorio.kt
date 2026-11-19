package io.github.cruzpazv.pardela.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Une las fuentes: tablón con clave (horarios) + radar gratis (posición, avión, ruta). */
object Repositorio {

    suspend fun buscarVuelo(texto: String, fecha: LocalDate): List<Vuelo> {
        val numero = Aerolineas.numeroComercial(texto)
        var fallo: Throwable? = null
        if (Almacen.tieneClave) {
            val r = intentar { AeroDataBox.porNumero(numero, fecha.toString()) }.onFailure { fallo = it }.getOrNull()
            if (!r.isNullOrEmpty()) return r
        }
        // Sin clave, o la clave ha fallado (agotada, no válida…): radar gratuito.
        val radar = listOfNotNull(porRadar(texto, fecha.toString()))
        if (radar.isEmpty()) fallo?.let { throw it }
        return radar
    }

    /** Sin clave: busca el avión en el aire y su ruta habitual. */
    private suspend fun porRadar(texto: String, fecha: String?): Vuelo? {
        val limpio = texto.uppercase().replace(" ", "")
        val cs = Aerolineas.indicativo(limpio) ?: limpio
        val ruta = Adsbdb.ruta(cs) ?: Adsbdb.ruta(limpio)
        val c = Radar.porIndicativo(cs)
        if (ruta == null && c == null) return null
        val v = Vuelo(
            numero = ruta?.numero ?: Aerolineas.numeroComercial(limpio),
            indicativo = cs,
            aerolineaIata = ruta?.aerolineaIata ?: Aerolineas.partir(limpio)?.first?.let { p -> if (p.length == 2) p else Aerolineas.icao(p)?.iata },
            aerolineaNombre = ruta?.aerolineaNombre,
            salida = Extremo(lugar = completar(ruta?.origen)),
            llegada = Extremo(lugar = completar(ruta?.destino)),
            avion = Avion(hex = c?.hex, matricula = c?.matricula, tipoIcao = c?.tipo),
            directo = c?.directo,
            fecha = fecha,
        )
        return v.copy(estado = estadoRadar(v, null))
    }

    private fun completar(l: Lugar?): Lugar = l?.let { Aeropuertos.iata(it.iata)?.lugar ?: it } ?: Lugar()

    /**
     * Actualiza un vuelo. [conClave] = se permite gastar una consulta de AeroDataBox.
     * La posición en directo (gratis) se pide siempre.
     */
    suspend fun refrescar(v: Vuelo, conClave: Boolean): Vuelo {
        var n = v
        if (conClave && Almacen.tieneClave && v.fecha != null) {
            intentar { AeroDataBox.porNumero(v.numero, v.fecha) }.getOrNull()
                ?.let { l -> l.firstOrNull { it.salida.lugar.iata == v.salida.lugar.iata } ?: l.firstOrNull() }
                ?.let { nuevo -> n = nuevo.copy(avion = fusion(nuevo.avion, v.avion), directo = v.directo) }
        }
        val c = n.avion.hex?.let { Radar.porHex(it) }
            ?: (n.indicativo ?: Aerolineas.indicativo(n.numero))?.let { Radar.porIndicativo(it) }
        n = n.copy(
            // Se guarda la última posición conocida aunque el avión salga de cobertura (la UI indica si es antigua).
            directo = c?.directo ?: n.directo,
            avion = if (c != null) n.avion.copy(hex = n.avion.hex ?: c.hex, matricula = n.avion.matricula ?: c.matricula, tipoIcao = n.avion.tipoIcao ?: c.tipo) else n.avion,
        )
        if (!n.conHorarios) n = n.copy(estado = estadoRadar(n, v))
        else if (c != null && !c.directo.enTierra && n.estado in listOf(Estado.PROGRAMADO, Estado.EMBARCANDO, Estado.PUERTA_CERRADA, Estado.RETRASADO, Estado.FACTURACION))
            n = n.copy(estado = Estado.EN_VUELO)
        // Un vuelo no "desdespega": evita avisos repetidos si una fuente va con retraso o el radar pierde la señal.
        if (rango(n.estado) < rango(v.estado) && rango(v.estado) > 0) n = n.copy(estado = v.estado)
        return n
    }

    /** Orden del viaje: antes de salir (0) < en vuelo (1) < llegando (2) < aterrizado (3). Cancelado/desviado = -1 (se aceptan siempre). */
    private fun rango(e: Estado) = when (e) {
        Estado.EN_VUELO -> 1
        Estado.APROXIMANDO -> 2
        Estado.ATERRIZADO -> 3
        Estado.CANCELADO, Estado.DESVIADO -> 4
        else -> 0
    }

    /** Ficha completa del avión + foto. Solo al abrir el detalle. */
    suspend fun completarAvion(v: Vuelo): Vuelo {
        val a = v.avion
        val ficha = (a.hex ?: a.matricula)?.let { Adsbdb.avion(it) }
        var av = fusion(a, ficha)
        av.hex?.let { Fotos.deHex(it) }?.let { (url, autor) -> av = av.copy(foto = url, fotoAutor = autor) }
        return v.copy(avion = av)
    }

    private fun fusion(a: Avion, b: Avion?): Avion = if (b == null) a else Avion(
        hex = a.hex ?: b.hex, matricula = a.matricula ?: b.matricula, tipoIcao = a.tipoIcao ?: b.tipoIcao,
        modelo = a.modelo ?: b.modelo, fabricante = a.fabricante ?: b.fabricante,
        propietario = a.propietario ?: b.propietario, foto = a.foto ?: b.foto, fotoAutor = a.fotoAutor ?: b.fotoAutor,
    )

    /** Estado deducido solo del radar. */
    private fun estadoRadar(v: Vuelo, antes: Vuelo?): Estado {
        val d = v.directo
        val dest = Aeropuertos.iata(v.llegada.lugar.iata)
        val prev = antes?.estado
        val volando = prev == Estado.EN_VUELO || prev == Estado.APROXIMANDO
        if (d == null) return if (prev == Estado.ATERRIZADO) prev else Estado.DESCONOCIDO
        if (!d.reciente) {
            // Sin señal. Si la última posición estaba cerca del destino, ha aterrizado.
            if (volando && dest != null && km(d.lat, d.lon, dest.lat, dest.lon) < 60) return Estado.ATERRIZADO
            // Si no (p. ej. sobre el océano), sigue como estaba durante unas horas.
            if (volando && System.currentTimeMillis() - d.momento > 12 * 3600_000L) return Estado.DESCONOCIDO
            return prev ?: Estado.DESCONOCIDO
        }
        val cerca = dest != null && km(d.lat, d.lon, dest.lat, dest.lon) < 15
        if (d.enTierra) return if (cerca || prev == Estado.EN_VUELO || prev == Estado.APROXIMANDO) Estado.ATERRIZADO else Estado.PROGRAMADO
        if (dest != null && km(d.lat, d.lon, dest.lat, dest.lon) < 80 && (d.altitudPies ?: 99999) < 12000) return Estado.APROXIMANDO
        return Estado.EN_VUELO
    }

    /** Minutos que faltan según radar (distancia / velocidad). */
    fun minutosRestantes(v: Vuelo): Int? {
        val d = v.directo?.takeIf { it.reciente } ?: return null
        val dest = Aeropuertos.iata(v.llegada.lugar.iata) ?: return null
        val kt = d.velocidadNudos?.takeIf { it > 80 } ?: return null
        return (km(d.lat, d.lon, dest.lat, dest.lon) / (kt * 1.852) * 60).toInt() + 5
    }

    fun progreso(v: Vuelo): Float? {
        val d = v.directo ?: return null
        val o = Aeropuertos.iata(v.salida.lugar.iata) ?: return null
        val de = Aeropuertos.iata(v.llegada.lugar.iata) ?: return null
        val total = km(o.lat, o.lon, de.lat, de.lon).takeIf { it > 1 } ?: return null
        return (km(o.lat, o.lon, d.lat, d.lon) / total).toFloat().coerceIn(0f, 1f)
    }

    suspend fun tablon(iata: String, salidas: Boolean): List<Vuelo> {
        if (Almacen.tieneClave) {
            return AeroDataBox.tablon(iata, salidas).sortedBy { (if (salidas) it.salida else it.llegada).utc ?: Long.MAX_VALUE }
        }
        return tablonRadar(iata, salidas)
    }

    /** Sin clave: aviones a ~50 millas, se queda con los que salen/llegan aquí. */
    private suspend fun tablonRadar(iata: String, salidas: Boolean): List<Vuelo> = coroutineScope {
        val ap = Aeropuertos.iata(iata) ?: return@coroutineScope emptyList()
        val cerca = Radar.cerca(ap.lat, ap.lon, 50).filter { !it.indicativo.isNullOrBlank() }
            .sortedBy { km(it.directo.lat, it.directo.lon, ap.lat, ap.lon) }.take(40)
        val limite = Semaphore(4)
        cerca.map { c ->
            async {
                val r = limite.withPermit { Adsbdb.ruta(c.indicativo!!) } ?: return@async null
                val coincide = if (salidas) r.origen.iata == iata else r.destino.iata == iata
                if (!coincide) return@async null
                val v = Vuelo(
                    numero = r.numero ?: c.indicativo!!, indicativo = c.indicativo,
                    aerolineaIata = r.aerolineaIata, aerolineaNombre = r.aerolineaNombre,
                    salida = Extremo(lugar = completar(r.origen)), llegada = Extremo(lugar = completar(r.destino)),
                    avion = Avion(hex = c.hex, matricula = c.matricula, tipoIcao = c.tipo),
                    directo = c.directo, fecha = LocalDate.now().toString(),
                )
                v.copy(estado = estadoRadar(v, null))
            }
        }.awaitAll().filterNotNull()
    }

    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
