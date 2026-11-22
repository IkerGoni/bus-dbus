package eus.busdonostia.app.domain

import java.time.Instant
import java.time.ZonedDateTime

/*
 * Motor de avisos (04-android-platform.md §1): decisión pura de cada vuelta del
 * Foreground Service y de las alarmas de RECURRING. El servicio solo consulta,
 * escribe lo que sale de aquí y notifica.
 */

/** Tras este tiempo seguido sin servicio o con errores en todas las paradas, el motor se para y lo notifica. */
const val NO_SERVICE_STOP_MS = 60 * 60_000L

/** Margen para no repetir una consulta que la UI acaba de hacer (sondeo de 30 s con algo de retraso). */
const val FETCH_SLACK_MS = 5_000L

/**
 * En vigilancia: el servicio tiene que seguir vivo por este aviso. ONCE activo y sin
 * caducar, RECURRING dentro de su franja sin haber avisado aún, o pospuesto.
 */
fun isWatching(rule: AlertRule, nowMs: Long): Boolean = when (computeAlertStatus(rule, nowMs)) {
    ComputedAlertStatus.ACTIVE, ComputedAlertStatus.SNOOZED -> true
    else -> false
}

/** Paradas que hay que consultar: las de avisos en vigilancia que no están pospuestos. */
fun stopsToPoll(rules: List<AlertRule>, nowMs: Long): Set<String> =
    rules.filter { computeAlertStatus(it, nowMs) == ComputedAlertStatus.ACTIVE }.mapTo(sortedSetOf()) { it.stopId }

/** Hay que consultar si nunca se ha hecho o la última respuesta tiene casi un intervalo (la UI también sondea). */
fun isFetchDue(lastFetchedAtMs: Long?, nowMs: Long, intervalMs: Long): Boolean =
    lastFetchedAtMs == null || nowMs - lastFetchedAtMs >= intervalMs - FETCH_SLACK_MS

/** Lo que se escribe al disparar: `lastTriggeredAtMs = now` y, si es ONCE, `active = false` (§3). */
fun AlertRule.fired(nowMs: Long): AlertRule =
    copy(lastTriggeredAtMs = nowMs, active = if (frequency == AlertFrequency.ONCE) false else active)

/** Aviso que acaba de pasar a `TRIGGERED`: [rule] ya actualizado con [fired] y la llegada que lo dispara. */
data class Firing(val rule: AlertRule, val arrival: Arrival)

enum class EngineAction {
    CONTINUE,

    /** No queda ningún aviso en vigilancia. */
    STOP_IDLE,

    /** [NO_SERVICE_STOP_MS] seguidos sin servicio ni datos: se para y se notifica. */
    STOP_NO_SERVICE,
}

data class EngineTick(
    val firings: List<Firing>,
    val action: EngineAction,
    /** Desde cuándo todas las paradas consultadas están sin servicio o con error; null si alguna responde. */
    val troubleSinceMs: Long?,
    /** Avisos en vigilancia tras esta vuelta (notificación «Vigilando …»). */
    val watching: List<AlertRule>,
)

/**
 * Una vuelta del motor. [views] = vista de 5 estados de cada parada consultada.
 * Dispara lo que esté `TRIGGERED` (misma `computeAlertStatus` que A01/A04), decide si
 * seguir y lleva la cuenta del tiempo seguido sin servicio ([troubleSinceMs]).
 */
fun evaluateTick(rules: List<AlertRule>, views: Map<String, ArrivalsView>, nowMs: Long, troubleSinceMs: Long?): EngineTick {
    val firings = rules.mapNotNull { rule ->
        val item = alertItem(rule, usableArrivals(views[rule.stopId] ?: ArrivalsView.Loading), nowMs)
        val arrival = item.nextArrival
        if (item.status == ComputedAlertStatus.TRIGGERED && arrival != null) Firing(rule.fired(nowMs), arrival) else null
    }
    val updated = firings.associateBy { it.rule.id }
    val after = rules.map { updated[it.id]?.rule ?: it }
    val watching = after.filter { isWatching(it, nowMs) }

    val polled = stopsToPoll(rules, nowMs).mapNotNull { views[it] }.filter { it != ArrivalsView.Loading }
    val trouble = when {
        polled.isEmpty() -> troubleSinceMs // nada consultado (todo pospuesto): no cuenta ni a favor ni en contra
        polled.all(::isTrouble) -> troubleSinceMs ?: nowMs
        else -> null
    }
    val action = when {
        watching.isEmpty() -> EngineAction.STOP_IDLE
        trouble != null && nowMs - trouble >= NO_SERVICE_STOP_MS -> EngineAction.STOP_NO_SERVICE
        else -> EngineAction.CONTINUE
    }
    return EngineTick(firings, action, trouble, watching)
}

private fun isTrouble(view: ArrivalsView): Boolean =
    view == ArrivalsView.Error || (view is ArrivalsView.Degraded && view.reason == DegradedReason.NO_SERVICE)

/**
 * Próximo inicio de franja (`windowFrom` de un día marcado, hora de Madrid) estrictamente
 * posterior a [nowMs] entre los RECURRING activos; null si no hay ninguno. Una sola alarma
 * basta: al sonar arranca el servicio, que vigila todos los avisos que estén en franja.
 */
fun nextAlarmAtMs(rules: List<AlertRule>, nowMs: Long): Long? {
    val today = Instant.ofEpochMilli(nowMs).atZone(MADRID).toLocalDate()
    return rules
        .filter { it.active && it.frequency == AlertFrequency.RECURRING }
        .mapNotNull { rule ->
            val from = parseClock(rule.windowFrom) ?: return@mapNotNull null
            (0L..7L).asSequence()
                .map { today.plusDays(it) }
                .filter { it.dayOfWeek.value in rule.daysOfWeek }
                .map { ZonedDateTime.of(it, from, MADRID).toInstant().toEpochMilli() }
                .firstOrNull { it > nowMs }
        }
        .minOrNull()
}

/** Destino para el título de la notificación: el real de la llegada o el filtro del aviso; null si ninguno. */
fun firingDestination(rule: AlertRule, arrival: Arrival): String? =
    arrival.destination.trim().ifEmpty { null }
        ?: rule.destination?.trim()?.takeIf { it.isNotEmpty() && !destinationsMatch(it, UNKNOWN_DESTINATION) }
