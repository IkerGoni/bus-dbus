package eus.busdonostia.app.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** Días y franjas se evalúan siempre en Madrid, no en el TZ del móvil. */
val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

/** ONCE caduca a los 7 días (paridad con la regla 7 del bot). */
const val ONCE_TTL_MS = 7 * 86_400_000L

/** Posponer 5'. */
const val SNOOZE_MS = 300_000L

/** Franja concreta de un aviso RECURRING: `[startMs, endMs]`. */
data class AlertWindow(val startMs: Long, val endMs: Long)

private fun parseHhMm(value: String?): LocalTime? =
    try {
        value?.let(LocalTime::parse)
    } catch (_: DateTimeParseException) {
        null
    }

/**
 * Franja de [alert] que contiene [nowMs], o null si hoy no toca o se está fuera.
 * Admite franjas que cruzan medianoche (`23:30–00:30`): la franja pertenece al día
 * en que empieza, así que también se mira la que empezó ayer. Las horas que no
 * existen o se repiten por el cambio de hora las resuelve `ZonedDateTime` (en marzo
 * 02:30 pasa a 03:30; en octubre 02:30 es la primera ocurrencia).
 */
fun currentWindow(alert: AlertRule, nowMs: Long): AlertWindow? {
    val from = parseHhMm(alert.windowFrom) ?: return null
    val to = parseHhMm(alert.windowTo) ?: return null
    if (from == to) return null
    val today = Instant.ofEpochMilli(nowMs).atZone(MADRID).toLocalDate()
    return listOf(today, today.minusDays(1)).firstNotNullOfOrNull { day ->
        if (day.dayOfWeek.value !in alert.daysOfWeek) return@firstNotNullOfOrNull null
        val start = ZonedDateTime.of(day, from, MADRID)
        val end = ZonedDateTime.of(if (to > from) day else day.plusDays(1), to, MADRID)
        val window = AlertWindow(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
        window.takeIf { nowMs in it.startMs..it.endMs }
    }
}

/**
 * Máquina de estados única de avisos (§3). El motor dispara al pasar a `TRIGGERED`,
 * escribe `lastTriggeredAtMs = now` y, si es ONCE, `active = false`. En RECURRING
 * eso deja el aviso en `SILENCED` hasta la franja siguiente: «Descartar» no re-dispara.
 */
fun computeAlertStatus(alert: AlertRule, nowMs: Long, nextArrival: Arrival? = null): ComputedAlertStatus {
    if (!alert.active) return ComputedAlertStatus.INACTIVE
    if (alert.frequency == AlertFrequency.ONCE && nowMs - alert.createdAtMs > ONCE_TTL_MS) {
        return ComputedAlertStatus.INACTIVE
    }
    if (alert.snoozedUntilMs != null && nowMs < alert.snoozedUntilMs) return ComputedAlertStatus.SNOOZED
    if (alert.frequency == AlertFrequency.RECURRING) {
        val window = currentWindow(alert, nowMs) ?: return ComputedAlertStatus.SILENCED
        if (alert.lastTriggeredAtMs != null && alert.lastTriggeredAtMs >= window.startMs) {
            return ComputedAlertStatus.SILENCED
        }
    }
    if (nextArrival != null && remainingMinutes(nextArrival, nowMs) <= alert.minutesAhead) {
        return ComputedAlertStatus.TRIGGERED
    }
    return ComputedAlertStatus.ACTIVE
}
