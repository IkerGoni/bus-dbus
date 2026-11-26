package eus.busdonostia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** B14: `es` (values/), `eu` (values-eu/) y `en` (values-en/) tienen exactamente las mismas claves y ninguna vacía. */
class StringsParityTest {
    /** Todas las `<string>` de los `strings*.xml` de [dir]. */
    private fun load(dir: String): Map<String, String> {
        val files = File("src/main/res/$dir").listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty()
        assertTrue("No hay strings*.xml en $dir", files.any { it.name == "strings.xml" })
        return files.sortedBy { it.name }.flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { i ->
                val el = nodes.item(i)
                el.attributes.getNamedItem("name").nodeValue to el.textContent.trim()
            }
        }.toMap()
    }

    @Test
    fun esYEuTienenLasMismasClaves() {
        val es = load("values")
        val eu = load("values-eu")
        assertEquals(es.keys.sorted(), eu.keys.sorted())
    }

    @Test
    fun esYEnTienenLasMismasClaves() {
        val es = load("values")
        val en = load("values-en")
        assertEquals(es.keys.sorted(), en.keys.sorted())
    }

    @Test
    fun ningunTextoVacio() {
        (load("values") + load("values-eu").mapKeys { "eu:${it.key}" } + load("values-en").mapKeys { "en:${it.key}" })
            .forEach { (k, v) -> assertTrue("Texto vacío: $k", v.isNotEmpty()) }
    }

    @Test
    fun alertAlarmStringsContienenFormatoSegundos() {
        listOf("values", "values-eu", "values-en").forEach { dir ->
            val strings = load(dir)
            val hint = strings["alert_mode_alarm_hint"]
            val note = strings["alert_preview_alarm_note"]
            assertNotNull("alert_mode_alarm_hint debe existir en $dir", hint)
            assertNotNull("alert_preview_alarm_note debe existir en $dir", note)
            assertTrue("alert_mode_alarm_hint en $dir debe contener %1\$d: $hint", hint!!.contains("%1\$d"))
            assertTrue("alert_preview_alarm_note en $dir debe contener %1\$d: $note", note!!.contains("%1\$d"))
        }
    }
}
