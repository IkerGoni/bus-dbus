package eus.busdonostia.app.ui

import eus.busdonostia.app.domain.AlertDetail
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.ComputedAlertStatus
import eus.busdonostia.app.ui.components.SharedKeys
import eus.busdonostia.app.ui.components.SharedOrigins
import eus.busdonostia.app.ui.home.FavoriteStopCard
import eus.busdonostia.app.ui.home.HomeUiState
import eus.busdonostia.app.ui.home.homeSharedOrigins
import eus.busdonostia.app.ui.stops.StopRow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Claves de shared element sin colisiones (P1-1) y ruta de parada con origen (P2-4). */
class SharedKeysTest {
    private fun favorite(stopId: String, lineId: String?) = FavoriteStopCard(
        row = StopRow(
            stopId = stopId,
            name = "Parada $stopId",
            lineIds = listOfNotNull(lineId, "28").distinct(),
            favoriteLineId = lineId,
            starred = true,
        ),
        view = ArrivalsView.Loading,
        arrivals = emptyList(),
    )

    private fun alert(stopId: String, lineId: String) = AlertItem(
        rule = AlertRule(
            id = "a1",
            stopId = stopId,
            stopName = "Parada $stopId",
            lineId = lineId,
            minutesAhead = 5,
            createdAtMs = 0,
        ),
        status = ComputedAlertStatus.ACTIVE,
        detail = AlertDetail.NONE,
        nextArrival = null,
        remainingMin = null,
    )

    @Test
    fun `inicio - aviso y favoritas de la misma parada no colisionan`() {
        val state = HomeUiState(
            nextAlert = alert("1111", "28"),
            favorites = listOf(favorite("1111", null), favorite("1111", "28")),
        )
        val origins = homeSharedOrigins(state)
        assertEquals(3, origins.size)
        assertEquals(origins.size, origins.toSet().size)

        // Claves reales que emite la UI: aviso + favorita completa + favorita parada+línea.
        val keys = listOf(
            SharedKeys.stopTitle("1111", SharedOrigins.NEXT_ALERT),
            SharedKeys.stopTitle("1111", SharedOrigins.favorite("1111", null)),
            SharedKeys.stopTitle("1111", SharedOrigins.favorite("1111", "28")),
        )
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `timeline - parada repetida en linea circular no colisiona`() {
        val keys = (0 until 3).map { index -> SharedKeys.stopTitle("999", SharedOrigins.timeline(0, index)) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `ruta de parada con y sin origen`() {
        assertEquals("?line=28&from=fav-1-28", stopRouteQuery("28", "fav-1-28"))
        assertEquals("?line=28", stopRouteQuery("28", null))
        assertEquals("?from=search-1", stopRouteQuery(null, "search-1"))
        assertEquals("", stopRouteQuery(null, null))
    }
}
