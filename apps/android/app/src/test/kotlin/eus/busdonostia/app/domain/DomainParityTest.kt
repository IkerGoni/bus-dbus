package eus.busdonostia.app.domain

import eus.busdonostia.app.TestData
import eus.busdonostia.app.data.api.parseStopFeed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paridad con TypeScript (ADR 009 §4, H6): los casos de
 * `packages/contracts/fixtures/domain-cases.json` que también ejecutan
 * `packages/core/tests/parity.test.ts` y `apps/bot/tests/haversine-parity.test.ts`.
 */
class DomainParityTest {
    private val cases = Json.parseToJsonElement(TestData.fixture("domain-cases.json")).jsonObject

    private fun section(name: String) = cases.getValue(name).jsonArray.map { it.jsonObject }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.contentOrNull

    @Test
    fun androidEjecutaTodasLasSecciones() {
        cases.getValue("runs").jsonObject.forEach { (name, sides) ->
            assertTrue(name, sides.jsonArray.any { it.jsonPrimitive.content == "android" })
        }
    }

    @Test
    fun parseoDeLosSieteFixturesReales() {
        val t0 = 1_700_000_000_000L
        val parse = section("tiemposParada")
        assertEquals(7, parse.size)
        for (case in parse) {
            val file = case.str("file")!!
            val feed = parseStopFeed(TestData.fixture(file), t0)
            val expected = case.getValue("arrivals").jsonArray.map { it.jsonObject }
            val status = when (case.str("estado")) {
                "DATOS" -> if (expected.isEmpty()) StopFeedStatus.EMPTY else StopFeedStatus.OK
                "SIN_SERVICIO" -> StopFeedStatus.NO_SERVICE
                else -> StopFeedStatus.API_ERROR // `NO DESC` o desconocido
            }
            assertEquals(file, status, feed.status)
            assertEquals(
                file,
                expected.map { listOf(it.str("lineId"), it.getValue("minutes").jsonPrimitive.int, it.getValue("realtime").jsonPrimitive.boolean, it.str("destination")) },
                feed.arrivals.map { listOf(it.lineId, it.minutesAtFetch, it.source == ArrivalSource.LIVE, it.destination) },
            )
        }
    }

    @Test
    fun matchingConDestinoYPreferenciaGps() {
        val now = 1_700_000_000_000L
        for (case in section("matching")) {
            val arrivals = case.getValue("arrivals").jsonArray.map { it.jsonObject }.map {
                val line = it.str("lineId")!!
                val source = if (it.getValue("realtime").jsonPrimitive.boolean) ArrivalSource.LIVE else ArrivalSource.SCHEDULED
                Arrival(line, line, it.str("destination")!!, it.getValue("minutes").jsonPrimitive.int, null, source, now, null)
            }
            val found = findMatchingArrival(arrivals, case.str("lineId")!!, case.str("destination"), case.getValue("minutesAhead").jsonPrimitive.int, now)
            val index = found?.let { f -> arrivals.indexOfFirst { it === f } }
            assertEquals(case.str("name"), case.getValue("expectedIndex").jsonPrimitive.intOrNull, index)
        }
    }

    @Test
    fun haversineRedondeadoAlMetro() {
        for (case in section("haversineMeters")) {
            val (lat1, lon1) = case.getValue("from").jsonArray.map { it.jsonPrimitive.double }
            val (lat2, lon2) = case.getValue("to").jsonArray.map { it.jsonPrimitive.double }
            assertEquals(case.str("name"), case.getValue("expectedMeters").jsonPrimitive.long, haversineMeters(lat1, lon1, lat2, lon2))
        }
    }

    @Test
    fun minutosRestantesSinSegundos() {
        val fetchedAt = 1_700_000_000_000L
        for (case in section("remainingMinutes")) {
            val arrival = Arrival("5", "5", "Amara", case.getValue("minutesAtFetch").jsonPrimitive.int, null, ArrivalSource.LIVE, fetchedAt, null)
            val now = fetchedAt + case.getValue("elapsedMs").jsonPrimitive.long
            assertEquals(case.str("name"), case.getValue("expected").jsonPrimitive.int, remainingMinutes(arrival, now))
        }
    }

    @Test
    fun frescura() {
        val fetchedAt = 1_700_000_000_000L
        for (case in section("freshness")) {
            val age = case.getValue("ageMs").jsonPrimitive.long
            assertEquals("ageMs=$age", FreshnessLevel.valueOf(case.str("expected")!!), computeFreshness(fetchedAt, fetchedAt + age))
        }
    }
}
