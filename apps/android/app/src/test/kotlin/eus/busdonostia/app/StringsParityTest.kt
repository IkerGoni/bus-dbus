package eus.busdonostia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** B14: `es` (values/) y `eu` (values-eu/) tienen exactamente las mismas claves y ninguna vacía. */
class StringsParityTest {
    private fun load(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("No existe ${file.path}", file.exists())
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val el = nodes.item(i)
            el.attributes.getNamedItem("name").nodeValue to el.textContent.trim()
        }
    }

    @Test
    fun esYEuTienenLasMismasClaves() {
        val es = load("values")
        val eu = load("values-eu")
        assertEquals(es.keys.sorted(), eu.keys.sorted())
    }

    @Test
    fun ningunTextoVacio() {
        (load("values") + load("values-eu").mapKeys { "eu:${it.key}" })
            .forEach { (k, v) -> assertTrue("Texto vacío: $k", v.isNotEmpty()) }
    }
}
