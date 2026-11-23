package io.github.cruzpazv.pardela

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sin Android: comprueba los ficheros de textos (mismas claves y los mismos huecos %1$s en cada idioma). */
class TraduccionesTest {
    private fun textos(dir: String): Map<String, String> {
        val f = File("src/main/res/$dir/strings.xml")
        assertTrue("No existe $f", f.exists())
        return Regex("""<string name="([a-z0-9_]+)">(.*?)</string>""").findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }
    private fun huecos(s: String) = Regex("""%\d+\$[a-z0-9]+""").findAll(s).map { it.value }.toSortedSet()

    @Test fun todosLosIdiomasTienenLasMismasClaves() {
        val en = textos("values")
        for (d in listOf("values-es", "values-ca", "values-gl", "values-eu")) {
            val otro = textos(d)
            assertEquals("Claves distintas en $d", en.keys.sorted(), otro.keys.sorted())
        }
    }

    @Test fun mismosHuecosEnInglesYEspanol() {
        val en = textos("values"); val es = textos("values-es")
        for ((k, v) in en) assertEquals("Huecos distintos en «$k»", huecos(v), huecos(es.getValue(k)))
    }
}
