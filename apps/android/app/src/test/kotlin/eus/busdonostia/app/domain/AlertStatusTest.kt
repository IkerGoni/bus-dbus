package eus.busdonostia.app.domain

import eus.busdonostia.app.domain.ComputedAlertStatus.ACTIVE
import eus.busdonostia.app.domain.ComputedAlertStatus.INACTIVE
import eus.busdonostia.app.domain.ComputedAlertStatus.TRIGGERED
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Tabla §5 de `docs/07-android-mvp/docs/02-domain-contracts.md`: avisos de un solo uso. */
class AlertStatusTest {
    /** Epoch ms de una hora local de Madrid. */
    private fun madrid(iso: String): Long =
        ZonedDateTime.of(LocalDateTime.parse(iso), ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli()

    private fun arrival(minutes: Int, fetchedAtMs: Long, source: ArrivalSource = ArrivalSource.LIVE) =
        Arrival("28", "32", "Ospitaleak", minutes, null, source, fetchedAtMs, null)

    private fun once(createdAtMs: Long, minutesAhead: Int = 5) = AlertRule(
        id = "a", stopId = "1", stopName = "Boulevard 9", lineId = "28",
        minutesAhead = minutesAhead, createdAtMs = createdAtMs,
    )

    // ── «ONCE sin final» → se borra al disparar (motor) o queda inactivo a los 7 días ──

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
    fun disparaConUnBusDentroDeLaAntelacion() {
        val created = madrid("2026-11-02T08:00")
        val now = created + 60_000
        assertEquals(TRIGGERED, computeAlertStatus(once(created), now, arrival(3, now)))
        assertEquals(ACTIVE, computeAlertStatus(once(created), now, arrival(6, now)))
    }

    // ── «etaSeconds» → minutos enteros; el reloj solo decide cuándo baja el minuto ──

    @Test
    fun disparaCuandoElMinutoRestanteEntraEnLaVentana() {
        val created = madrid("2026-11-02T08:00")
        val fetched = created
        val a = arrival(minutes = 7, fetchedAtMs = fetched)
        val alert = once(created, minutesAhead = 5)
        // Avisa 30 s antes (NOTIFY_EARLY_MS): mejor pronto que tarde.
        assertEquals(ACTIVE, computeAlertStatus(alert, fetched + 89_999, a)) // aún 6 min dentro de 30 s
        assertEquals(TRIGGERED, computeAlertStatus(alert, fetched + 90_000, a)) // en 30 s serán 5 min
        // Un bus que llega con un minuto de más que la antelación ya dispara: está a menos de 30 s de entrar.
        assertEquals(ACTIVE, computeAlertStatus(alert, fetched, arrival(minutes = 6, fetchedAtMs = fetched)))
        assertEquals(TRIGGERED, computeAlertStatus(alert, fetched + 30_000, arrival(minutes = 6, fetchedAtMs = fetched)))
    }

    @Test
    fun pausadoEsInactivo() {
        assertEquals(INACTIVE, computeAlertStatus(once(0).copy(active = false), 1))
    }
}
