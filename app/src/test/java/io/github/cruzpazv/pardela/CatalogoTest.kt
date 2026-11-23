package io.github.cruzpazv.pardela

import io.github.cruzpazv.pardela.data.Aerolineas
import io.github.cruzpazv.pardela.data.Aeropuertos
import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Vuelo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoTest : Base() {
    @Test fun numerosDeVuelo() {
        assertEquals("IB" to "3166", Aerolineas.partir("ib 3166"))
        assertEquals("IBE" to "3166", Aerolineas.partir("IBE3166"))
        assertEquals("IB3166", Aerolineas.numeroComercial("IBE3166"))
        assertEquals("IB316", Aerolineas.numeroComercial("IBE0316"))
        assertEquals("IBE3166", Aerolineas.indicativo("IB3166"))
        assertEquals("U23456", Aerolineas.numeroComercial("u2 3456"))
    }

    @Test fun posicionamientos() {
        assertTrue(Aerolineas.posicionamiento("RSC678"))
        assertTrue(Aerolineas.posicionamiento("ZN307"))
        assertFalse(Aerolineas.posicionamiento("NT123"))
    }

    @Test fun ocultarPosicionamientos() {
        val l = listOf(Vuelo("RSC678"), Vuelo("NT123"))
        assertEquals(2, Aerolineas.visibles(l).size)
        Almacen.ajustar { it.copy(ocultarPosicionamiento = true) }
        assertEquals(listOf("NT123"), Aerolineas.visibles(l).map { it.numero })
    }

    @Test fun aeropuertos() {
        assertEquals("GCLP", Aeropuertos.iata("LPA")?.icao)
        assertEquals("MAD", Aeropuertos.icao("LEMD")?.iata)
        // Sin tildes y por ciudad
        assertTrue(Aeropuertos.buscar("malaga").any { it.iata == "AGP" })
        assertTrue(Aeropuertos.buscar("Málaga").any { it.iata == "AGP" })
        assertTrue(Aeropuertos.buscar("TFN").any { it.iata == "TFN" })
    }
}
