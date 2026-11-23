package io.github.cruzpazv.pardela.data

import io.github.cruzpazv.pardela.R
import io.github.cruzpazv.pardela.tx

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
            if (!r.isNullOrEmpty()) return r.map { conAerolinea(it) }
        }
        // El radar solo ve los vuelos de hoy: para otra fecha daría el vuelo de hoy con ese número.
        val hoy = LocalDate.now()
        if (fecha < hoy.minusDays(1) || fecha > hoy.plusDays(1)) {
            fallo?.let { throw it }
            return emptyList()
        }
        // Sin clave, o la clave ha fallado (agotada, no válida…): radar gratuito.
        val radar = listOfNotNull(porRadar(texto, fecha.toString())?.let { conAerolinea(it) })
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
        val c = if (!radarUtil(n)) null
        else n.avion.hex?.let { Radar.porHex(it) }
            // El mismo avión puede estar haciendo antes otro vuelo: solo vale si el indicativo coincide.
            ?.takeIf { n.indicativo == null || it.indicativo.isNullOrBlank() || it.indicativo.equals(n.indicativo, ignoreCase = true) }
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
        // Solo cuenta como actualizado si había conexión (si no, son los datos de antes).
        val momento = if (Red.hayConexion()) System.currentTimeMillis() else v.actualizado
        return conAerolinea(n.copy(actualizado = momento)).also { apuntar(it) }
    }

    /**
     * ¿Tiene sentido buscar este vuelo en el radar? Solo cerca de su hora (3 h antes de salir a 6 h después de llegar).
     * Así un vuelo guardado para dentro de un mes no se confunde con el de hoy que lleva el mismo número.
     */
    fun radarUtil(v: Vuelo, ahora: Long = System.currentTimeMillis()): Boolean {
        val hora = 3_600_000L
        val sale = v.salida.utcPrevista ?: v.salida.utc
        if (sale != null) {
            val llega = v.llegada.utcPrevista ?: v.llegada.utc ?: (sale + 6 * hora)
            return ahora > sale - 3 * hora && ahora < llega + 6 * hora
        }
        val dia = v.fecha?.let { intentar { LocalDate.parse(it) }.getOrNull() } ?: return true
        val hoy = LocalDate.now()
        return dia >= hoy.minusDays(1) && dia <= hoy
    }

    /** Orden del viaje: antes de salir (0) < en vuelo (1) < llegando (2) < aterrizado (3). Cancelado/desviado = -1 (se aceptan siempre). */
    internal fun rango(e: Estado) = when (e) {
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
    internal fun estadoRadar(v: Vuelo, antes: Vuelo?): Estado {
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

    suspend fun tablon(iata: String, salidas: Boolean, conClave: Boolean = true): List<Vuelo> {
        val l = if (conClave && Almacen.tieneClave) AeroDataBox.tablon(iata, salidas).sortedBy { (if (salidas) it.salida else it.llegada).utc ?: Long.MAX_VALUE }
        else tablonRadar(iata, salidas)
        return l.map { conAerolinea(it) }
    }

    private val rastros = HashMap<String, MutableList<EnDirecto>>()

    /** Posiciones vistas de un vuelo mientras la app estaba abierta (para dibujar su recorrido en el mapa). */
    fun rastro(v: Vuelo): List<EnDirecto> = synchronized(rastros) { rastros[v.clave]?.toList().orEmpty() }

    private fun apuntar(v: Vuelo) {
        val d = v.directo?.takeIf { it.reciente && !it.enTierra } ?: return
        synchronized(rastros) {
            val l = rastros.getOrPut(v.clave) { mutableListOf() }
            if (l.lastOrNull()?.momento != d.momento) {
                l.add(d)
                if (l.size > 600) l.removeAt(0)
            }
        }
    }

    /**
     * Recorrido para el mapa: el real (adsb.lol, últimas 24 h) si se conoce el avión y el vuelo es de hoy;
     * si no, las posiciones vistas mientras la app estaba abierta.
     */
    suspend fun recorrido(v: Vuelo): List<EnDirecto> {
        val hex = v.avion.hex
        if (hex == null || !radarUtil(v)) return rastro(v)
        val desde = v.salida.utcPrevista ?: v.salida.utc
        val hasta = if (v.estado.terminado) (v.llegada.utcPrevista ?: v.llegada.utc)?.plus(30 * 60_000L) else null
        return Radar.recorrido(hex, desde, hasta).ifEmpty { rastro(v) }
    }

    /** Todos los vuelos directos de un día entre dos aeropuertos. Necesita clave. */
    suspend fun buscarTrayecto(origen: String, destino: String, fecha: LocalDate): List<Vuelo> {
        if (!Almacen.tieneClave) error(tx(R.string.para_buscar_por_trayecto_hace_falta_la))
        return AeroDataBox.porTrayecto(origen, destino, fecha.toString()).map { conAerolinea(it) }
    }

    /** Puntualidad habitual de este número de vuelo. Necesita clave (y que el plan incluya la consulta). */
    suspend fun puntualidad(v: Vuelo): AeroDataBox.Puntualidad? {
        if (!Almacen.tieneClave) return null
        return try {
            AeroDataBox.puntualidad(v.numero, Aeropuertos.iata(v.salida.lugar.iata)?.icao, Aeropuertos.iata(v.llegada.lugar.iata)?.icao)
        } catch (e: ErrorRed) {
            if (e.codigo == 401 || e.codigo == 403) error(tx(R.string.puntualidad_no_disponible))
            throw e
        }
    }

    /** Vuelos del avión en los últimos 7 días, del más reciente al más antiguo. Necesita clave. */
    suspend fun historialAvion(a: Avion): List<Vuelo> {
        if (!Almacen.tieneClave) error(tx(R.string.para_ver_los_vuelos_anteriores_del_avi))
        val hoy = LocalDate.now()
        val l = try {
            AeroDataBox.porAvion(a, hoy.minusDays(6).toString(), hoy.toString())
        } catch (e: ErrorRed) {
            // 401/403 aquí suele ser el plan, no la clave: la consulta de historial es de un nivel superior.
            if (e.codigo == 401 || e.codigo == 403) error(tx(R.string.no_se_ha_podido_consultar_el_historial))
            throw e
        }
        val ahora = System.currentTimeMillis()
        return l.filter { (it.salida.utc ?: 0L) <= ahora }
            .sortedByDescending { it.salida.utc ?: 0L }
            .map { conAerolinea(it) }
    }

    /**
     * Rellena el código IATA (para el logo) y el nombre de la aerolínea si la fuente no los trae.
     * Orden: datos del vuelo → prefijo del número o del indicativo → catálogo interno → adsbdb.
     */
    private suspend fun conAerolinea(v: Vuelo): Vuelo {
        if (v.aerolineaIata != null && v.aerolineaNombre != null) return v
        val pre = Aerolineas.partir(v.indicativo ?: "")?.first?.takeIf { it.length == 3 }
            ?: Aerolineas.partir(v.numero)?.first
        val cat = pre?.let { if (it.length == 3) Aerolineas.icao(it) else Aerolineas.iata(it) }
        val filial = if (cat == null) Aerolineas.filial(pre) else null
        var iata = v.aerolineaIata ?: cat?.iata ?: filial?.second ?: pre?.takeIf { it.length == 2 }
        var nombre = v.aerolineaNombre ?: cat?.nombre ?: filial?.first
        if (iata == null || nombre == null) {
            val icao = cat?.icao ?: pre?.takeIf { it.length == 3 }
            val opciones = (icao ?: iata)?.let { Adsbdb.aerolineas(it) }.orEmpty()
            // Si el código está repetido, vale la del indicativo o la de un país de la ruta; si no, mejor nada que una equivocada.
            val paises = listOf(v.salida, v.llegada).mapNotNull { Aeropuertos.iata(it.lugar.iata)?.pais }
            val elegida = opciones.singleOrNull()
                ?: opciones.firstOrNull { icao != null && it.icao == icao }
                ?: opciones.filter { it.pais in paises }.singleOrNull()
            elegida?.let {
                iata = iata ?: it.iata
                nombre = nombre ?: it.nombre
            }
        }
        return v.copy(aerolineaIata = iata, aerolineaNombre = nombre)
    }

    /** Sin clave: aviones a ~50 millas, se queda con los que salen/llegan aquí. */
    data class Cercano(val vuelo: Vuelo, val km: Double)

    /** Aviones en el aire a menos de ~55 km (30 millas náuticas) de un punto, con su ruta si se conoce. */
    suspend fun cerca(lat: Double, lon: Double): List<Cercano> = coroutineScope {
        val hoy = LocalDate.now().toString()
        val aviones = Radar.cerca(lat, lon, 30).filter { !it.directo.enTierra }
            .map { it to km(it.directo.lat, it.directo.lon, lat, lon) }.sortedBy { it.second }.take(30)
        val limite = Semaphore(4)
        aviones.map { (c, distancia) ->
            async {
                val cs = c.indicativo?.takeIf { it.isNotBlank() }
                val r = cs?.let { limite.withPermit { Adsbdb.ruta(it) } }
                val v = Vuelo(
                    numero = r?.numero ?: cs ?: c.matricula ?: c.hex.uppercase(), indicativo = cs,
                    aerolineaIata = r?.aerolineaIata, aerolineaNombre = r?.aerolineaNombre,
                    salida = Extremo(lugar = completar(r?.origen)), llegada = Extremo(lugar = completar(r?.destino)),
                    avion = Avion(hex = c.hex, matricula = c.matricula, tipoIcao = c.tipo),
                    directo = c.directo, fecha = hoy,
                )
                Cercano(conAerolinea(v.copy(estado = estadoRadar(v, null))), distancia)
            }
        }.awaitAll()
    }

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
