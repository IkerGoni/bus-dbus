package eus.busdonostia.app.domain

import eus.busdonostia.app.TestData
import eus.busdonostia.app.data.api.parseStopFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** StopFeed + frescura → las 5 vistas de A05, con respuestas reales de Dbus. */
class StopViewTest {
    private val t0 = 1_700_000_000_000L
    private fun feed(name: String, at: Long = t0) = parseStopFeed(TestData.fixture(name), at)
    private val error = StopFeed(StopFeedStatus.API_ERROR, emptyList(), null, t0)

    @Test
    fun sinRespuestaEsLoading() {
        assertEquals(ArrivalsView.Loading, stopArrivalsView(null, null, t0))
    }

    @Test
    fun datosFrescosSonLiveConSuAviso() {
        val ok = feed("tiemposParada-340-datos-avisos.json")
        val view = stopArrivalsView(ok, ok, t0 + 45_000) as ArrivalsView.Live
        assertTrue(view.arrivals.isNotEmpty())
        assertEquals(ok.notice, view.notice)
        assertTrue(view.notice!!.isNotBlank()) // `notice` visible también con datos
    }

    @Test
    fun sinServicioEsDegradedConMotivoReal() {
        val noService = feed("tiemposParada-1-sin-servicio.json")
        val view = stopArrivalsView(noService, noService, t0) as ArrivalsView.Degraded
        assertEquals(DegradedReason.NO_SERVICE, view.reason)
        assertEquals(noService.notice, view.notice)
        assertTrue(view.arrivals.isEmpty())
    }

    @Test
    fun fallaElRefrescoDatosAnterioresSegunFrescura() {
        val ok = feed("tiemposParada-50-datos.json")
        // ≤ 45 s: siguen siendo live aunque el último intento fallara.
        assertTrue(stopArrivalsView(error, ok, t0 + 45_000) is ArrivalsView.Live)
        // 46–90 s: degraded STALE con las llegadas viejas y la hora real del dato.
        val stale = stopArrivalsView(error, ok, t0 + 60_000) as ArrivalsView.Degraded
        assertEquals(DegradedReason.STALE, stale.reason)
        assertEquals(ok.arrivals, stale.arrivals)
        assertEquals(t0, stale.fetchedAtMs)
        // > 90 s: error, no se enseñan datos caducados.
        assertEquals(ArrivalsView.Error, stopArrivalsView(error, ok, t0 + 91_000))
    }

    @Test
    fun datoValidoQueEnvejeceNoSeDaPorVivo() {
        val ok = feed("tiemposParada-50-datos.json")
        val view = stopArrivalsView(ok, ok, t0 + 120_000) as ArrivalsView.Degraded
        assertEquals(DegradedReason.STALE, view.reason)
    }

    @Test
    fun errorSinDatosPreviosYNoDesc() {
        assertEquals(ArrivalsView.Error, stopArrivalsView(error, null, t0))
        val noDesc = feed("tiemposParada-99999-no-desc.json")
        assertEquals(StopFeedStatus.API_ERROR, noDesc.status)
        assertEquals(ArrivalsView.Error, stopArrivalsView(noDesc, null, t0))
    }

    @Test
    fun datosSinTiemposEsEmpty() {
        val empty = StopFeed(StopFeedStatus.EMPTY, emptyList(), null, t0)
        assertEquals(ArrivalsView.Empty(null), stopArrivalsView(empty, empty, t0))
    }

    @Test
    fun chipsDeLineasDeLaParadaYFiltro() {
        val ok = feed("tiemposParada-340-datos-avisos.json")
        val ids = stopLineIds(TestData.catalog, "340", ok.arrivals)
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.containsAll(TestData.catalog.stopByCode("340")!!.lines))
        assertTrue(ok.arrivals.all { it.lineId in ids })
        val line = ok.arrivals.first().lineId
        assertTrue(filterArrivals(ok.arrivals, line).all { it.lineId == line })
        assertEquals(ok.arrivals, filterArrivals(ok.arrivals, null))
        // En la parada 1 el alias interno 32 es la línea pública 28.
        assertTrue("28" in stopLineIds(TestData.catalog, "1", emptyList()))
    }

    @Test
    fun filasDeA05FiltradasConMinutosYClaveUnica() {
        val ok = feed("tiemposParada-340-datos-avisos.json")
        val line = ok.arrivals.first().lineId
        val rows = arrivalRows(ok.arrivals, line, t0 + 60_000)
        assertEquals(filterArrivals(ok.arrivals, line), rows.map { it.arrival }) // H11: el filtro sale del composable
        assertEquals(rows.map { remainingMinutes(it.arrival, t0 + 60_000) }, rows.map { it.minutes })
        // Dbus puede repetir línea, destino y minutos: la clave no se repite (items(key) no revienta).
        val twin = ok.arrivals.first()
        val keys = arrivalRows(listOf(twin, twin, twin.copy(destination = "Otro")), null, t0).map { it.key }
        assertEquals(keys.distinct(), keys)
    }

    @Test
    fun filasDeA05IgualesMientrasNoCambiaUnMinuto() {
        // H10: el reloj late cada segundo, pero el estado solo cambia (y la UI solo se recompone)
        // cuando cambia algún minuto mostrado.
        val ok = feed("tiemposParada-340-datos-avisos.json")
        assertEquals(arrivalRows(ok.arrivals, null, t0 + 1_000), arrivalRows(ok.arrivals, null, t0 + 59_000))
        assertTrue(arrivalRows(ok.arrivals, null, t0 + 59_000) != arrivalRows(ok.arrivals, null, t0 + 60_000))
    }

    // ── A5-12: el resalte de un deep link solo vale si es una línea real de la parada ──

    @Test
    fun resalteDeDeepLinkSoloLineasReales() {
        val lines = listOf("13", "28", "B4")
        assertEquals("13", sanitizeHighlightLine(lines, "13"))
        assertEquals("28", sanitizeHighlightLine(lines, " l028 "))
        assertNull(sanitizeHighlightLine(lines, "13';--"))
        assertNull(sanitizeHighlightLine(lines, "99"))
        assertNull(sanitizeHighlightLine(lines, null))
        assertNull(sanitizeHighlightLine(emptyList(), "13"))
    }
}
