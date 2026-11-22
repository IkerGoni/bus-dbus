package eus.busdonostia.app.domain

/** ONCE caduca a los 7 días (paridad con la regla 7 del bot). */
const val ONCE_TTL_MS = 7 * 86_400_000L

/** Posponer 5' (A01). */
const val SNOOZE_MS = 300_000L

/**
 * Máquina de estados única de avisos (§3). Todos los avisos son de un solo uso:
 * el motor, al pasar a `TRIGGERED`, notifica y borra el aviso.
 */
fun computeAlertStatus(alert: AlertRule, nowMs: Long, nextArrival: Arrival? = null): ComputedAlertStatus {
    if (!alert.active) return ComputedAlertStatus.INACTIVE
    if (nowMs - alert.createdAtMs > ONCE_TTL_MS) return ComputedAlertStatus.INACTIVE
    if (alert.snoozedUntilMs != null && nowMs < alert.snoozedUntilMs) return ComputedAlertStatus.SNOOZED
    if (nextArrival != null && remainingMinutes(nextArrival, nowMs) <= alert.minutesAhead) {
        return ComputedAlertStatus.TRIGGERED
    }
    return ComputedAlertStatus.ACTIVE
}
