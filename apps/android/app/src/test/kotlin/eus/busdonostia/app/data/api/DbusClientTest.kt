package eus.busdonostia.app.data.api

import eus.busdonostia.app.TestData
import eus.busdonostia.app.data.config.ApiConfig
import eus.busdonostia.app.domain.StopFeedStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/** El cliente arma la URL desde ApiConfig y traduce red/HTTP/JSON a StopFeed. */
class DbusClientTest {
    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()

    @After
    fun tearDown() = server.shutdown()

    /** La config empaquetada apuntando al servidor local; mismos paths, params y cabeceras. */
    private fun config(headers: Map<String, String> = emptyMap()) =
        ApiConfig.parse(TestData.bundledConfigJson)!!.copy(
            apiBaseUrl = server.url("/SSIIMovilWSv2/ws/cons/").toString(),
            headers = headers,
        )

    /** Avisos que el cliente manda a logcat (en JVM no hay `android.util.Log`). */
    private val warnings = mutableListOf<String>()

    private fun client(cfg: ApiConfig = config()) = DbusClient(http, { cfg }, nowMs = { 42L }, warn = { msg, _ -> warnings += msg })

    @Test
    fun llamaConLosParametrosDeLaConfig() = runTest {
        server.enqueue(MockResponse().setBody(TestData.fixture("tiemposParada-50-datos.json")))
        val feed = client(config(mapOf("User-Agent" to "test-ua"))).tiemposParada("50", "eu")
        assertEquals(StopFeedStatus.OK, feed.status)
        assertEquals(42L, feed.fetchedAtMs)
        val request = server.takeRequest()
        assertEquals("/SSIIMovilWSv2/ws/cons/tiemposParada?codParada=50&idioma=eu", request.path)
        assertEquals("test-ua", request.getHeader("User-Agent"))
    }

    @Test
    fun parametrosRenombradosPorConfigRemota() = runTest {
        server.enqueue(MockResponse().setBody(TestData.fixture("tiemposParada-1-sin-servicio.json")))
        val cfg = config().copy(params = ApiConfig.Params(stopCode = "stop", language = "lang"))
        assertEquals(StopFeedStatus.NO_SERVICE, client(cfg).tiemposParada("1", "es").status)
        assertEquals("/SSIIMovilWSv2/ws/cons/tiemposParada?stop=1&lang=es", server.takeRequest().path)
    }

    @Test
    fun http500EsErrorDeApi() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        assertEquals(StopFeedStatus.API_ERROR, client().tiemposParada("50", "es").status)
    }

    @Test
    fun cuerpoNoJsonEsErrorDeApi() = runTest {
        server.enqueue(MockResponse().setBody("<html>mantenimiento</html>"))
        assertEquals(StopFeedStatus.API_ERROR, client().tiemposParada("50", "es").status)
    }

    @Test
    fun sinRedEsErrorDeApi() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(StopFeedStatus.API_ERROR, client().tiemposParada("50", "es").status)
        assertEquals(1, warnings.size) // el fallo de red no es mudo (H5)
    }
}
