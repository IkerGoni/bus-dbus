package eus.busdonostia.app.domain

import eus.busdonostia.app.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** remainingMinutes, computeFreshness, haversineMeters, identidad de línea y matching. */
class DomainTest {
    private fun arrival(
        lineId: String,
        minutes: Int,
        source: ArrivalSource = ArrivalSource.LIVE,
        destination: String = "Ospitaleak",
        internal: String = "",
        fetchedAtMs: Long = 0,
    ) = Arrival(lineId, internal, destination, minutes, null, source, fetchedAtMs, null)

    // ── remainingMinutes: minutos enteros, nunca negativos ──

    @Test
    fun remainingMinutesBajaUnMinutoCada60s() {
        val a = arrival("28", 3, fetchedAtMs = 1_000_000)
        assertEquals(3, remainingMinutes(a, 1_000_000))
        assertEquals(3, remainingMinutes(a, 1_059_999))
        assertEquals(2, remainingMinutes(a, 1_060_000))
        assertEquals(0, remainingMinutes(a, 1_000_000 + 10 * 60_000))
        assertEquals(3, remainingMinutes(a, 999_000)) // reloj por detrás del fetch: no sube
    }

    // ── computeFreshness: FRESH ≤ 45 s, STALE ≤ 90 s (tabla §5, «FRESH < 30 s») ──

    @Test
    fun freshnessUmbrales() {
        assertEquals(FreshnessLevel.FRESH, computeFreshness(0, 45_999))
        assertEquals(FreshnessLevel.STALE, computeFreshness(0, 46_000))
        assertEquals(FreshnessLevel.STALE, computeFreshness(0, 90_999))
        assertEquals(FreshnessLevel.EXPIRED, computeFreshness(0, 91_000))
        assertEquals(FreshnessLevel.FRESH, computeFreshness(10_000, 0)) // futuro → edad 0
    }

    // ── haversineMeters: misma fórmula que apps/bot/src/nearby.ts ──

    @Test
    fun haversineEntreParadasReales() {
        val c = TestData.catalog
        val boulevard = c.stopByCode("1")!!
        val pioBaroja = c.stopByCode("340")!!
        assertEquals(0L, haversineMeters(boulevard.lat!!, boulevard.lon!!, boulevard.lat!!, boulevard.lon!!))
        // Valor de referencia calculado con haversineMeters del bot (Math.round) sobre las mismas coordenadas.
        assertEquals(
            1_626L,
            haversineMeters(boulevard.lat!!, boulevard.lon!!, pioBaroja.lat!!, pioBaroja.lon!!),
        )
        // 1° de latitud ≈ 111,2 km con R = 6 371 km.
        assertEquals(111_195L, haversineMeters(43.0, -2.0, 44.0, -2.0))
    }

    // ── Identidad de línea (regla 5): 32 → 28 en contexto de parada; gana la pública ──

    @Test
    fun normalizeLineComoCore() {
        assertEquals("28", normalizeLine(" l028 "))
        assertEquals("B9", normalizeLine("b09"))
        assertEquals("B4", normalizeLine("B4"))
    }

    @Test
    fun identidadDeLinea() {
        val c = TestData.catalog
        assertEquals("28", c.lineByCode("28")!!.cod)
        assertEquals("32", c.lineByCode("32")!!.cod) // 32 es pública: gana a la interna 32 (= 28)
        // El id interno 28 es la línea pública 26: en la parada 8 (pasa la 26, no la 28) 28 → 26.
        assertEquals("26", c.toPublicLineId("28", stopId = "8"))
        assertEquals("28", c.toPublicLineId("28"))
        // En la parada 1 (21, 28, B4, B9) no pasa la 32 pública: 32 es el alias interno de la 28.
        assertEquals("28", c.toPublicLineId("32", stopId = "1"))
        assertEquals("32", c.toPublicLineId("32"))
        assertEquals("B4", c.toPublicLineId("b04", stopId = "1"))
        assertEquals("B4", c.lineByCode("B4")!!.cod)
        assertTrue(isNightLine("B4"))
        assertFalse(isNightLine("28"))
    }

    // ── Matching (§2): prefiere LIVE, filtra destino, código público ──

    @Test
    fun matchingPrefiereGps() {
        val arrivals = listOf(
            arrival("28", 2, ArrivalSource.SCHEDULED),
            arrival("28", 4, ArrivalSource.LIVE),
        )
        assertEquals(4, findMatchingArrival(arrivals, "28", null, 5, 0)!!.minutesAtFetch)
        // Si no hay GPS dentro de la ventana, vale el horario.
        assertEquals(2, findMatchingArrival(arrivals, "28", null, 3, 0)!!.minutesAtFetch)
    }

    @Test
    fun matchingFiltraDestinoYLinea() {
        val arrivals = listOf(
            arrival("28", 3, destination = "Amara-Mutualitateak"),
            arrival("28", 6, destination = "Ospitaleak"),
            arrival("21", 1),
        )
        assertEquals(6, findMatchingArrival(arrivals, "28", "ospitaleak ", 10, 0)!!.minutesAtFetch)
        assertEquals(3, findMatchingArrival(arrivals, "28", UNKNOWN_DESTINATION, 10, 0)!!.minutesAtFetch)
        assertNull(findMatchingArrival(arrivals, "28", "Ospitaleak", 5, 0))
    }

    @Test
    fun matchingNoConfundeAliasInternoConLineaPublica() {
        // Llegada real de la 28 (id interno 32): un aviso de la línea pública 32 no debe casar.
        val arrivals = listOf(arrival("28", 2, internal = "32"))
        assertNull(findMatchingArrival(arrivals, "32", null, 10, 0))
        assertEquals("28", findMatchingArrival(arrivals, "L028", null, 10, 0)!!.lineId)
    }
}
