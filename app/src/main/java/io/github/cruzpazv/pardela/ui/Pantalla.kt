package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.data.Avion
import io.github.cruzpazv.pardela.data.Vuelo

sealed interface Pantalla {
    data object Inicio : Pantalla
    /** Cada pantalla de la pila guarda su estado: al volver atrás se ve igual que se dejó. */
    class BuscarVuelo : Pantalla { val estado = EstadoBusqueda() }
    data object Ajustes : Pantalla
    data class AjustesDe(val seccion: SeccionAjustes) : Pantalla
    /** origen: null = ver tablón; "" = elegir origen de trayecto; "MAD" = elegir destino. */
    data class BuscarAeropuerto(val origen: String?) : Pantalla
    data class Detalle(val vuelo: Vuelo) : Pantalla
    data class Tablon(val iata: String) : Pantalla { val estado = EstadoTablon() }
    data class HistorialAvion(val avion: Avion) : Pantalla
    data class Mapa(val vuelo: Vuelo) : Pantalla
    /** Aviones cerca de un punto; sin coordenadas = de la posición del móvil. */
    data class Cerca(val lat: Double?, val lon: Double?, val titulo: String) : Pantalla
}
