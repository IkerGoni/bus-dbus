package eus.busdonostia.app.domain

import eus.busdonostia.app.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A06: validación, presets, dedup `(stopId, lineId)` y switch/posponer. */
class AlertDraftTest {
    private val base = AlertDraft(stopId = "340", stopName = "Pío Baroja 10", lineId = "5", minutesAhead = 5)

    private fun recurring(from: String?, to: String?, days: Set<Int> = DaysPreset.WEEKDAYS.days) =
        base.copy(frequency = AlertFrequency.RECURRING, daysOfWeek = days, windowFrom = from, windowTo = to)

    @Test
    fun onceValidoYFaltaParadaLineaOAntelacion() {
        assertEquals(emptyList<DraftError>(), validateDraft(base))
        assertEquals(listOf(DraftError.NO_STOP, DraftError.NO_LINE), validateDraft(AlertDraft()))
        assertEquals(listOf(DraftError.MINUTES_OUT_OF_RANGE), validateDraft(base.copy(minutesAhead = 0)))
        assertEquals(listOf(DraftError.MINUTES_OUT_OF_RANGE), validateDraft(base.copy(minutesAhead = 31)))
    }

    @Test
    fun recurrenteExigeDiasYFranjaDeMaximo3Horas() {
        assertEquals(listOf(DraftError.NO_WINDOW), validateDraft(recurring(null, "08:30")))
        assertEquals(listOf(DraftError.NO_WINDOW), validateDraft(recurring("7:45", "08:30"))) // no es HH:mm
        assertEquals(listOf(DraftError.EMPTY_WINDOW), validateDraft(recurring("08:00", "08:00")))
        assertEquals(emptyList<DraftError>(), validateDraft(recurring("07:45", "08:30")))
        assertEquals(emptyList<DraftError>(), validateDraft(recurring("07:00", "10:00"))) // 3 h justas
        assertEquals(listOf(DraftError.WINDOW_TOO_LONG), validateDraft(recurring("07:00", "10:01")))
        // Cruce de medianoche: 23:30–00:30 = 60 min; 22:00–01:30 = 210 min.
        assertEquals(emptyList<DraftError>(), validateDraft(recurring("23:30", "00:30")))
        assertEquals(listOf(DraftError.WINDOW_TOO_LONG), validateDraft(recurring("22:00", "01:30")))
        assertEquals(listOf(DraftError.NO_DAYS), validateDraft(recurring("07:45", "08:30", emptySet())))
    }

    @Test
    fun presetsDeDias() {
        assertEquals(DaysPreset.WEEKDAYS, presetOf(setOf(1, 2, 3, 4, 5)))
        assertEquals(DaysPreset.DAILY, presetOf((1..7).toSet()))
        assertEquals(DaysPreset.WEEKEND, presetOf(setOf(6, 7)))
        assertNull(presetOf(setOf(1, 3, 5)))
    }

    @Test
    fun guardarArmaElAvisoYOnceNoGuardaFranja() {
        val once = recurring("07:45", "08:30").copy(frequency = AlertFrequency.ONCE, lineId = " b4 ").toRule("id", 1_000)
        assertEquals("B4", once.lineId)
        assertEquals(1_000L, once.createdAtMs)
        assertTrue(once.active && once.daysOfWeek.isEmpty() && once.windowFrom == null && once.snoozedUntilMs == null)
        val rec = recurring("07:45", "08:30", setOf(1, 9)).toRule("id", 1_000)
        assertEquals(setOf(1), rec.daysOfWeek)
        assertEquals("07:45" to "08:30", rec.windowFrom to rec.windowTo)
        assertEquals(base.copy(daysOfWeek = DaysPreset.WEEKDAYS.days), base.toRule("x", 0).toDraft())
    }

    @Test
    fun dedupPorParadaYLinea() {
        val existing = base.toRule("old", 0)
        // Nuevo sin conflicto → insertar.
        assertEquals(SavePlan.Write(base.toRule("new", 5), insert = true), planSave(base, null, null, false, "new", 5))
        // Nuevo con conflicto → preguntar; confirmado → se escribe sobre el existente (mismo id).
        assertEquals(SavePlan.NeedsConfirmation(existing), planSave(base, null, existing, false, "new", 5))
        assertEquals(SavePlan.Write(base.toRule("old", 5), insert = false), planSave(base, null, existing, true, "new", 5))
        // Editar el propio aviso no es conflicto.
        assertEquals(SavePlan.Write(base.toRule("old", 5), insert = false), planSave(base, "old", existing, false, "new", 5))
        // Editar otro aviso hasta chocar con uno existente: confirmado, queda uno solo.
        assertEquals(SavePlan.NeedsConfirmation(existing), planSave(base, "other", existing, false, "new", 5))
        assertEquals(
            SavePlan.Write(base.toRule("old", 5), insert = false, deleteId = "other"),
            planSave(base, "other", existing, true, "new", 5),
        )
    }

    @Test
    fun switchYPosponer() {
        val fired = base.toRule("a", 0).copy(active = false, lastTriggeredAtMs = 10, snoozedUntilMs = 20)
        val rearmed = fired.withActive(true, 99)
        assertEquals(fired.copy(active = true, createdAtMs = 99, lastTriggeredAtMs = null, snoozedUntilMs = null), rearmed)
        val rec = recurring("07:45", "08:30").toRule("r", 0).copy(lastTriggeredAtMs = 10)
        assertEquals(rec.copy(active = false), rec.withActive(false, 99))
        assertEquals(rec, rec.copy(active = false).withActive(true, 99)) // RECURRING conserva su historial
        assertEquals(1_000L + SNOOZE_MS, rec.copy(active = false).snoozed(1_000).snoozedUntilMs)
        assertTrue(rec.copy(active = false).snoozed(1_000).active)
    }

    @Test
    fun destinosRealesDeLaLineaEnLaParada() {
        val catalog = TestData.catalog
        val itinerary = catalog.itineraries.first { it.stops.isNotEmpty() }
        val stop = itinerary.stops.first()
        val live = Arrival(itinerary.lineCod, itinerary.lineId, "Destino visto en vivo", 3, null, ArrivalSource.LIVE, 0, null)
        val other = live.copy(lineId = "otra", destination = "No es de esta línea")
        val destinations = lineDestinationsAtStop(catalog, stop, itinerary.lineCod, listOf(live, other))
        assertTrue(itinerary.destination in destinations)
        assertTrue("Destino visto en vivo" in destinations)
        assertTrue("No es de esta línea" !in destinations)
        assertEquals(destinations.size, destinations.map { it.lowercase() }.distinct().size)
    }
}
