package eus.busdonostia.app.domain

/** ONCE caduca a los 7 días (paridad con la regla 7 del bot). */
const val ONCE_TTL_MS = 7 * 86_400_000L

/**
 * Se avisa 30 s antes de lo que dicen los minutos (decisión de Iker): mejor pronto que
 * tarde. La llegada se evalúa como si fuera `now + 30 s`.
 */
const val NOTIFY_EARLY_MS = 30_000L

/**
 * Máquina de estados única de avisos (§3). Todos los avisos son de un solo uso:
 * el motor, al pasar a `TRIGGERED`, notifica y borra el aviso. `TRIGGERED` mira la
 * llegada [NOTIFY_EARLY_MS] por delante.
 */
fun computeAlertStatus(alert: AlertRule, nowMs: Long, nextArrival: Arrival? = null): ComputedAlertStatus {
    if (!alert.active) return ComputedAlertStatus.INACTIVE
    if (nowMs - alert.createdAtMs > ONCE_TTL_MS) return ComputedAlertStatus.INACTIVE
    if (nextArrival != null && remainingMinutes(nextArrival, nowMs + NOTIFY_EARLY_MS) <= alert.minutesAhead) {
        return ComputedAlertStatus.TRIGGERED
    }
    return ComputedAlertStatus.ACTIVE
}
