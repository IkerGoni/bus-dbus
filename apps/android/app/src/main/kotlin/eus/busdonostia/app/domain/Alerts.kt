package eus.busdonostia.app.domain

internal const val ONE_DAY_MS = 86_400_000L

/** ONCE caduca a los 7 días (paridad con la regla 7 del bot). */
const val ONCE_TTL_MS = 7 * ONE_DAY_MS

/**
 * Se avisa 30 s antes de lo que dicen los minutos (decisión de Iker): mejor pronto que
 * tarde. La llegada se evalúa como si fuera `now + 30 s`.
 */
const val NOTIFY_EARLY_MS = 30_000L

/**
 * Máquina de estados única de avisos (§3). Todos los avisos son de un solo uso:
 * el motor, al pasar a `TRIGGERED`, notifica y borra el aviso. `TRIGGERED` mira la
 * llegada `triggerBufferSec` por delante.
 */
fun computeAlertStatus(
    alert: AlertRule,
    nowMs: Long,
    nextArrival: Arrival? = null,
    config: AlertsConfig = AlertsConfig(),
): ComputedAlertStatus {
    if (!alert.active) return ComputedAlertStatus.INACTIVE
    val ttlMs = config.expirationDays * ONE_DAY_MS
    if (nowMs - alert.createdAtMs > ttlMs) return ComputedAlertStatus.INACTIVE
    val notifyEarlyMs = config.triggerBufferSec * 1_000L
    if (nextArrival != null && remainingMinutes(nextArrival, nowMs + notifyEarlyMs) <= alert.minutesAhead) {
        return ComputedAlertStatus.TRIGGERED
    }
    return ComputedAlertStatus.ACTIVE
}
