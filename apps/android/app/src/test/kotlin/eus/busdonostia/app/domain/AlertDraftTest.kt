package eus.busdonostia.app.domain

import eus.busdonostia.app.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A06: validación, dedup `(stopId, lineId)` y switch. */
class AlertDraftTest {
    private val base = AlertDraft(stopId = "340", stopName = "Pío Baroja 10", lineId = "5", minutesAhead = 5)

    @Test
    fun onceValidoYFaltaParadaLineaOAntelacion() {
        assertEquals(emptyList<DraftError>(), validateDraft(base))
        assertEquals(listOf(DraftError.NO_STOP, DraftError.NO_LINE), validateDraft(AlertDraft()))
        assertEquals(listOf(DraftError.MINUTES_OUT_OF_RANGE), validateDraft(base.copy(minutesAhead = 0)))
        assertEquals(listOf(DraftError.MINUTES_OUT_OF_RANGE), validateDraft(base.copy(minutesAhead = 31)))
    }

    @Test
    fun destinosSinDuplicadosYConElElegido() {
        assertEquals(listOf("Amara", "Ospitaleak"), destinationChoices(listOf("Amara", "Ospitaleak"), null))
        assertEquals(listOf("Amara", "Ospitaleak"), destinationChoices(listOf("Amara", "Ospitaleak"), "AMARA"))
        // El precargado desde una llegada que hoy no sale en vivo sigue siendo elegible.
        assertEquals(listOf("Amara", "Igara"), destinationChoices(listOf("Amara", "amara"), "Igara"))
    }

    @Test
    fun guardarArmaElAviso() {
        val rule = base.copy(lineId = " b4 ").toRule("id", 1_000)
        assertEquals("B4", rule.lineId)
        assertEquals(1_000L, rule.createdAtMs)
        assertTrue(rule.active)
        assertEquals(base, base.toRule("x", 0).toDraft())
    }

    @Test
    fun nombreDesconocidoNuncaEsElCodigoDeParada() {
        assertEquals("", base.copy(stopName = null).toRule("id", 1_000).stopName)
        assertEquals("", base.copy(stopName = "   ").toRule("id", 1_000).stopName)
        assertEquals("Pío Baroja 10", base.toRule("id", 1_000).stopName)
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
    fun switchPausaYReactiva() {
        val paused = base.toRule("a", 0).copy(active = false)
        // Reactivar (pausado o caducado) lo vuelve a armar desde ahora.
        assertEquals(paused.copy(active = true, createdAtMs = 99), paused.withActive(true, 99))
        assertEquals(paused.copy(active = false), paused.withActive(false, 99))
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
