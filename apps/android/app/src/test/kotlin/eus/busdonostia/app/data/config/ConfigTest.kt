package eus.busdonostia.app.data.config

import eus.busdonostia.app.TestData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() = server.shutdown()

    private val bundled = ApiConfig.parse(TestData.bundledConfigJson)!!

    /** La empaquetada con un campo cambiado, como JSON. */
    private fun variant(key: String, value: String): String {
        val root = Json.parseToJsonElement(TestData.bundledConfigJson).jsonObject
        return JsonObject(root + (key to JsonPrimitive(value))).toString()
    }

    private fun variantInt(key: String, value: Int): String {
        val root = Json.parseToJsonElement(TestData.bundledConfigJson).jsonObject
        return JsonObject(root + (key to JsonPrimitive(value))).toString()
    }

    private var now = 1_700_000_000_000L
    private val cacheFile get() = tmp.root.resolve("api-config.json")

    /** Avisos que el repositorio manda a logcat (en JVM no hay `android.util.Log`). */
    private val warnings = mutableListOf<String>()

    private fun repo(http: OkHttpClient = OkHttpClient()) =
        ConfigRepository(bundled, cacheFile, server.url("/config.json"), http, nowMs = { now }, warn = { msg, _ -> warnings += msg })

    // ── Validación ──

    @Test
    fun laEmpaquetadaEsValida() {
        assertEquals(emptyList<String>(), bundled.validate())
        assertEquals("http://app.dbus.eus/SSIIMovilWSv2/ws/cons", bundled.apiBaseUrl)
        assertEquals("/tiemposParada", bundled.endpoints.tiemposParada)
        assertEquals(30, bundled.pollIntervalSec)
    }

    @Test
    fun apiBaseUrlHttpFueraDeDbusSeRechaza() {
        assertNull(ApiConfig.parse(variant("apiBaseUrl", "http://evil.example/ws")))
        assertNull(ApiConfig.parse(variant("apiBaseUrl", "http://otro.dbus.eus/ws")))
        assertNotNull(ApiConfig.parse(variant("apiBaseUrl", "https://app.dbus.eus/ws")))
        assertNull(ApiConfig.parse(variant("apiBaseUrl", "no es una url")))
    }

    @Test
    fun apiBaseUrlSoloHaciaHostsPermitidos() {
        // HTTPS no basta (H21): quien controle el hosting de la config no puede redirigir las consultas.
        assertNull(ApiConfig.parse(variant("apiBaseUrl", "https://evil.example/ws")))
        assertNull(ApiConfig.parse(variant("apiBaseUrl", "https://nuevo.dbus.eus/ws")))
        assertNull(ApiConfig.parse(variant("apiBaseUrl", "https://app.dbus.eus.evil.example/ws")))
        assertNotNull(ApiConfig.parse(variant("apiBaseUrl", "http://APP.dbus.eus/SSIIMovilWSv3/ws/cons")))
        // La lista es parámetro de validate(): por defecto solo app.dbus.eus.
        val otherHost = bundled.copy(apiBaseUrl = "https://nuevo.dbus.eus/ws")
        assertEquals(emptyList<String>(), otherHost.validate(allowedHosts = setOf("app.dbus.eus", "nuevo.dbus.eus")))
    }

    @Test
    fun cabecerasSoloDeLaListaBlanca() {
        assertNotNull(bundled.copy(headers = mapOf("User-Agent" to "x", "accept-language" to "eu")).takeIf { it.validate().isEmpty() })
        for (name in listOf("Authorization", "Cookie", "Host", "Proxy-Authorization", "X-Forwarded-For", "X-Api-Key")) {
            assertTrue(name, bundled.copy(headers = mapOf(name to "x")).validate().isNotEmpty())
        }
        // Un valor con salto de línea no puede colar otra cabecera.
        assertTrue(bundled.copy(headers = mapOf("User-Agent" to "x\r\nCookie: y")).validate().isNotEmpty())
    }

    @Test
    fun rangosYEsquema() {
        assertNull(ApiConfig.parse(variantInt("pollIntervalSec", 19)))
        assertNotNull(ApiConfig.parse(variantInt("pollIntervalSec", 20)))
        assertNull(ApiConfig.parse(variantInt("schemaVersion", 2)))
        assertNull(ApiConfig.parse("{\"schemaVersion\":1}"))
        assertNull(ApiConfig.parse("no json"))
    }

    @Test
    fun laUrlRemotaSoloPuedeSerHttps() {
        assertNull(ConfigRepository.remoteConfigUrl(""))
        assertNull(ConfigRepository.remoteConfigUrl("http://example.org/config.json"))
        assertNotNull(ConfigRepository.remoteConfigUrl("https://example.org/config.json"))
    }

    // ── Carga remota opcional ──

    @Test
    fun sinUrlRemotaUsaLaEmpaquetadaYNoLlama() = runTest {
        val r = ConfigRepository(bundled, cacheFile, null, OkHttpClient(), nowMs = { now })
        assertFalse(r.refreshIfDue())
        assertEquals(bundled, r.config.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun descargaValidaSeAplicaYPersiste() = runTest {
        server.enqueue(MockResponse().setBody(variantInt("pollIntervalSec", 45)))
        val r = repo()
        assertEquals(bundled, r.config.value) // arranca con la empaquetada, sin esperar
        assertTrue(r.refreshIfDue())
        assertEquals(45, r.config.value.pollIntervalSec)
        // Un arranque posterior parte de la última válida, sin red.
        assertEquals(45, repo().config.value.pollIntervalSec)
    }

    @Test
    fun configRotaSeIgnora() = runTest {
        server.enqueue(MockResponse().setBody("{\"schemaVersion\":1,\"apiBaseUrl\":"))
        val r = repo()
        assertFalse(r.refreshIfDue())
        assertEquals(bundled, r.config.value)
    }

    @Test
    fun apiBaseUrlHttpRemotoSeRechazaYSeQuedaLaAnterior() = runTest {
        server.enqueue(MockResponse().setBody(variantInt("pollIntervalSec", 45)))
        val r = repo()
        assertTrue(r.refreshIfDue())
        now += ConfigRepository.REFRESH_INTERVAL_MS
        server.enqueue(MockResponse().setBody(variant("apiBaseUrl", "http://evil.example/ws")))
        assertFalse(r.refreshIfDue())
        assertEquals(45, r.config.value.pollIntervalSec) // última válida, no la empaquetada
        assertEquals(bundled.apiBaseUrl, r.config.value.apiBaseUrl)
    }

    @Test
    fun timeoutDe5sNoBloqueaYSeQuedaLaEmpaquetada() = runTest {
        server.enqueue(MockResponse().setBody(variantInt("pollIntervalSec", 45)).setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val r = repo()
        val started = System.nanoTime()
        assertFalse(r.refreshIfDue())
        val elapsedSec = (System.nanoTime() - started) / 1e9
        assertTrue("timeout tardó $elapsedSec s", elapsedSec in 4.5..8.0)
        assertEquals(bundled, r.config.value)
        assertEquals(1, warnings.size) // el timeout no es mudo (H5)
    }

    @Test
    fun comoMuchoCada12hContandoIntentosFallidos() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val r = repo()
        assertFalse(r.refreshIfDue())
        now += ConfigRepository.REFRESH_INTERVAL_MS - 1
        assertFalse(r.refreshIfDue()) // no vuelve a pedir antes de 12 h
        assertEquals(1, server.requestCount)
        now += 1
        server.enqueue(MockResponse().setBody(variantInt("pollIntervalSec", 60)))
        assertTrue(r.refreshIfDue())
        assertEquals(2, server.requestCount)
        assertEquals(60, r.config.value.pollIntervalSec)
    }

    @Test
    fun cacheCorruptaEnDiscoSeIgnora() {
        cacheFile.writeText("basura")
        assertEquals(bundled, repo().config.value)
        assertEquals(1, warnings.size) // corrupta: se avisa (H5); ausente es lo normal y no
    }
}
