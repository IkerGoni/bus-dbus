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
        ConfigRepository(bundled, { cacheFile }, server.url("/config.json"), http, nowMs = { now }, warn = { msg, _ -> warnings += msg })

    // ── Validación ──

    @Test
    fun laEmpaquetadaEsValida() {
        assertEquals(emptyList<String>(), bundled.validate())
        assertEquals(2, bundled.schemaVersion)
        assertEquals("http://app.dbus.eus/SSIIMovilWSv2/ws/cons", bundled.apiBaseUrl)
        assertEquals("/tiemposParada", bundled.endpoints.tiemposParada)
        assertEquals(30, bundled.pollIntervalSec)
        assertEquals(30, bundled.polling.uiTickerSec)
        assertEquals(30, bundled.polling.foregroundVisibleSec)
        assertEquals(30, bundled.polling.baseBackgroundSec)
        assertEquals(2, bundled.polling.noServiceOrErrorMultiplier)
        assertEquals(4, bundled.polling.adaptiveTiers.size)
        assertEquals(45, bundled.freshness.freshMaxSec)
        assertEquals(90, bundled.freshness.staleMaxSec)
        assertEquals(3, bundled.freshness.liveFeedHistorySize)
        assertEquals(0, bundled.alerts.triggerBufferSec)
        assertEquals(7, bundled.alerts.expirationDays)
        assertEquals(1, bundled.alerts.minMinutesAhead)
        assertEquals(30, bundled.alerts.maxMinutesAhead)
        assertEquals(9, bundled.alerts.maxBadgeCount)
        assertEquals(3, bundled.alerts.maxWatchHours)
        assertEquals(60, bundled.alerts.alarmTimeoutSec)
        assertEquals(60, bundled.polling.noServiceStopMin)
        assertEquals(5, bundled.polling.fetchSlackSec)
        assertEquals(120, bundled.geo.lastKnownMaxAgeSec)
        assertEquals(10, bundled.geo.locationTimeoutSec)
        assertEquals(10, bundled.uiLimits.recentStopsLimit)
        assertEquals(4.8, bundled.geo.walkingSpeedKmh, 0.001)
        assertEquals(3, bundled.uiLimits.homeFavoriteMaxArrivals)
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
        assertNull(ApiConfig.parse(variantInt("schemaVersion", 3)))
        assertNull(ApiConfig.parse("{\"schemaVersion\":2}"))
        assertNull(ApiConfig.parse("no json"))
        assertTrue(bundled.copy(freshness = bundled.freshness.copy(freshMaxSec = 90, staleMaxSec = 90)).validate().isNotEmpty())
        assertTrue(bundled.copy(freshness = bundled.freshness.copy(liveFeedHistorySize = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(alerts = bundled.alerts.copy(minMinutesAhead = 35, maxMinutesAhead = 30)).validate().isNotEmpty())
        assertTrue(bundled.copy(geo = bundled.geo.copy(walkingSpeedKmh = 0.0)).validate().isNotEmpty())
        assertTrue(bundled.copy(uiLimits = bundled.uiLimits.copy(homeFavoriteMaxArrivals = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(polling = bundled.polling.copy(noServiceStopMin = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(polling = bundled.polling.copy(fetchSlackSec = 31)).validate().isNotEmpty())
        assertTrue(bundled.copy(alerts = bundled.alerts.copy(maxWatchHours = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(alerts = bundled.alerts.copy(maxWatchHours = 25)).validate().isNotEmpty())
        assertTrue(bundled.copy(alerts = bundled.alerts.copy(alarmTimeoutSec = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(alerts = bundled.alerts.copy(alarmTimeoutSec = 301)).validate().isNotEmpty())
        assertTrue(bundled.copy(alerts = bundled.alerts.copy(alarmTimeoutSec = 10)).validate().isEmpty())
        assertTrue(bundled.copy(alerts = bundled.alerts.copy(alarmTimeoutSec = 300)).validate().isEmpty())
        assertTrue(bundled.copy(geo = bundled.geo.copy(lastKnownMaxAgeSec = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(geo = bundled.geo.copy(locationTimeoutSec = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(uiLimits = bundled.uiLimits.copy(recentStopsLimit = 0)).validate().isNotEmpty())
    }

    @Test
    fun escalonesAdaptativosEstrictamenteDescendentesYEnRango() {
        val tiers = bundled.polling.adaptiveTiers
        assertTrue(bundled.copy(polling = bundled.polling.copy(adaptiveTiers = tiers.reversed())).validate().isNotEmpty())
        assertTrue(bundled.copy(polling = bundled.polling.copy(adaptiveTiers = tiers + tiers.last())).validate().isNotEmpty())
        assertTrue(bundled.copy(polling = bundled.polling.copy(adaptiveTiers = emptyList())).validate().isNotEmpty())
        val tooFast = tiers.map { it.copy(delaySec = 5) }
        assertTrue(bundled.copy(polling = bundled.polling.copy(adaptiveTiers = tooFast)).validate().isNotEmpty())
    }

    @Test
    fun limitesDeUiEnRango() {
        assertTrue(bundled.copy(uiLimits = bundled.uiLimits.copy(homeSearchLimit = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(uiLimits = bundled.uiLimits.copy(searchStopsLimit = 0)).validate().isNotEmpty())
        assertTrue(bundled.copy(uiLimits = bundled.uiLimits.copy(nearbyStopsLimit = 1_000)).validate().isNotEmpty())
    }

    @Test
    fun configRemotaConRangosInvalidosSeRechazaYSeQuedaLaEmpaquetada() = runTest {
        val root = Json.parseToJsonElement(TestData.bundledConfigJson).jsonObject
        val freshness = root.getValue("freshness").jsonObject
        val inverted = JsonObject(freshness + ("freshMaxSec" to JsonPrimitive(120)) + ("staleMaxSec" to JsonPrimitive(60)))
        server.enqueue(MockResponse().setBody(JsonObject(root + ("freshness" to inverted)).toString()))
        val r = repo()
        assertFalse(r.refreshIfDue())
        assertEquals(bundled, r.config.value)
        assertEquals(bundled.freshness, r.freshnessConfig.value)
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
        val r = ConfigRepository(bundled, { cacheFile }, null, OkHttpClient(), nowMs = { now })
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
        // Un arranque posterior carga la última válida desde disco en IO, sin red.
        val nextRepo = repo()
        nextRepo.initializeFromDisk()
        assertEquals(45, nextRepo.config.value.pollIntervalSec)
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
        now += bundled.remoteConfigCacheHours * 3_600_000L
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
        now += bundled.remoteConfigCacheHours * 3_600_000L - 1
        assertFalse(r.refreshIfDue()) // no vuelve a pedir antes de 12 h
        assertEquals(1, server.requestCount)
        now += 1
        server.enqueue(MockResponse().setBody(variantInt("pollIntervalSec", 60)))
        assertTrue(r.refreshIfDue())
        assertEquals(2, server.requestCount)
        assertEquals(60, r.config.value.pollIntervalSec)
    }

    @Test
    fun cacheCorruptaEnDiscoSeIgnora() = runTest {
        cacheFile.writeText("basura")
        val r = repo()
        r.initializeFromDisk()
        assertEquals(bundled, r.config.value)
        assertEquals(1, warnings.size) // corrupta: se avisa (H5); ausente es lo normal y no
    }

    // ── A5-16: el origen de la config es visible (un fallback silencioso a defaults se nota) ──

    @Test
    fun schemaVersionEsObligatorio() {
        val root = Json.parseToJsonElement(TestData.bundledConfigJson).jsonObject
        assertNull(ApiConfig.parse(JsonObject(root.filterKeys { it != "schemaVersion" }).toString()))
    }

    @Test
    fun origenDeLaConfigVisible() = runTest {
        val r = repo()
        assertEquals(ConfigSource.DEFAULTS, r.configSource.value)
        r.initializeFromDisk()
        assertEquals(ConfigSource.BUNDLED, r.configSource.value)
        server.enqueue(MockResponse().setBody(variantInt("pollIntervalSec", 45)))
        assertTrue(r.refreshIfDue())
        assertEquals(ConfigSource.REMOTE, r.configSource.value)
        // Un arranque posterior con la caché en disco: REMOTE_CACHE, no DEFAULTS.
        val next = repo()
        next.initializeFromDisk()
        assertEquals(ConfigSource.REMOTE_CACHE, next.configSource.value)
    }

    @Test
    fun falloDeLecturaDelAssetQuedaEnDefaultsYAvisa() = runTest {
        val r = repo()
        r.initializeFromDisk { error("parseo roto (p. ej. por R8)") }
        assertEquals(ConfigSource.DEFAULTS, r.configSource.value)
        assertEquals(1, warnings.size)
        assertEquals(bundled, r.config.value)
    }
}
