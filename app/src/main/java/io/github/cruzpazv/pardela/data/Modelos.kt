package io.github.cruzpazv.pardela.data

import kotlinx.serialization.Serializable

@Serializable
enum class Estado(val texto: String, val simbolo: String = "") {
    PROGRAMADO("PROGRAMADO"),
    FACTURACION("FACTURACIÓN"),
    EMBARCANDO("EMBARCANDO", "»"),
    PUERTA_CERRADA("PUERTA CERRADA", "■"),
    RETRASADO("RETRASADO", "⚠"),
    EN_VUELO("EN VUELO", "✈"),
    APROXIMANDO("EN APROXIMACIÓN", "▼"),
    ATERRIZADO("ATERRIZADO", "✔"),
    CANCELADO("CANCELADO", "✕"),
    DESVIADO("DESVIADO", "↪"),
    DESCONOCIDO("—");

    /** Texto con símbolo: el estado se entiende sin depender del color. */
    val etiqueta get() = if (simbolo.isEmpty()) texto else "$simbolo $texto"
    val terminado get() = this == ATERRIZADO || this == CANCELADO || this == DESVIADO
}

@Serializable
data class Lugar(
    val iata: String = "",
    val nombre: String = "",
    val ciudad: String = "",
)

/** Un extremo del vuelo. Horas como "HH:mm" en hora local del aeropuerto; utc en epoch ms. */
@Serializable
data class Extremo(
    val lugar: Lugar = Lugar(),
    val hora: String? = null,
    val horaPrevista: String? = null,
    val utc: Long? = null,
    val utcPrevista: Long? = null,
    val terminal: String? = null,
    val puerta: String? = null,
    val cinta: String? = null,
) {
    /** Minutos de retraso (positivo = tarde). */
    val retraso: Int?
        get() = if (utc != null && utcPrevista != null) ((utcPrevista - utc) / 60000).toInt() else null
}

@Serializable
data class EnDirecto(
    val lat: Double,
    val lon: Double,
    val altitudPies: Int?,
    val velocidadNudos: Int?,
    val rumbo: Double?,
    val enTierra: Boolean,
    val momento: Long,
)

/** La última posición tiene menos de 5 min (si no, el avión está fuera de cobertura del radar). */
val EnDirecto.reciente get() = System.currentTimeMillis() - momento < 5 * 60_000L

@Serializable
data class Avion(
    val hex: String? = null,
    val matricula: String? = null,
    val tipoIcao: String? = null,
    val modelo: String? = null,
    val fabricante: String? = null,
    val propietario: String? = null,
    val foto: String? = null,
    val fotoAutor: String? = null,
)

@Serializable
data class Vuelo(
    val numero: String,
    val indicativo: String? = null,
    val aerolineaIata: String? = null,
    val aerolineaNombre: String? = null,
    val salida: Extremo = Extremo(),
    val llegada: Extremo = Extremo(),
    val estado: Estado = Estado.DESCONOCIDO,
    val avion: Avion = Avion(),
    val directo: EnDirecto? = null,
    val fecha: String? = null,
    /** true si los datos vienen del tablón (clave) y no solo de radar. */
    val conHorarios: Boolean = false,
) {
    val clave get() = "$numero|${fecha ?: ""}|${salida.lugar.iata}"
    val ruta get() = "${salida.lugar.iata.ifBlank { "???" }} → ${llegada.lugar.iata.ifBlank { "???" }}"
}

@Serializable
data class VueloSeguido(
    val numero: String,
    val fecha: String?,
    val origen: String = "",
    val ultimo: Vuelo? = null,
    val ultimaConsultaClave: Long = 0,
) {
    val clave get() = "$numero|${fecha ?: ""}|$origen"
}

@Serializable
data class Trayecto(
    val origen: String,
    val destino: String,
    /** Último estado conocido de cada vuelo del trayecto, por número. */
    val vistos: Map<String, Vuelo> = emptyMap(),
    val ultimaConsulta: Long = 0,
) {
    val clave get() = "$origen-$destino"
}

@Serializable
enum class Tema(val texto: String) { AUTO("AUTO"), CLARO("CLARO"), OSCURO("OSCURO") }

@Serializable
enum class Unidades(val texto: String) { METRICO("METROS · KM/H"), AVIACION("PIES · NUDOS") }

/** Formato de alturas y velocidades según el ajuste de unidades. */
object Formato {
    private val es = java.util.Locale("es", "ES")
    fun altura(pies: Int, u: Unidades) =
        if (u == Unidades.METRICO) String.format(es, "%,d m", (pies * 0.3048).toInt() / 100 * 100)
        else String.format(es, "%,d ft", pies / 100 * 100)
    fun velocidad(nudos: Int, u: Unidades) =
        if (u == Unidades.METRICO) "${(nudos * 1.852).toInt()} km/h" else "$nudos kt"
}

@Serializable
data class Ajustes(
    val claveApi: String = "",
    val textoGrande: Boolean = true,
    val sonido: Boolean = true,
    val bienvenidaVista: Boolean = false,
    val tema: Tema = Tema.AUTO,
    val animaciones: Boolean = true,
    val daltonismo: Boolean = false,
    val unidades: Unidades = Unidades.METRICO,
    // Qué avisos recibir (cancelaciones y desvíos siempre se avisan)
    val avisoEmbarque: Boolean = true,
    val avisoDespegue: Boolean = true,
    val avisoAterrizaje: Boolean = true,
    val avisoRetraso: Boolean = true,
    val avisoPuerta: Boolean = true,
    val avisoCinta: Boolean = true,
    // Horas de silencio: avisos sin sonido (salvo cancelaciones)
    val silencio: Boolean = false,
    val silencioDesde: Int = 22,
    val silencioHasta: Int = 8,
)

@Serializable
data class Datos(
    val vuelos: List<VueloSeguido> = emptyList(),
    val aeropuertos: List<String> = emptyList(),
    val trayectos: List<Trayecto> = emptyList(),
    val ajustes: Ajustes = Ajustes(),
)
