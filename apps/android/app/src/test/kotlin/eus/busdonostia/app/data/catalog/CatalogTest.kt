package eus.busdonostia.app.data.catalog

import eus.busdonostia.app.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `assets/catalog.json` generado por `mergeDebugCatalog` desde `apps/bot/data/`. */
class CatalogTest {
    private val catalog = TestData.catalog

    @Test
    fun conteos() {
        assertEquals(548, catalog.stops.size)
        assertEquals(514, catalog.stops.count { it.lat != null && it.lon != null })
        assertEquals(39, catalog.lines.size)
        assertEquals(127, catalog.itineraries.size)
    }

    @Test
    fun coordenadasSoloParaParadasDelCatalogoYDentroDeDonostia() {
        assertEquals(catalog.stops.size, catalog.stops.map { it.cod }.toSet().size)
        catalog.stops.filter { it.lat != null }.forEach {
            assertTrue("${it.cod} fuera de rango", it.lat!! in 43.2..43.4 && it.lon!! in -2.1..-1.8)
        }
        // Las 34 sin coordenadas no se inventan.
        assertEquals(34, catalog.stops.count { it.lat == null && it.lon == null })
    }

    @Test
    fun atribucionesYMetadatos() {
        assertEquals(1, catalog.schemaVersion)
        assertEquals("© OpenStreetMap contributors, ODbL", catalog.coordinates.attribution)
        assertTrue(catalog.attribution.contains("Dbus"))
    }

    @Test
    fun paradaRealConLineasEItinerarios() {
        val pioBaroja = catalog.stops.single { it.cod == "340" }
        assertEquals("Pío Baroja 10", pioBaroja.desc)
        assertEquals(listOf("5", "16", "24", "35"), pioBaroja.lines)
        // Toda parada de un itinerario existe en el catálogo.
        val cods = catalog.stops.map { it.cod }.toSet()
        catalog.itineraries.forEach { it2 -> assertTrue(it2.id, cods.containsAll(it2.stops)) }
    }
}
