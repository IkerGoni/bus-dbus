package eus.busdonostia.app.data.api

import eus.busdonostia.app.TestData.fixture
import eus.busdonostia.app.data.db.lineColors
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.StopFeedStatus
import eus.busdonostia.app.domain.findMatchingArrival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `tiemposParada` → StopFeed contra respuestas reales capturadas con `curl` en
 * `packages/contracts/fixtures/` (las mismas que lee `packages/core/tests/core.test.ts`).
 *
 * Paridad con `@dbus/core` sobre `tiemposParada-1.json` (assertions equivalentes en
 * core.test.ts): estado DATOS, 2 tiempos; orden 28 → 21; la 28 con id interno 32,
 * destino «Ospitaleak», 36 min, horario (tipo=2), hora 07:30; color real de la 28
 * -811520; `avisos: null` → sin aviso; `findMatchingArrival` 28 con 40 min casa,
 * con 10 min no, con destino «Amara-Mutualitateak» no.
 */
class StopFeedParserTest {
    private val fetchedAt = 1_700_000_000_000L

    @Test
    fun paridadConCoreEnTiemposParada1() {
        val feed = parseStopFeed(fixture("tiemposParada-1.json"), fetchedAt)
        assertEquals(StopFeedStatus.OK, feed.status)
        assertEquals("Boulevard 9", feed.stopName)
        assertNull(feed.notice)
        assertEquals(listOf("28", "21"), feed.arrivals.map { it.lineId })
        val l28 = feed.arrivals[0]
        assertEquals("32", l28.internalLineId)
        assertEquals("Ospitaleak", l28.destination)
        assertEquals(36, l28.minutesAtFetch)
        assertEquals(ArrivalSource.SCHEDULED, l28.source)
        assertEquals("07:30", l28.clockTime)
        assertEquals(-811520, l28.colorArgb)
        assertEquals(fetchedAt, l28.fetchedAtMs)

        assertEquals("28", findMatchingArrival(feed.arrivals, "28", null, 40, fetchedAt)?.lineId)
        assertNull(findMatchingArrival(feed.arrivals, "28", null, 10, fetchedAt))
        assertNull(findMatchingArrival(feed.arrivals, "28", "Amara-Mutualitateak", 40, fetchedAt))

        assertEquals(mapOf("28" to -811520, "21" to -5897347), feed.lineColors().associate { it.lineId to it.colorArgb })
    }

    /** Paridad con `packages/core/tests/tipo0.test.ts`: Dbus da `tipo=0` (sin GPS) desde el 2026-11-22. */
    @Test
    fun tipo0EsHorario() {
        val feed = parseStopFeed(fixture("tiemposParada-2-datos-tipo0.json"), fetchedAt)
        assertEquals(StopFeedStatus.OK, feed.status)
        assertEquals(
            listOf(
                Triple("36", 5, ArrivalSource.SCHEDULED),
                Triple("28", 8, ArrivalSource.LIVE),
                Triple("46", 10, ArrivalSource.SCHEDULED),
                Triple("28", 16, ArrivalSource.LIVE),
                Triple("21", 17, ArrivalSource.LIVE),
            ),
            feed.arrivals.map { Triple(it.lineId, it.minutesAtFetch, it.source) },
        )
    }

    @Test
    fun datosConGps() {
        val feed = parseStopFeed(fixture("tiemposParada-50-datos.json"), fetchedAt)
        assertEquals(StopFeedStatus.OK, feed.status)
        assertEquals("Tolosa 138", feed.stopName)
        assertEquals(listOf("45", "25", "27"), feed.arrivals.map { it.lineId })
        assertEquals(listOf(41, 45, 73), feed.arrivals.map { it.minutesAtFetch })
        assertTrue(feed.arrivals.all { it.source == ArrivalSource.LIVE })
    }

    @Test
    fun datosConAvisosMuestraElAviso() {
        val feed = parseStopFeed(fixture("tiemposParada-340-datos-avisos.json"), fetchedAt)
        assertEquals(StopFeedStatus.OK, feed.status)
        assertEquals("Pío Baroja 10", feed.stopName)
        assertEquals("Traslados: L16 sin servicio   L24 Errotaburu", feed.notice)
        assertEquals("35", feed.arrivals.single().lineId)
    }

    @Test
    fun sinServicioConMotivo() {
        val feed = parseStopFeed(fixture("tiemposParada-1-sin-servicio.json"), fetchedAt)
        assertEquals(StopFeedStatus.NO_SERVICE, feed.status)
        assertEquals("Parada suprimida - traslados: L28 Pte. Sta. Catalina   L21 sin servicio.", feed.notice)
        assertTrue(feed.arrivals.isEmpty())
    }

    @Test
    fun sinServicioSinMotivo() {
        val feed = parseStopFeed(fixture("tiemposParada-566-sin-servicio.json"), fetchedAt)
        assertEquals(StopFeedStatus.NO_SERVICE, feed.status)
        assertNull(feed.notice)
    }

    @Test
    fun noDescEsErrorDeApi() {
        val feed = parseStopFeed(fixture("tiemposParada-99999-no-desc.json"), fetchedAt)
        assertEquals(StopFeedStatus.API_ERROR, feed.status)
        assertTrue(feed.arrivals.isEmpty())
    }

    /**
     * No se ha observado aún una respuesta real `DATOS` sin tiempos (barrido del
     * 2026-11-22: las paradas sin buses dan `SIN_SERVICIO`). Se deriva de un fixture
     * real vaciando `tiempos`; no se guarda como fixture.
     */
    @Test
    fun datosSinTiemposEsVacio() {
        val body = fixture("tiemposParada-50-datos.json").replace(Regex("\"tiempos\":\\[.*]"), "\"tiempos\":[]")
        assertEquals(StopFeedStatus.EMPTY, parseStopFeed(body, fetchedAt).status)
        val nullTiempos = fixture("tiemposParada-50-datos.json").replace(Regex("\"tiempos\":\\[.*]"), "\"tiempos\":null")
        assertEquals(StopFeedStatus.EMPTY, parseStopFeed(nullTiempos, fetchedAt).status)
    }

    @Test
    fun jsonInvalidoOEstadoDesconocidoEsErrorDeApi() {
        assertEquals(StopFeedStatus.API_ERROR, parseStopFeed("<html>502</html>", fetchedAt).status)
        assertEquals(StopFeedStatus.API_ERROR, parseStopFeed("[]", fetchedAt).status)
        assertEquals(StopFeedStatus.API_ERROR, parseStopFeed("{\"estado\":\"OTRO\"}", fetchedAt).status)
        assertEquals(StopFeedStatus.API_ERROR, parseStopFeed("{}", fetchedAt).status)
    }
}
