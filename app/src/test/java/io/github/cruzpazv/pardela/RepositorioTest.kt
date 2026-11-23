package io.github.cruzpazv.pardela

import io.github.cruzpazv.pardela.data.EnDirecto
import io.github.cruzpazv.pardela.data.Estado
import io.github.cruzpazv.pardela.data.Extremo
import io.github.cruzpazv.pardela.data.Lugar
import io.github.cruzpazv.pardela.data.Repositorio
import io.github.cruzpazv.pardela.data.Vuelo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepositorioTest : Base() {
    private val hora = 3_600_000L
    private val ahora = System.currentTimeMillis()
    private fun vuelo(sale: Long?, llega: Long?, fecha: String? = LocalDate.now().toString()) =
        Vuelo("IB3166", salida = Extremo(Lugar("LPA"), utc = sale), llegada = Extremo(Lugar("MAD"), utc = llega), fecha = fecha)

    @Test fun radarSoloCercaDeLaHora() {
        assertTrue(Repositorio.radarUtil(vuelo(ahora + hora, ahora + 4 * hora), ahora))
        assertFalse(Repositorio.radarUtil(vuelo(ahora + 5 * hora, ahora + 8 * hora), ahora))
        assertFalse(Repositorio.radarUtil(vuelo(ahora - 10 * hora, ahora - 7 * hora), ahora))
    }

    @Test fun radarSinHorasSegunLaFecha() {
        assertTrue(Repositorio.radarUtil(vuelo(null, null), ahora))
        assertFalse(Repositorio.radarUtil(vuelo(null, null, LocalDate.now().plusDays(1).toString()), ahora))
    }

    @Test fun unVueloNoRetrocede() {
        // refrescar() se apoya en este orden para no "desdespegar" un vuelo.
        val orden = listOf(Estado.PROGRAMADO, Estado.EN_VUELO, Estado.APROXIMANDO, Estado.ATERRIZADO).map { Repositorio.rango(it) }
        assertEquals(orden.sorted(), orden)
        assertTrue(Repositorio.rango(Estado.CANCELADO) > Repositorio.rango(Estado.ATERRIZADO))
    }

    // Madrid-Barajas: 40.47, -3.56
    private fun enRadar(lat: Double, lon: Double, tierra: Boolean, haceMin: Int) =
        vuelo(null, null).copy(directo = EnDirecto(lat, lon, if (tierra) 0 else 30000, 400, 90.0, tierra, ahora - haceMin * 60_000L))

    @Test fun enTierraJuntoAlDestinoEsAterrizado() {
        val v = enRadar(40.47, -3.56, tierra = true, haceMin = 1)
        assertEquals(Estado.ATERRIZADO, Repositorio.estadoRadar(v, v.copy(estado = Estado.EN_VUELO)))
    }

    @Test fun sinSenalCercaDelDestinoEsAterrizado() {
        val v = enRadar(40.6, -3.7, tierra = false, haceMin = 20)
        assertEquals(Estado.ATERRIZADO, Repositorio.estadoRadar(v, v.copy(estado = Estado.EN_VUELO)))
    }

    @Test fun sinSenalSobreElMarSigueEnVuelo() {
        val v = enRadar(33.0, -12.0, tierra = false, haceMin = 30)
        assertEquals(Estado.EN_VUELO, Repositorio.estadoRadar(v, v.copy(estado = Estado.EN_VUELO)))
    }

    @Test fun sinPosicionNiHistoriaEsDesconocido() {
        assertEquals(Estado.DESCONOCIDO, Repositorio.estadoRadar(vuelo(null, null), null))
    }

    @Test fun distancias() {
        // Gran Canaria – Madrid ≈ 1750 km
        val km = Repositorio.km(27.93, -15.39, 40.47, -3.56)
        assertTrue("$km", km in 1700.0..1800.0)
    }
}
