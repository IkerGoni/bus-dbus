package eus.busdonostia.app.data.live

import eus.busdonostia.app.TestData
import eus.busdonostia.app.data.api.DbusClient
import eus.busdonostia.app.data.config.ApiConfig
import eus.busdonostia.app.data.db.LineColorEntity
import eus.busdonostia.app.domain.StopFeedStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Puerta única a `tiemposParada` (H9): registra feeds, hora de la última respuesta real y colores. */
class LiveRepositoryTest {
    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() = server.shutdown()

    private var now = 1_700_000_000_000L
    private val liveFetches = mutableListOf<Long>()
    private val colors = mutableListOf<LineColorEntity>()

    private fun repo(scope: kotlinx.coroutines.CoroutineScope): LiveRepository {
        val cfg = ApiConfig.parse(TestData.bundledConfigJson)!!.copy(apiBaseUrl = server.url("/ws/").toString())
        return LiveRepository(
            client = DbusClient(OkHttpClient(), { cfg }, nowMs = { now }, warn = { _, _ -> }),
            language = { "es" },
            pollIntervalMs = { 30_000L },
            onLiveFetch = { liveFetches += it },
            onLineColors = { colors += it },
            scope = scope,
            nowMs = { now },
        )
    }

    @Test
    fun respuestaValidaQuedaRegistradaConHoraYColores() = runTest {
        server.enqueue(MockResponse().setBody(TestData.fixture("tiemposParada-1.json")))
        val live = repo(backgroundScope)
        val feed = live.fetchStop("1")
        assertEquals(StopFeedStatus.OK, feed.status)
        assertEquals(feed, live.feeds.value["1"]?.latest)
        assertEquals(listOf(now), liveFetches)
        testScheduler.advanceUntilIdle()
        assertTrue(colors.isNotEmpty())
    }

    @Test
    fun errorDeApiNoCuentaComoRespuestaRealYConservaLaValida() = runTest {
        server.enqueue(MockResponse().setBody(TestData.fixture("tiemposParada-1.json")))
        server.enqueue(MockResponse().setResponseCode(500))
        val live = repo(backgroundScope)
        val ok = live.fetchStop("1")
        now += 40_000
        assertEquals(StopFeedStatus.API_ERROR, live.fetchStop("1").status)
        assertEquals(ok, live.feeds.value["1"]?.lastValid)
        assertEquals(1, liveFetches.size)
    }

    @Test
    fun elSondeoReutilizaLoRecienteYPideLoViejo() = runTest {
        server.enqueue(MockResponse().setBody(TestData.fixture("tiemposParada-1.json")))
        server.enqueue(MockResponse().setBody(TestData.fixture("tiemposParada-1.json")))
        val live = repo(backgroundScope)
        val first = live.fetchStop("1") // p. ej. el motor
        now += 10_000
        assertEquals(first, live.pollStop("1"))
        assertEquals(1, server.requestCount)
        now += 15_000
        live.pollStop("1")
        assertEquals(2, server.requestCount)
        assertNull(live.feeds.value["2"])
    }
}
