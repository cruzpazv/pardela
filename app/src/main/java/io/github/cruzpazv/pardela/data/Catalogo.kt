package io.github.cruzpazv.pardela.data

import android.content.Context
import java.text.Normalizer

data class Aeropuerto(
    val iata: String, val icao: String, val nombre: String, val ciudad: String,
    val pais: String, val lat: Double, val lon: Double, val grande: Boolean,
) {
    val lugar get() = Lugar(iata, nombre, ciudad)
}

object Aeropuertos {
    private var lista: List<Aeropuerto> = emptyList()
    private var porIata: Map<String, Aeropuerto> = emptyMap()
    private var porIcao: Map<String, Aeropuerto> = emptyMap()

    fun cargar(ctx: Context) {
        if (lista.isNotEmpty()) return
        lista = ctx.assets.open("aeropuertos.csv").bufferedReader().useLines { lines ->
            lines.mapNotNull { l ->
                val c = l.split('|')
                if (c.size < 8) null else intentar {
                    Aeropuerto(c[0], c[1], c[2], c[3], c[4], c[5].toDouble(), c[6].toDouble(), c[7] == "1")
                }.getOrNull()
            }.toList()
        }
        porIata = lista.associateBy { it.iata }
        porIcao = lista.associateBy { it.icao }
    }

    fun iata(code: String?) = code?.let { porIata[it.uppercase()] }
    fun icao(code: String?) = code?.let { porIcao[it.uppercase()] }

    private fun plano(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase()

    fun buscar(texto: String): List<Aeropuerto> {
        val t = texto.trim()
        if (t.length < 2) return emptyList()
        val up = t.uppercase()
        val exacto = listOfNotNull(porIata[up], porIcao[up])
        val p = plano(t)
        val resto = lista.asSequence()
            .filter { it !in exacto && (plano(it.ciudad).contains(p) || plano(it.nombre).contains(p)) }
            .sortedWith(compareBy({ !plano(it.ciudad).startsWith(p) }, { !it.grande }, { it.pais != "ES" }))
            .take(20).toList()
        return exacto + resto
    }
}

data class Aerolinea(val iata: String, val icao: String, val nombre: String)

/** Aerolíneas habituales. Sirve para pasar de "IB3166" (billete) a "IBE3166" (radar). */
object Aerolineas {
    private val lista = listOf(
        Aerolinea("IB", "IBE", "Iberia"), Aerolinea("I2", "IBS", "Iberia Express"),
        Aerolinea("YW", "ANE", "Air Nostrum"), Aerolinea("VY", "VLG", "Vueling"),
        Aerolinea("UX", "AEA", "Air Europa"), Aerolinea("NT", "IBB", "Binter"),
        Aerolinea("V7", "VOE", "Volotea"), Aerolinea("FR", "RYR", "Ryanair"),
        Aerolinea("U2", "EZY", "easyJet"), Aerolinea("EC", "EJU", "easyJet Europe"),
        Aerolinea("DS", "EZS", "easyJet Suiza"), Aerolinea("W6", "WZZ", "Wizz Air"),
        Aerolinea("W4", "WMT", "Wizz Air Malta"), Aerolinea("DY", "NOZ", "Norwegian"),
        Aerolinea("D8", "NSZ", "Norwegian"), Aerolinea("BA", "BAW", "British Airways"),
        Aerolinea("AF", "AFR", "Air France"), Aerolinea("KL", "KLM", "KLM"),
        Aerolinea("HV", "TRA", "Transavia"), Aerolinea("TO", "TVF", "Transavia Francia"),
        Aerolinea("LH", "DLH", "Lufthansa"), Aerolinea("EW", "EWG", "Eurowings"),
        Aerolinea("DE", "CFG", "Condor"), Aerolinea("LX", "SWR", "Swiss"),
        Aerolinea("OS", "AUA", "Austrian"), Aerolinea("SN", "BEL", "Brussels Airlines"),
        Aerolinea("TP", "TAP", "TAP Air Portugal"), Aerolinea("AZ", "ITY", "ITA Airways"),
        Aerolinea("EI", "EIN", "Aer Lingus"), Aerolinea("SK", "SAS", "SAS"),
        Aerolinea("AY", "FIN", "Finnair"), Aerolinea("LO", "LOT", "LOT"),
        Aerolinea("A3", "AEE", "Aegean"), Aerolinea("TK", "THY", "Turkish Airlines"),
        Aerolinea("PC", "PGT", "Pegasus"), Aerolinea("LS", "EXS", "Jet2"),
        Aerolinea("BY", "TOM", "TUI"), Aerolinea("X3", "TUI", "TUIfly"),
        Aerolinea("EK", "UAE", "Emirates"), Aerolinea("QR", "QTR", "Qatar Airways"),
        Aerolinea("EY", "ETD", "Etihad"), Aerolinea("SV", "SVA", "Saudia"),
        Aerolinea("AT", "RAM", "Royal Air Maroc"), Aerolinea("MS", "MSR", "EgyptAir"),
        Aerolinea("LY", "ELY", "El Al"), Aerolinea("AA", "AAL", "American Airlines"),
        Aerolinea("UA", "UAL", "United"), Aerolinea("DL", "DAL", "Delta"),
        Aerolinea("B6", "JBU", "JetBlue"), Aerolinea("AC", "ACA", "Air Canada"),
        Aerolinea("AM", "AMX", "Aeroméxico"), Aerolinea("AV", "AVA", "Avianca"),
        Aerolinea("LA", "LAN", "LATAM"), Aerolinea("CM", "CMP", "Copa"),
        Aerolinea("AR", "ARG", "Aerolíneas Argentinas"), Aerolinea("PU", "PUA", "Plus Ultra"),
        Aerolinea("2W", "WLX", "World2fly"), Aerolinea("EB", "PLM", "Wamos Air"),
        Aerolinea("KE", "KAL", "Korean Air"), Aerolinea("NH", "ANA", "ANA"),
        Aerolinea("JL", "JAL", "Japan Airlines"), Aerolinea("CX", "CPA", "Cathay Pacific"),
        Aerolinea("SQ", "SIA", "Singapore Airlines"), Aerolinea("QF", "QFA", "Qantas"),
        Aerolinea("CA", "CCA", "Air China"), Aerolinea("MU", "CES", "China Eastern"),
        Aerolinea("CZ", "CSN", "China Southern"), Aerolinea("ET", "ETH", "Ethiopian"),
        Aerolinea("4Y", "OCN", "Discover"), Aerolinea("WK", "EDW", "Edelweiss"),
        Aerolinea("BT", "BTI", "airBaltic"), Aerolinea("RO", "ROT", "TAROM"),
        Aerolinea("OU", "CTN", "Croatia Airlines"), Aerolinea("JU", "ASL", "Air Serbia"),
        Aerolinea("FI", "ICE", "Icelandair"), Aerolinea("S4", "RZO", "Azores Airlines"),
    )
    private val porIata = lista.associateBy { it.iata }
    private val porIcao = lista.associateBy { it.icao }

    fun iata(c: String?) = c?.let { porIata[it.uppercase()] }
    fun icao(c: String?) = c?.let { porIcao[it.uppercase()] }

    /** URL del logo. Sin clave: CDN público de logos por código IATA. */
    fun logo(iata: String?) = iata?.takeIf { it.length == 2 }?.let { "https://pics.avs.io/200/80/${it.uppercase()}.png" }

    /** "IB 3166" → ("IB", "3166"). Acepta también "IBE3166". */
    fun partir(texto: String): Pair<String, String>? {
        val t = texto.uppercase().replace(Regex("[^A-Z0-9]"), "")
        Regex("^([A-Z]{3})(\\d{1,4}[A-Z]?)$").find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        Regex("^([A-Z0-9]{2})(\\d{1,4}[A-Z]?)$").find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        return null
    }

    /** Número comercial (IB3166) a partir de cualquier forma. */
    fun numeroComercial(texto: String): String {
        val (pre, num) = partir(texto) ?: return texto.uppercase().replace(" ", "")
        val iata = if (pre.length == 3) icao(pre)?.iata ?: pre else pre
        return iata + num.trimStart('0').ifEmpty { "0" }
    }

    /** Indicativo de radar (IBE3166) a partir del número comercial. */
    fun indicativo(texto: String): String? {
        val (pre, num) = partir(texto) ?: return null
        val icao = if (pre.length == 3) pre else iata(pre)?.icao ?: return null
        return icao + num
    }
}

/** Familia del avión para mostrar el distintivo del fabricante. */
object Fabricantes {
    data class Marca(val nombre: String, val color: Long)

    private val AIRBUS = Marca("AIRBUS", 0xFF00205B)
    private val BOEING = Marca("BOEING", 0xFF0033A1)
    private val EMBRAER = Marca("EMBRAER", 0xFF0B2D71)
    private val ATR = Marca("ATR", 0xFF00A0DF)
    private val BOMBARDIER = Marca("BOMBARDIER", 0xFF222222)
    private val DE_HAVILLAND = Marca("DE HAVILLAND", 0xFF8A1538)
    private val OTRO = Marca("AVIÓN", 0xFF333333)

    fun de(tipoIcao: String?, fabricante: String?): Marca {
        val f = fabricante?.uppercase() ?: ""
        val t = tipoIcao?.uppercase() ?: ""
        return when {
            "AIRBUS" in f || t.startsWith("A3") || t.startsWith("A2") || t.startsWith("BCS") -> AIRBUS
            "BOEING" in f || Regex("^B7\\d").containsMatchIn(t) || t.startsWith("B3") -> BOEING
            "EMBRAER" in f || t.startsWith("E1") || t.startsWith("E2") || t.startsWith("E7") -> EMBRAER
            "ATR" in f || "AVIONS DE TRANSPORT" in f || t.startsWith("AT4") || t.startsWith("AT7") -> ATR
            "HAVILLAND" in f || t.startsWith("DH8") -> DE_HAVILLAND
            "BOMBARDIER" in f || "CANADAIR" in f || t.startsWith("CRJ") -> BOMBARDIER
            else -> OTRO
        }
    }

    /** Nombre legible del modelo a partir del código ICAO. */
    fun modelo(t: String?): String? = when (t?.uppercase()) {
        null -> null
        "A319" -> "Airbus A319"; "A320" -> "Airbus A320"; "A321" -> "Airbus A321"
        "A19N" -> "Airbus A319neo"; "A20N" -> "Airbus A320neo"; "A21N" -> "Airbus A321neo"
        "A332" -> "Airbus A330-200"; "A333" -> "Airbus A330-300"; "A339" -> "Airbus A330-900neo"
        "A343" -> "Airbus A340-300"; "A346" -> "Airbus A340-600"
        "A359" -> "Airbus A350-900"; "A35K" -> "Airbus A350-1000"; "A388" -> "Airbus A380"
        "BCS1" -> "Airbus A220-100"; "BCS3" -> "Airbus A220-300"
        "B737" -> "Boeing 737-700"; "B738" -> "Boeing 737-800"; "B739" -> "Boeing 737-900"
        "B38M" -> "Boeing 737 MAX 8"; "B39M" -> "Boeing 737 MAX 9"
        "B752" -> "Boeing 757-200"; "B763" -> "Boeing 767-300"; "B772" -> "Boeing 777-200"
        "B77W" -> "Boeing 777-300ER"; "B788" -> "Boeing 787-8"; "B789" -> "Boeing 787-9"
        "B78X" -> "Boeing 787-10"; "B744" -> "Boeing 747-400"; "B748" -> "Boeing 747-8"
        "E190" -> "Embraer 190"; "E195" -> "Embraer 195"; "E290" -> "Embraer 190-E2"
        "E295" -> "Embraer 195-E2"; "E75L", "E75S" -> "Embraer 175"
        "AT72" -> "ATR 72"; "AT75" -> "ATR 72-500"; "AT76" -> "ATR 72-600"; "AT45", "AT46" -> "ATR 42"
        "CRJ9" -> "Bombardier CRJ900"; "CRJ1" -> "Bombardier CRJ1000"; "CRJ2" -> "Bombardier CRJ200"
        "DH8D" -> "De Havilland Dash 8-400"
        else -> null
    }
}
