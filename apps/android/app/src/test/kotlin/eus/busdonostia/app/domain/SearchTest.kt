package eus.busdonostia.app.domain

import eus.busdonostia.app.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Búsqueda offline y cercanía sobre el catálogo real. */
class SearchTest {
    private val catalog = TestData.catalog

    private fun stops(q: String) = searchStops(catalog, q).joinToString(",") { it.cod }

    private fun lines(q: String) = searchLines(catalog, q).joinToString(",") { it.cod }

    // Esperados generados con `searchStops` / `searchLines` de packages/core/dist (mismo catálogo).

    @Test
    fun searchStopsParidadConCore() {
        assertEquals("340", stops("340"))
        assertEquals("34,340,341,344,345,347,348,349", stops("34"))
        assertEquals("1,34,56,79,102,238", stops("boulevard"))
        assertEquals("339,340,344,345,304,315", stops("pio baroja"))
        assertEquals("339,340,344,345,347,164,304,315", stops("PÍO"))
        assertEquals("1,10,11,12,13,14,15,16", stops("1"))
        assertEquals("40", stops("ondarreta"))
        assertEquals("5,50,51,52,53,54,55,56", stops("L5"))
        assertEquals("", stops("zzz"))
        assertEquals("", stops("   "))
    }

    @Test
    fun searchLinesParidadConCoreYGanaLaPublica() {
        assertEquals("28,26", lines("28")) // 26 tiene id interno 28
        assertEquals("32,28", lines("32")) // 32 es pública: gana a la 28 (id interno 32)
        assertEquals("B9", lines("B9"))
        assertEquals("B9", lines("b09"))
        assertEquals("17,28,31,35", lines("ospitaleak"))
        assertEquals("17,21,24,26,27,28,B4,B9", lines("amara"))
        assertEquals("21,23,24,25,26,27,28,29", lines("2"))
        assertEquals("", lines("zzz"))
    }

    @Test
    fun searchKeyQuitaAcentosYEspacios() {
        assertEquals("pio baroja", searchKey("  Pío   Baroja "))
        assertEquals("anorga", searchKey("AÑORGA"))
    }

    @Test
    fun cercanasSoloConCoordenadasYOrdenadas() {
        val boulevard = catalog.stopByCode("1")!!
        val nearby = nearbyStops(catalog, boulevard.lat!!, boulevard.lon!!)
        assertEquals(20, nearby.size)
        assertEquals("1", nearby.first().stop.cod)
        assertEquals(0L, nearby.first().meters)
        assertTrue(nearby.zipWithNext().all { (a, b) -> a.meters <= b.meters })
        assertTrue(nearby.all { it.stop.lat != null && it.stop.lon != null })
        // Las 34 sin coordenadas nunca entran, ni pidiendo todas.
        assertEquals(514, nearbyStops(catalog, boulevard.lat!!, boulevard.lon!!, limit = 1_000).size)
    }

    @Test
    fun minutosAPieA80MetrosPorMinuto() {
        assertEquals(1, walkMinutes(0))
        assertEquals(1, walkMinutes(80))
        assertEquals(2, walkMinutes(81))
        assertEquals(2, walkMinutes(140)) // «140 m · 2 min» de la spec
        assertEquals(21, walkMinutes(1_626))
    }

    @Test
    fun itinerariosYOrdenDeLineas() {
        // Alias interno 32 → línea pública 28 (sin parada gana la pública 32).
        assertTrue(catalog.itinerariesForLine("28").all { it.lineCod == "28" })
        assertTrue(catalog.itinerariesForLine("28").isNotEmpty())
        assertTrue(catalog.itinerariesForLine("32").all { it.lineCod == "32" })
        assertEquals(127, catalog.lines.sumOf { catalog.itinerariesForLine(it.cod).size })
        val sorted = sortLines(catalog.lines).map { it.cod }
        assertEquals(listOf("5", "8", "9"), sorted.take(3))
        assertEquals("46", sorted[sorted.indexOf("B1") - 1])
        assertEquals("B10", sorted.last())
    }
}
