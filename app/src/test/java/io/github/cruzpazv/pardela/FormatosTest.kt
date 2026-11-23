package io.github.cruzpazv.pardela

import io.github.cruzpazv.pardela.data.Almacen
import io.github.cruzpazv.pardela.data.Estado
import io.github.cruzpazv.pardela.data.Extremo
import io.github.cruzpazv.pardela.data.Formato
import io.github.cruzpazv.pardela.data.Lugar
import io.github.cruzpazv.pardela.data.Resumen
import io.github.cruzpazv.pardela.data.Tiempo
import io.github.cruzpazv.pardela.data.Unidades
import io.github.cruzpazv.pardela.data.Vuelo
import io.github.cruzpazv.pardela.notif.Widget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FormatosTest : Base() {
    @Test fun alturasYVelocidades() {
        // 40.000 ft = 12.192 m → se redondea a centenas.
        assertEquals("12.100 m", Formato.altura(40_000, Unidades.METRICO))
        assertEquals("40.000 ft", Formato.altura(40_000, Unidades.AVIACION))
        assertEquals("833 km/h", Formato.velocidad(450, Unidades.METRICO))
        enIngles()
        assertEquals("12,100 m", Formato.altura(40_000, Unidades.METRICO))
    }

    private val aterrizado = Vuelo(
        "IB3166", aerolineaNombre = "Iberia",
        salida = Extremo(Lugar("LPA", ciudad = "Gran Canaria"), hora = "10:00"),
        llegada = Extremo(Lugar("MAD", ciudad = "Madrid"), hora = "13:00", cinta = "7"),
        estado = Estado.ATERRIZADO, fecha = LocalDate.now().toString(),
    )

    @Test fun vozEnLosDosIdiomas() {
        val es = Resumen.voz(aterrizado)
        assertTrue(es, es.contains("I B 3166") && es.contains("cinta 7") && es.contains("Madrid"))
        enIngles()
        val en = Resumen.voz(aterrizado)
        assertTrue(en, en.contains("belt 7") && !en.contains("cinta"))
    }

    @Test fun textoParaCompartir() {
        val t = Resumen.compartir(aterrizado)
        assertTrue(t, t.contains("IB3166") && t.contains("Gran Canaria (LPA) → Madrid (MAD)") && t.contains("flightradar24"))
    }

    @Test fun tiempoYHora() {
        assertEquals("despejado", Tiempo.describir(Tiempo.Parte(20.0, 0, true)).second)
        assertEquals("☀", Tiempo.describir(Tiempo.Parte(20.0, 0, true)).first)
        assertEquals("la misma hora que aquí", Tiempo.diferencia(ZoneId.systemDefault()))
        enIngles()
        assertEquals("clear", Tiempo.describir(Tiempo.Parte(20.0, 0, true)).second)
    }

    @Test fun widgetEligeElProximoVuelo() {
        val ahora = System.currentTimeMillis(); val h = 3_600_000L; val hoy = LocalDate.now().toString()
        fun v(n: String, sale: Long, llega: Long, e: Estado = Estado.PROGRAMADO) =
            Vuelo(n, salida = Extremo(utc = sale), llegada = Extremo(utc = llega), estado = e, fecha = hoy)
        val manana = v("B", ahora + 20 * h, ahora + 22 * h)
        val luego = v("A", ahora + 2 * h, ahora + 4 * h)
        val viejo = v("C", ahora - 6 * h, ahora - 4 * h, Estado.ATERRIZADO)
        val reciente = v("D", ahora - 3 * h, ahora - h, Estado.ATERRIZADO)
        assertEquals("A", Widget.elegir(listOf(manana, luego, viejo), ahora)?.numero)
        // Un aterrizado hace menos de 2 h se queda (para ir a recoger a alguien).
        assertEquals("D", Widget.elegir(listOf(manana, reciente), ahora)?.numero)
        assertNull(Widget.elegir(listOf(viejo), ahora))
    }

    @Test fun copiaSinClave() {
        Almacen.ajustar { it.copy(claveApi = "CLAVE-SECRETA") }
        val copia = Almacen.exportar()
        assertFalse(copia.contains("CLAVE-SECRETA"))
        // Al recuperar, se conserva la clave del móvil.
        assertTrue(Almacen.importar(copia))
        assertEquals("CLAVE-SECRETA", Almacen.ajustes.claveApi)
        assertFalse(Almacen.importar("esto no es una copia"))
    }
}
