package eus.busdonostia.app.ui

import eus.busdonostia.app.TestData
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.FavoriteType
import eus.busdonostia.app.ui.lines.LinesFilter
import eus.busdonostia.app.ui.lines.lineRows
import eus.busdonostia.app.ui.lines.starredLines
import eus.busdonostia.app.ui.stops.favoriteRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura de A02 (Favoritas) y A03 (lista y filtros). */
class ScreenLogicTest {
    private val catalog = TestData.catalog

    @Test
    fun lineasTodasNocturnasYFavoritas() {
        val all = lineRows(catalog, "", LinesFilter.ALL, emptySet())
        assertEquals(39, all.size)
        val night = lineRows(catalog, "", LinesFilter.NIGHT, emptySet())
        assertTrue(night.isNotEmpty() && night.all { it.lineId.startsWith("B") })
        val starred = starredLines(listOf(Favorite(FavoriteType.LINE, null, "28", 1), Favorite(FavoriteType.STOP, "1", null, 2)))
        assertEquals(setOf("28"), starred)
        assertEquals(listOf("28"), lineRows(catalog, "", LinesFilter.FAVORITES, starred).map { it.lineId })
        // Búsqueda + filtro: `32` → 32 y 28; solo la 28 es favorita.
        assertEquals(listOf("32", "28"), lineRows(catalog, "32", LinesFilter.ALL, starred).map { it.lineId })
        assertEquals(listOf("28"), lineRows(catalog, "32", LinesFilter.FAVORITES, starred).map { it.lineId })
    }

    @Test
    fun favoritasDeParadaYParadaLinea() {
        val rows = favoriteRows(
            catalog,
            listOf(
                Favorite(FavoriteType.STOP, "340", null, 1),
                Favorite(FavoriteType.STOP_LINE, "1", "28", 3),
                Favorite(FavoriteType.LINE, null, "5", 4), // no es de parada
                Favorite(FavoriteType.STOP, "99999", null, 5), // no está en el catálogo: no se inventa
            ),
        )
        assertEquals(listOf("1", "340"), rows.map { it.stopId }) // más recientes primero
        assertEquals("28", rows[0].favoriteLineId)
        assertEquals(listOf("28"), rows[0].lineIds)
        assertTrue(rows.all { it.starred })
    }

    @Test
    fun lineRowSoloCodigosPublicos() {
        val rows = lineRows(catalog, "", LinesFilter.ALL, emptySet())
        val publicCodes = catalog.lines.map { it.cod }.toSet()
        rows.forEach { assertTrue("lineId público: ${it.lineId}", publicCodes.contains(it.lineId)) }
        rows.forEach { row ->
            val matchingLine = catalog.lines.find { it.cod == row.lineId }
            assertNotNull("lineId ${row.lineId} debe existir como código público", matchingLine)
            assertTrue("lineId debe ser el código público: ${row.lineId}", matchingLine!!.cod == row.lineId)
        }
    }

    @Test
    fun stopRowSinCodigosEnTextosVisibles() {
        val favRows = favoriteRows(
            catalog,
            listOf(Favorite(FavoriteType.STOP, "340", null, 1)),
        )
        favRows.forEach { row ->
            assertFalse("name no debe contener stopId: ${row.name}", row.name.contains(row.stopId))
            row.mainDestination?.let { assertFalse("mainDestination no debe contener stopId: $it", it.contains(row.stopId)) }
            row.lineIds.forEach { assertTrue("lineId público: $it", catalog.lines.map { it.cod }.toSet().contains(it)) }
        }
    }
}