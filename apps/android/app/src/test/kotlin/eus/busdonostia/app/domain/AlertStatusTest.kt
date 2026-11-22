package eus.busdonostia.app.domain

import eus.busdonostia.app.domain.ComputedAlertStatus.ACTIVE
import eus.busdonostia.app.domain.ComputedAlertStatus.INACTIVE
import eus.busdonostia.app.domain.ComputedAlertStatus.SILENCED
import eus.busdonostia.app.domain.ComputedAlertStatus.SNOOZED
import eus.busdonostia.app.domain.ComputedAlertStatus.TRIGGERED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Tabla §5 de `docs/07-android-mvp/docs/02-domain-contracts.md`, fila a fila. */
class AlertStatusTest {
    /** Epoch ms de una hora local de Madrid. */
    private fun madrid(iso: String): Long =
        ZonedDateTime.of(LocalDateTime.parse(iso), MADRID).toInstant().toEpochMilli()

    private fun arrival(minutes: Int, fetchedAtMs: Long, source: ArrivalSource = ArrivalSource.LIVE) =
        Arrival("28", "32", "Ospitaleak", minutes, null, source, fetchedAtMs, null)

    private fun once(createdAtMs: Long, minutesAhead: Int = 5) = AlertRule(
        id = "a", stopId = "1", stopName = "Boulevard 9", lineId = "28",
        minutesAhead = minutesAhead, frequency = AlertFrequency.ONCE, createdAtMs = createdAtMs,
    )

    private fun recurring(from: String, to: String, days: Set<Int>, lastTriggeredAtMs: Long? = null) = AlertRule(
        id = "r", stopId = "1", stopName = "Boulevard 9", lineId = "28", minutesAhead = 5,
        frequency = AlertFrequency.RECURRING, daysOfWeek = days, windowFrom = from, windowTo = to,
        createdAtMs = 0, lastTriggeredAtMs = lastTriggeredAtMs,
    )

    // ── «ONCE sin final» → active = false al disparar o a los 7 días ──

    @Test
    fun onceCaducaALos7Dias() {
        val created = madrid("2026-11-02T08:00")
        val alert = once(created)
        assertEquals(ACTIVE, computeAlertStatus(alert, created + ONCE_TTL_MS))
        assertEquals(INACTIVE, computeAlertStatus(alert, created + ONCE_TTL_MS + 1))
        // Caducado no dispara ni con un bus encima.
        val now = created + ONCE_TTL_MS + 60_000
        assertEquals(INACTIVE, computeAlertStatus(alert, now, arrival(1, now)))
    }

    @Test
    fun onceDisparadaQuedaInactiva() {
        val created = madrid("2026-11-02T08:00")
        val now = created + 60_000
        assertEquals(TRIGGERED, computeAlertStatus(once(created), now, arrival(3, now)))
        // El motor escribe active = false tras disparar.
        assertEquals(INACTIVE, computeAlertStatus(once(created).copy(active = false, lastTriggeredAtMs = now), now, arrival(3, now)))
    }

    // ── «etaSeconds» → minutos enteros; el reloj solo decide cuándo baja el minuto ──

    @Test
    fun disparaCuandoElMinutoRestanteEntraEnLaVentana() {
        val created = madrid("2026-11-02T08:00")
        val fetched = created
        val a = arrival(minutes = 7, fetchedAtMs = fetched)
        val alert = once(created, minutesAhead = 5)
        assertEquals(ACTIVE, computeAlertStatus(alert, fetched + 119_999, a)) // aún 6 min
        assertEquals(TRIGGERED, computeAlertStatus(alert, fetched + 120_000, a)) // 5 min
    }

    @Test
    fun pospuestoHastaSnoozedUntil() {
        val created = madrid("2026-11-02T08:00")
        val now = created + 60_000
        val alert = once(created).copy(snoozedUntilMs = now + SNOOZE_MS)
        assertEquals(SNOOZED, computeAlertStatus(alert, now, arrival(1, now)))
        assertEquals(TRIGGERED, computeAlertStatus(alert, now + SNOOZE_MS, arrival(1, now + SNOOZE_MS)))
    }

    @Test
    fun pausadoEsInactivo() {
        assertEquals(INACTIVE, computeAlertStatus(once(0).copy(active = false), 1))
    }

    // ── «RECURRING solo con días + silencio 23–07» → franja obligatoria ──

    @Test
    fun recurringFueraDeFranjaODiaEstaSilenciado() {
        // 2026-11-23 es lunes (ISO 1).
        val alert = recurring("07:45", "08:30", setOf(1))
        assertEquals(SILENCED, computeAlertStatus(alert, madrid("2026-11-23T07:44")))
        assertEquals(ACTIVE, computeAlertStatus(alert, madrid("2026-11-23T07:45")))
        assertEquals(ACTIVE, computeAlertStatus(alert, madrid("2026-11-23T08:30")))
        assertEquals(SILENCED, computeAlertStatus(alert, madrid("2026-11-23T08:31")))
        assertEquals(SILENCED, computeAlertStatus(alert, madrid("2026-11-24T08:00"))) // martes
    }

    @Test
    fun recurringSinFranjaNoAvisaNunca() {
        val alert = recurring("07:45", "08:30", setOf(1, 2, 3, 4, 5, 6, 7)).copy(windowFrom = null, windowTo = null)
        val now = madrid("2026-11-23T08:00")
        assertEquals(SILENCED, computeAlertStatus(alert, now, arrival(1, now)))
    }

    // ── «daysOfWeek 0=Dom» → ISO 1=Lun..7=Dom ──

    @Test
    fun domingoEs7() {
        // 2026-11-22 es domingo.
        val now = madrid("2026-11-22T10:00")
        assertNotNull(currentWindow(recurring("09:00", "11:00", setOf(7)), now))
        assertNull(currentWindow(recurring("09:00", "11:00", setOf(1)), now))
    }

    // ── «Sin zona horaria» → Europe/Madrid ──

    @Test
    fun laFranjaSeEvaluaEnMadridNoEnElTzDelMovil() {
        // 07:50 en Madrid (invierno, UTC+1) = 06:50 UTC. Da igual el TZ por defecto de la JVM.
        val now = java.time.Instant.parse("2026-11-23T06:50:00Z").toEpochMilli()
        assertEquals(ACTIVE, computeAlertStatus(recurring("07:45", "08:30", setOf(1)), now))
    }

    // ── Cruce de medianoche: la franja pertenece al día en que empieza ──

    @Test
    fun franjaQueCruzaMedianoche() {
        val friday = recurring("23:30", "00:30", setOf(5)) // 2026-11-27 es viernes
        assertEquals(ACTIVE, computeAlertStatus(friday, madrid("2026-11-27T23:45")))
        assertEquals(ACTIVE, computeAlertStatus(friday, madrid("2026-11-28T00:15"))) // ya sábado
        assertEquals(SILENCED, computeAlertStatus(friday, madrid("2026-11-28T00:31")))
        // Marcado solo el sábado: la madrugada del sábado pertenece a la franja del viernes.
        val saturday = recurring("23:30", "00:30", setOf(6))
        assertEquals(SILENCED, computeAlertStatus(saturday, madrid("2026-11-28T00:15")))
        assertEquals(ACTIVE, computeAlertStatus(saturday, madrid("2026-11-28T23:40")))
        val window = currentWindow(friday, madrid("2026-11-28T00:15"))!!
        assertEquals(madrid("2026-11-27T23:30"), window.startMs)
        assertEquals(madrid("2026-11-28T00:30"), window.endMs)
    }

    // ── Cambio de hora (DST) ──

    @Test
    fun dstMarzo() {
        // 2027-03-28 (domingo): a las 02:00 CET se pasa a 03:00 CEST. La franja 01:30–03:30 dura 1 h real.
        val alert = recurring("01:30", "03:30", setOf(7))
        val window = currentWindow(alert, madrid("2027-03-28T01:45"))!!
        assertEquals(3_600_000L, window.endMs - window.startMs)
        assertEquals(ACTIVE, computeAlertStatus(alert, madrid("2027-03-28T03:15")))
        // Una franja que empieza en la hora inexistente (02:30) arranca a las 03:30 CEST.
        val gap = recurring("02:30", "04:00", setOf(7))
        assertEquals(SILENCED, computeAlertStatus(gap, madrid("2027-03-28T03:20")))
        assertEquals(ACTIVE, computeAlertStatus(gap, madrid("2027-03-28T03:30")))
        // La franja de diario sigue a la misma hora local tras el cambio.
        val daily = recurring("07:45", "08:30", setOf(6, 7, 1))
        assertEquals(ACTIVE, computeAlertStatus(daily, java.time.Instant.parse("2027-03-27T06:50:00Z").toEpochMilli())) // CET
        assertEquals(ACTIVE, computeAlertStatus(daily, java.time.Instant.parse("2027-03-29T05:50:00Z").toEpochMilli())) // CEST
        assertEquals(SILENCED, computeAlertStatus(daily, java.time.Instant.parse("2027-03-29T06:50:00Z").toEpochMilli()))
    }

    @Test
    fun dstOctubre() {
        // 2026-10-25 (domingo): a las 03:00 CEST se vuelve a 02:00 CET. La franja 01:30–03:30 dura 3 h reales.
        val alert = recurring("01:30", "03:30", setOf(7))
        val start = madrid("2026-10-25T01:30")
        val window = currentWindow(alert, start)!!
        assertEquals(3 * 3_600_000L, window.endMs - window.startMs)
        // Las 02:30 ocurren dos veces y ambas están dentro de la franja.
        val firstHalfPast2 = java.time.Instant.parse("2026-10-25T00:30:00Z").toEpochMilli() // CEST
        val secondHalfPast2 = java.time.Instant.parse("2026-10-25T01:30:00Z").toEpochMilli() // CET
        assertEquals(ACTIVE, computeAlertStatus(alert, firstHalfPast2))
        assertEquals(ACTIVE, computeAlertStatus(alert, secondHalfPast2))
        // Disparado en la primera 02:30: la repetición de la hora no vuelve a disparar.
        val triggered = alert.copy(lastTriggeredAtMs = firstHalfPast2)
        assertEquals(SILENCED, computeAlertStatus(triggered, secondHalfPast2, arrival(1, secondHalfPast2)))
    }

    // ── «recentlyTriggered 2 min» → una vez por franja; Descartar no re-dispara ──

    @Test
    fun unaVezPorFranjaYDescartarNoRedispara() {
        val alert = recurring("07:45", "08:30", setOf(1, 2))
        val t1 = madrid("2026-11-23T07:50")
        assertEquals(TRIGGERED, computeAlertStatus(alert, t1, arrival(4, t1)))
        // El motor escribe lastTriggeredAtMs; el usuario pulsa «Descartar» (no cambia nada más).
        val fired = alert.copy(lastTriggeredAtMs = t1)
        val t2 = t1 + 3 * 60_000
        assertEquals(SILENCED, computeAlertStatus(fired, t2, arrival(2, t2))) // mismo bus
        val t3 = madrid("2026-11-23T08:20")
        assertEquals(SILENCED, computeAlertStatus(fired, t3, arrival(3, t3))) // siguiente bus, misma franja
        // Franja del día siguiente: vuelve a vigilar y dispara.
        val t4 = madrid("2026-11-24T07:50")
        assertEquals(ACTIVE, computeAlertStatus(fired, t4))
        assertEquals(TRIGGERED, computeAlertStatus(fired, t4, arrival(5, t4)))
    }
}
