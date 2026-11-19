package io.github.cruzpazv.pardela.ui

import io.github.cruzpazv.pardela.data.Vuelo

sealed interface Pantalla {
    data object Inicio : Pantalla
    data object BuscarVuelo : Pantalla
    data object Ajustes : Pantalla
    /** origen: null = ver tablón; "" = elegir origen de trayecto; "MAD" = elegir destino. */
    data class BuscarAeropuerto(val origen: String?) : Pantalla
    data class Detalle(val vuelo: Vuelo) : Pantalla
    data class Tablon(val iata: String) : Pantalla
}
