package io.github.cruzpazv.pardela.data

import kotlinx.serialization.json.JsonElement
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Avión visto por radar ADS-B (gratis, sin clave). */
data class Contacto(
    val hex: String,
    val indicativo: String?,
    val matricula: String?,
    val tipo: String?,
    val directo: EnDirecto,
)

/** adsb.fi (principal) y adsb.lol (respaldo). Mismo formato readsb. */
object Radar {
    private fun contactos(j: JsonElement?): List<Contacto> =
        (j.lista("aircraft").ifEmpty { j.lista("ac") }).mapNotNull { a ->
            val lat = a.d("lat") ?: a.o("lastPosition").d("lat") ?: return@mapNotNull null
            val lon = a.d("lon") ?: a.o("lastPosition").d("lon") ?: return@mapNotNull null
            val alt = a.s("alt_baro")
            val suelo = alt == "ground"
            Contacto(
                hex = a.s("hex") ?: return@mapNotNull null,
                indicativo = a.s("flight"),
                matricula = a.s("r"),
                tipo = a.s("t"),
                directo = EnDirecto(
                    lat, lon,
                    altitudPies = if (suelo) 0 else alt?.toDoubleOrNull()?.toInt(),
                    velocidadNudos = a.d("gs")?.toInt(),
                    rumbo = a.d("track"),
                    enTierra = suelo,
                    momento = System.currentTimeMillis(),
                ),
            )
        }

    private suspend fun pedir(fi: String, lol: String): List<Contacto> =
        intentar { contactos(Red.get("https://opendata.adsb.fi/api/v2/$fi", despacio = true)) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
            ?: intentar { contactos(Red.get("https://api.adsb.lol/v2/$lol")) }.getOrDefault(emptyList())

    suspend fun porIndicativo(cs: String) = pedir("callsign/$cs", "callsign/$cs").firstOrNull()
    suspend fun porHex(hex: String) = pedir("hex/${hex.lowercase()}", "hex/${hex.lowercase()}").firstOrNull()
    suspend fun porMatricula(reg: String) = pedir("registration/$reg", "reg/$reg").firstOrNull()
    suspend fun cerca(lat: Double, lon: Double, millas: Int) =
        pedir("lat/$lat/lon/$lon/dist/$millas", "point/$lat/$lon/$millas")
}

/** adsbdb.com: ruta por indicativo y ficha del avión. Gratis, sin clave. */
object Adsbdb {
    data class Ruta(val origen: Lugar, val destino: Lugar, val aerolineaIata: String?, val aerolineaNombre: String?, val numero: String?)

    private val rutas = HashMap<String, Ruta?>()
    private val aviones = HashMap<String, Avion?>()

    private fun lugar(j: JsonElement?) = Lugar(j.s("iata_code") ?: "", j.s("name") ?: "", j.s("municipality") ?: "")

    suspend fun ruta(cs: String): Ruta? {
        synchronized(rutas) { if (rutas.containsKey(cs)) return rutas[cs] }
        // Un fallo de red no se guarda: así se reintenta cuando vuelva la conexión.
        val j = intentar { Red.get("https://api.adsbdb.com/v0/callsign/$cs") }.getOrElse { return null }
        val r = j.o("response").o("flightroute")?.let { f ->
            Ruta(lugar(f.o("origin")), lugar(f.o("destination")), f.o("airline").s("iata"), f.o("airline").s("name"), f.s("callsign_iata"))
        }
        synchronized(rutas) { rutas[cs] = r }
        return r
    }

    data class InfoAerolinea(val iata: String?, val icao: String?, val nombre: String?, val pais: String?)

    private val aerolineas = HashMap<String, List<InfoAerolinea>>()

    /**
     * Aerolíneas con ese código ICAO (RYR) o IATA (FR). Un código IATA puede estar repetido
     * en varios países (ZN = Naysa en España y Zenith en Tailandia): se devuelven todas.
     */
    suspend fun aerolineas(codigo: String): List<InfoAerolinea> {
        synchronized(aerolineas) { aerolineas[codigo]?.let { return it } }
        val j = intentar { Red.get("https://api.adsbdb.com/v0/airline/$codigo") }.getOrElse { return emptyList() }
        val l = j.lista("response").map { InfoAerolinea(it.s("iata"), it.s("icao"), it.s("name"), it.s("country_iso")) }
        synchronized(aerolineas) { aerolineas[codigo] = l }
        return l
    }

    suspend fun avion(hexOMatricula: String): Avion? {
        synchronized(aviones) { if (aviones.containsKey(hexOMatricula)) return aviones[hexOMatricula] }
        val resp = intentar { Red.get("https://api.adsbdb.com/v0/aircraft/$hexOMatricula") }.getOrElse { return null }
        val a = resp.o("response").o("aircraft")?.let { j ->
                Avion(
                    hex = j.s("mode_s"), matricula = j.s("registration"), tipoIcao = j.s("icao_type"),
                    modelo = j.s("type"), fabricante = j.s("manufacturer"), propietario = j.s("registered_owner"),
                    foto = j.s("url_photo"),
                )
            }
        synchronized(aviones) { aviones[hexOMatricula] = a }
        return a
    }
}

/** Fotos de planespotters.net (hay que mostrar el autor). */
object Fotos {
    suspend fun deHex(hex: String): Pair<String, String?>? = intentar {
        Red.get("https://api.planespotters.net/pub/photos/hex/${hex.lowercase()}").lista("photos").firstOrNull()?.let { p ->
            (p.o("thumbnail_large").s("src") ?: p.o("thumbnail").s("src"))?.let { it to p.s("photographer") }
        }
    }.getOrNull()
}

/** AeroDataBox (RapidAPI). Opcional: solo si el usuario pega su clave. */
object AeroDataBox {
    private const val HOST = "aerodatabox.p.rapidapi.com"

    private suspend fun get(ruta: String): JsonElement? {
        val clave = Almacen.ajustes.claveApi.trim()
        if (clave.isEmpty()) return null
        return Red.get("https://$HOST$ruta", mapOf("X-RapidAPI-Key" to clave, "X-RapidAPI-Host" to HOST))
    }

    suspend fun porNumero(numero: String, fecha: String): List<Vuelo> {
        val j = get("/flights/number/$numero/$fecha?withAircraftImage=false&withLocation=false") ?: return emptyList()
        val items = (j as? kotlinx.serialization.json.JsonArray)?.toList() ?: listOf(j)
        return items.mapNotNull { vuelo(it, fecha) }
    }

    private val cacheTablon = HashMap<String, Pair<Long, List<Vuelo>>>()

    /** Salidas o llegadas de las próximas horas (y las 2 últimas). Se guarda 10 min para no gastar la clave. */
    suspend fun tablon(iata: String, salidas: Boolean): List<Vuelo> {
        val k = "$iata|$salidas"
        synchronized(cacheTablon) {
            cacheTablon[k]?.let { (t, l) -> if (System.currentTimeMillis() - t < 10 * 60_000L) return l }
        }
        return tablonRed(iata, salidas).also { l -> synchronized(cacheTablon) { cacheTablon[k] = System.currentTimeMillis() to l } }
    }

    private fun filtros(salidas: Boolean) = "withLeg=true&direction=${if (salidas) "Departure" else "Arrival"}" +
        "&withCancelled=true&withCodeshared=false&withCargo=false&withPrivate=false"

    private suspend fun tablonRed(iata: String, salidas: Boolean): List<Vuelo> =
        fids(iata, salidas, "/flights/airports/iata/$iata?offsetMinutes=-120&durationMinutes=720&${filtros(salidas)}")

    /** Lee una respuesta de tablón (FIDS) de [iata]. */
    private suspend fun fids(iata: String, salidas: Boolean, ruta: String): List<Vuelo> {
        val j = get(ruta) ?: return emptyList()
        val aqui = Aeropuertos.iata(iata)?.lugar ?: Lugar(iata)
        return j.lista(if (salidas) "departures" else "arrivals").mapNotNull { f ->
            if (f.o("departure") == null && f.o("movement") != null) {
                // Formato sin "leg": movement = el otro aeropuerto, con horas de este.
                val m = extremo(f.o("movement"))
                val otro = m.copy(hora = null, horaPrevista = null, utc = null, utcPrevista = null, terminal = null, puerta = null)
                val este = m.copy(lugar = aqui)
                vuelo(f, null)?.copy(salida = if (salidas) este else otro, llegada = if (salidas) otro else este)
            } else vuelo(f, null)?.let { v ->
                // En el tablón de un aeropuerto, AeroDataBox no repite ese aeropuerto en cada vuelo (salía "???").
                when {
                    salidas && v.salida.lugar.iata.isBlank() -> v.copy(salida = v.salida.copy(lugar = aqui))
                    !salidas && v.llegada.lugar.iata.isBlank() -> v.copy(llegada = v.llegada.copy(lugar = aqui))
                    else -> v
                }
            }
        }
    }

    private val cacheTrayecto = HashMap<String, Pair<Long, List<Vuelo>>>()

    /**
     * Vuelos directos de un día entre dos aeropuertos: salidas del origen filtradas por destino.
     * El tablón admite como mucho 12 h por consulta: un día = 2 consultas. Se guarda 30 min.
     */
    suspend fun porTrayecto(origen: String, destino: String, fecha: String): List<Vuelo> {
        val k = "$origen|$destino|$fecha"
        synchronized(cacheTrayecto) {
            cacheTrayecto[k]?.let { (t, l) -> if (System.currentTimeMillis() - t < 30 * 60_000L) return l }
        }
        val franjas = listOf("${fecha}T00:00" to "${fecha}T11:59", "${fecha}T12:00" to "${fecha}T23:59")
        val l = franjas.flatMap { (desde, hasta) -> fids(origen, true, "/flights/airports/iata/$origen/$desde/$hasta?${filtros(true)}") }
            .filter { it.llegada.lugar.iata.equals(destino, ignoreCase = true) }
            .distinctBy { it.clave }
            .sortedBy { it.salida.utc ?: Long.MAX_VALUE }
        synchronized(cacheTrayecto) { cacheTrayecto[k] = System.currentTimeMillis() to l }
        return l
    }

    private val cacheAvion = HashMap<String, Pair<Long, List<Vuelo>>>()

    /** Vuelos de un avión (por matrícula o, si no, por código radar) entre dos fechas. Se guarda 30 min. */
    suspend fun porAvion(avion: Avion, desde: String, hasta: String): List<Vuelo> {
        val (tipo, id) = avion.matricula?.let { "reg" to it } ?: avion.hex?.let { "icao24" to it.lowercase() } ?: return emptyList()
        val k = "$tipo|$id|$desde|$hasta"
        synchronized(cacheAvion) {
            cacheAvion[k]?.let { (t, l) -> if (System.currentTimeMillis() - t < 30 * 60_000L) return l }
        }
        val j = get("/flights/$tipo/$id/$desde/$hasta?withAircraftImage=false&withLocation=false") ?: return emptyList()
        val items = (j as? kotlinx.serialization.json.JsonArray)?.toList() ?: listOf(j)
        val l = items.mapNotNull { vuelo(it, null) }
        synchronized(cacheAvion) { cacheAvion[k] = System.currentTimeMillis() to l }
        return l
    }

    private fun utc(t: JsonElement?): Long? = t.s("utc")?.let {
        intentar { LocalDateTime.parse(it.take(16).replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private fun hhmm(t: JsonElement?): String? = t.s("local")?.takeIf { it.length >= 16 }?.substring(11, 16)

    private fun extremo(e: JsonElement?): Extremo {
        val a = e.o("airport")
        val iata = a.s("iata") ?: Aeropuertos.icao(a.s("icao"))?.iata ?: ""
        val cat = Aeropuertos.iata(iata)
        val prevista = e.o("revisedTime") ?: e.o("predictedTime") ?: e.o("runwayTime")
        return Extremo(
            lugar = Lugar(iata, cat?.nombre ?: a.s("name") ?: "", cat?.ciudad ?: a.s("municipalityName") ?: a.s("name") ?: ""),
            hora = hhmm(e.o("scheduledTime")), horaPrevista = hhmm(prevista),
            utc = utc(e.o("scheduledTime")), utcPrevista = utc(prevista),
            terminal = e.s("terminal"), puerta = e.s("gate"), cinta = e.s("baggageBelt"),
        )
    }

    private fun estado(s: String?): Estado = when (s) {
        "Expected", "Scheduled" -> Estado.PROGRAMADO
        "CheckIn" -> Estado.FACTURACION
        "Boarding" -> Estado.EMBARCANDO
        "GateClosed" -> Estado.PUERTA_CERRADA
        "Delayed" -> Estado.RETRASADO
        "Departed", "EnRoute" -> Estado.EN_VUELO
        "Approaching" -> Estado.APROXIMANDO
        "Arrived" -> Estado.ATERRIZADO
        "Canceled", "CanceledUncertain" -> Estado.CANCELADO
        "Diverted" -> Estado.DESVIADO
        else -> Estado.DESCONOCIDO
    }

    private fun vuelo(f: JsonElement, fecha: String?): Vuelo? {
        val numero = f.s("number")?.replace(" ", "") ?: return null
        val sal = extremo(f.o("departure"))
        val lle = extremo(f.o("arrival"))
        var est = estado(f.s("status"))
        if (est == Estado.PROGRAMADO && (sal.retraso ?: 0) >= 15) est = Estado.RETRASADO
        val av = f.o("aircraft")
        val fechaLocal = fecha ?: f.o("departure").o("scheduledTime").s("local")?.take(10)
        return Vuelo(
            numero = numero,
            indicativo = f.s("callSign")?.replace(" ", ""),
            aerolineaIata = f.o("airline").s("iata"),
            aerolineaNombre = f.o("airline").s("name"),
            salida = sal, llegada = lle, estado = est,
            avion = Avion(hex = av.s("modeS"), matricula = av.s("reg"), modelo = av.s("model")),
            fecha = fechaLocal,
            conHorarios = true,
        )
    }
}
