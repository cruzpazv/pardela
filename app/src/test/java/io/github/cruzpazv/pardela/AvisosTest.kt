package io.github.cruzpazv.pardela

import io.github.cruzpazv.pardela.data.Ajustes
import io.github.cruzpazv.pardela.data.Estado
import io.github.cruzpazv.pardela.data.Extremo
import io.github.cruzpazv.pardela.data.Lugar
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.notif.Avisos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class AvisosTest : Base() {
    private val base = Vuelo(
        "IB3166", salida = Extremo(Lugar("LPA", "Gran Canaria", "Gran Canaria"), hora = "10:00"),
        llegada = Extremo(Lugar("MAD", "Madrid", "Madrid"), hora = "13:00"), estado = Estado.PROGRAMADO,
    )
    private val todos = Ajustes()

    @Test fun silencioQueCruzaLaMedianoche() {
        val a = Ajustes(silencio = true, silencioDesde = 22, silencioHasta = 8)
        assertTrue(Avisos.enSilencio(a, LocalTime.of(23, 30)))
        assertTrue(Avisos.enSilencio(a, LocalTime.of(7, 59)))
        assertFalse(Avisos.enSilencio(a, LocalTime.of(8, 0)))
        assertFalse(Avisos.enSilencio(a, LocalTime.of(12, 0)))
    }

    @Test fun silencioDeDia() {
        val a = Ajustes(silencio = true, silencioDesde = 13, silencioHasta = 15)
        assertTrue(Avisos.enSilencio(a, LocalTime.of(14, 0)))
        assertFalse(Avisos.enSilencio(a, LocalTime.of(16, 0)))
    }

    @Test fun sinSilencioNuncaCalla() {
        assertFalse(Avisos.enSilencio(Ajustes(silencio = false), LocalTime.of(3, 0)))
    }

    @Test fun primeraLecturaNoAvisa() {
        assertTrue(Avisos.cambios(null, base, todos).isEmpty())
    }

    @Test fun despegueAvisaConHoraDeLlegada() {
        val c = Avisos.cambios(base, base.copy(estado = Estado.EN_VUELO), todos)
        assertEquals(1, c.size)
        assertTrue(c[0].texto, c[0].texto.contains("DESPEGADO") && c[0].texto.contains("13:00"))
    }

    @Test fun despegueDesactivadoNoAvisa() {
        assertTrue(Avisos.cambios(base, base.copy(estado = Estado.EN_VUELO), todos.copy(avisoDespegue = false)).isEmpty())
    }

    @Test fun cancelacionSiempreYUrgente() {
        val nada = Ajustes(avisoEmbarque = false, avisoDespegue = false, avisoAterrizaje = false, avisoRetraso = false, avisoPuerta = false, avisoCinta = false)
        val c = Avisos.cambios(base, base.copy(estado = Estado.CANCELADO), nada)
        assertEquals(1, c.size)
        assertTrue(c[0].urgente)
    }

    @Test fun cambioDePuerta() {
        val c = Avisos.cambios(base, base.copy(salida = base.salida.copy(puerta = "B12")), todos)
        assertTrue(c.any { it.texto.contains("B12") })
    }

    @Test fun cintaAlAterrizar() {
        val ahora = base.copy(estado = Estado.ATERRIZADO, llegada = base.llegada.copy(cinta = "7"))
        val c = Avisos.cambios(base.copy(estado = Estado.EN_VUELO), ahora, todos)
        assertEquals(1, c.size)
        assertTrue(c[0].texto, c[0].texto.contains("Madrid") && c[0].texto.contains("7"))
    }

    @Test fun retrasoDe30Minutos() {
        val t = 1_800_000_000_000L
        val antes = base.copy(salida = base.salida.copy(utc = t, utcPrevista = t))
        val ahora = base.copy(salida = base.salida.copy(utc = t, utcPrevista = t + 30 * 60_000L, horaPrevista = "10:30"))
        val c = Avisos.cambios(antes, ahora, todos)
        assertTrue(c.joinToString { it.texto }, c.any { it.texto.contains("30") && it.texto.contains("10:30") })
    }

    @Test fun retrasoSinHoraNuevaNoEscribeNull() {
        val t = 1_800_000_000_000L
        val antes = base.copy(salida = base.salida.copy(utc = t, utcPrevista = t))
        val ahora = base.copy(salida = base.salida.copy(utc = t, utcPrevista = t + 30 * 60_000L, horaPrevista = null))
        Avisos.cambios(antes, ahora, todos).forEach { assertFalse(it.texto, it.texto.contains("null")) }
    }

    @Test fun textosEnIngles() {
        enIngles()
        val c = Avisos.cambios(base, base.copy(estado = Estado.EN_VUELO), todos)
        assertTrue(c[0].texto, c[0].texto.contains("TOOK OFF"))
    }
}
