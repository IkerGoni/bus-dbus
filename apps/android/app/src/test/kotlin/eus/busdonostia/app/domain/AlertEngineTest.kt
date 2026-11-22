package eus.busdonostia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Motor de avisos: disparar / seguir / parar, sondeo y alarmas de RECURRING (04-android-platform.md §1). */
class AlertEngineTest {
    private fun madrid(iso: String): Long = ZonedDateTime.of(LocalDateTime.parse(iso), MADRID).toInstant().toEpochMilli()

    private val now = madrid("2026-11-23T08:00") // lunes

    private fun arrival(line: String, minutes: Int, destination: String = "Amara", source: ArrivalSource = ArrivalSource.LIVE) =
        Arrival(line, line, destination, minutes, null, source, now, null)

    private fun live(vararg arrivals: Arrival) = ArrivalsView.Live(arrivals.toList(), null, now)

    private val noService = ArrivalsView.Degraded(DegradedReason.NO_SERVICE, emptyList(), null, now)

    private fun once(id: String, line: String = "5", stop: String = "340", minutesAhead: Int = 5) = AlertRule(
        id = id, stopId = stop, stopName = "Pío Baroja 10", lineId = line, minutesAhead = minutesAhead,
        frequency = AlertFrequency.ONCE, createdAtMs = now - 1_000,
    )

    private fun recurring(id: String, from: String, to: String, days: Set<Int> = DaysPreset.WEEKDAYS.days) =
        once(id).copy(frequency = AlertFrequency.RECURRING, daysOfWeek = days, windowFrom = from, windowTo = to)

    @Test
    fun onceDisparaUnaVezYSePara() {
        val tick = evaluateTick(listOf(once("a")), mapOf("340" to live(arrival("5", 4))), now, null)
        val firing = tick.firings.single()
        assertEquals(4, firing.arrival.minutesAtFetch)
        assertEquals(now, firing.rule.lastTriggeredAtMs)
        assertFalse(firing.rule.active) // ONCE → active = false
        assertEquals(EngineAction.STOP_IDLE, tick.action)
        assertTrue(tick.watching.isEmpty())
    }

    @Test
    fun sigueMientrasNoLlegaDentroDeLaAntelacion() {
        val tick = evaluateTick(listOf(once("a")), mapOf("340" to live(arrival("5", 12), arrival("28", 2))), now, null)
        assertTrue(tick.firings.isEmpty())
        assertEquals(EngineAction.CONTINUE, tick.action)
        assertEquals(listOf("a"), tick.watching.map { it.id })
    }

    @Test
    fun recurringDisparaUnaVezPorFranja() {
        val rule = recurring("r", "07:45", "08:30")
        val first = evaluateTick(listOf(rule), mapOf("340" to live(arrival("5", 3))), now, null)
        val fired = first.firings.single().rule
        assertTrue(fired.active) // RECURRING sigue activo
        assertEquals(EngineAction.STOP_IDLE, first.action) // ya avisado en la franja: SILENCED
        // Con el siguiente bus, en la misma franja, no vuelve a disparar.
        val second = evaluateTick(listOf(fired), mapOf("340" to live(arrival("5", 2))), now + 60_000, null)
        assertTrue(second.firings.isEmpty())
    }

    @Test
    fun posponerVuelveAAvisarTambienEnRecurring() {
        val fired = recurring("r", "07:45", "08:30").fired(now)
        val snoozed = fired.snoozed(now)
        // Pospuesto: sigue vigilando sin consultar la parada.
        val during = evaluateTick(listOf(snoozed), emptyMap(), now + 60_000, null)
        assertEquals(EngineAction.CONTINUE, during.action)
        assertTrue(stopsToPoll(listOf(snoozed), now + 60_000).isEmpty())
        // Pasados 5 min, segunda notificación.
        val after = now + SNOOZE_MS
        val tick = evaluateTick(listOf(snoozed), mapOf("340" to live(arrival("5", 3))), after, null)
        assertEquals(1, tick.firings.size)
        // ONCE disparado y pospuesto: también vuelve a avisar.
        val onceSnoozed = once("a").fired(now).snoozed(now)
        assertEquals(1, evaluateTick(listOf(onceSnoozed), mapOf("340" to live(arrival("5", 3))), after, null).firings.size)
    }

    @Test
    fun soloVigilaLoQueTocaYAgrupaPorParada() {
        val rules = listOf(
            once("a", stop = "340"),
            once("b", line = "28", stop = "340"),
            once("c", stop = "1").copy(active = false),
            recurring("d", "18:00", "19:00"),
            once("e", stop = "7").copy(snoozedUntilMs = now + SNOOZE_MS),
        )
        assertEquals(setOf("340"), stopsToPoll(rules, now))
        assertEquals(listOf("a", "b", "e"), rules.filter { isWatching(it, now) }.map { it.id })
        assertFalse(isWatching(once("x").copy(createdAtMs = now - ONCE_TTL_MS - 1), now)) // caducado
    }

    @Test
    fun seParaTras60MinutosSinServicio() {
        val rules = listOf(once("a"), once("b", stop = "1"))
        val start = evaluateTick(rules, mapOf("340" to noService, "1" to ArrivalsView.Error), now, null)
        assertEquals(now, start.troubleSinceMs)
        assertEquals(EngineAction.CONTINUE, start.action)
        val later = now + NO_SERVICE_STOP_MS - 1
        assertEquals(EngineAction.CONTINUE, evaluateTick(rules, mapOf("340" to noService, "1" to ArrivalsView.Error), later, now).action)
        val end = evaluateTick(rules, mapOf("340" to noService, "1" to ArrivalsView.Error), now + NO_SERVICE_STOP_MS, now)
        assertEquals(EngineAction.STOP_NO_SERVICE, end.action)
        // Basta una parada que responda para reiniciar la cuenta.
        val ok = evaluateTick(rules, mapOf("340" to noService, "1" to live(arrival("13", 20))), now + NO_SERVICE_STOP_MS, now)
        assertNull(ok.troubleSinceMs)
        assertEquals(EngineAction.CONTINUE, ok.action)
    }

    @Test
    fun datosViejosSiguenSirviendoYPrefiereEnVivo() {
        val stale = ArrivalsView.Degraded(DegradedReason.STALE, listOf(arrival("5", 6).copy(fetchedAtMs = now - 60_000)), null, now - 60_000)
        val tick = evaluateTick(listOf(once("a")), mapOf("340" to stale), now, null)
        assertEquals(1, tick.firings.size) // 6 min hace 1 min → 5 min
        assertNull(tick.troubleSinceMs)
        val both = live(arrival("5", 3, source = ArrivalSource.SCHEDULED), arrival("5", 4))
        assertEquals(ArrivalSource.LIVE, evaluateTick(listOf(once("a")), mapOf("340" to both), now, null).firings.single().arrival.source)
    }

    @Test
    fun consultaSoloSiTocaPorIntervalo() {
        assertTrue(isFetchDue(null, now, 30_000))
        assertFalse(isFetchDue(now - 10_000, now, 30_000)) // la UI acaba de consultar
        assertTrue(isFetchDue(now - 25_000, now, 30_000))
    }

    @Test
    fun alarmaEnElProximoInicioDeFranja() {
        val weekdays = recurring("r", "07:45", "08:30")
        // Lunes 08:00: la de hoy ya empezó → martes 07:45.
        assertEquals(madrid("2026-11-24T07:45"), nextAlarmAtMs(listOf(weekdays), now))
        // Viernes por la tarde → lunes.
        assertEquals(madrid("2026-11-30T07:45"), nextAlarmAtMs(listOf(weekdays), madrid("2026-11-27T19:00")))
        // Domingo = 7.
        val sunday = recurring("s", "10:00", "11:00", days = setOf(7))
        assertEquals(madrid("2026-11-29T10:00"), nextAlarmAtMs(listOf(sunday), now))
        // Cruce de medianoche: empieza el día marcado.
        val night = recurring("n", "23:30", "00:30", days = setOf(1))
        assertEquals(madrid("2026-11-23T23:30"), nextAlarmAtMs(listOf(night), now))
        // La más cercana entre varias; pausados y ONCE no cuentan.
        val evening = recurring("e", "18:00", "19:00")
        assertEquals(madrid("2026-11-23T18:00"), nextAlarmAtMs(listOf(weekdays, evening, sunday.copy(active = false), once("o")), now))
        assertNull(nextAlarmAtMs(listOf(once("o"), evening.copy(active = false)), now))
    }

    @Test
    fun alarmaConCambioDeHora() {
        // 29-03-2026: a las 02:00 pasa a 03:00. 02:30 no existe → 03:30 (+02:00).
        val gap = recurring("g", "02:30", "03:30", days = setOf(7))
        val gapAt = nextAlarmAtMs(listOf(gap), madrid("2026-03-28T12:00"))
        assertEquals(ZonedDateTime.parse("2026-03-29T03:30+02:00[Europe/Madrid]").toInstant().toEpochMilli(), gapAt)
        // 25-10-2026: 02:30 ocurre dos veces; vale la primera (+02:00).
        val overlap = recurring("o", "02:30", "03:30", days = setOf(7))
        val overlapAt = nextAlarmAtMs(listOf(overlap), madrid("2026-10-24T12:00"))
        assertEquals(ZonedDateTime.parse("2026-10-25T02:30+02:00[Europe/Madrid]").toInstant().toEpochMilli(), overlapAt)
        // Las 07:45 de Madrid siguen siendo 07:45 después del cambio (UTC distinto).
        val after = nextAlarmAtMs(listOf(recurring("r", "07:45", "08:30")), madrid("2026-10-25T12:00"))
        assertEquals(ZonedDateTime.parse("2026-10-26T07:45+01:00[Europe/Madrid]").toInstant().toEpochMilli(), after)
    }

    @Test
    fun destinoDelTitulo() {
        assertEquals("Amara", firingDestination(once("a"), arrival("5", 3)))
        assertEquals("Ospitaleak", firingDestination(once("a").copy(destination = "Ospitaleak"), arrival("5", 3, destination = " ")))
        assertNull(firingDestination(once("a").copy(destination = UNKNOWN_DESTINATION), arrival("5", 3, destination = "")))
        assertNull(firingDestination(once("a"), arrival("5", 3, destination = "")))
    }
}
