package eus.busdonostia.app.domain

/*
 * Motor de avisos (04-android-platform.md §1): decisión pura de cada vuelta del
 * Foreground Service. El servicio solo consulta, borra lo que avisa y notifica.
 */

/** Tras este tiempo seguido sin servicio o con errores en todas las paradas, el motor se para y lo notifica. */
const val NO_SERVICE_STOP_MS = 60 * 60_000L

/** Margen para no repetir una consulta que la UI acaba de hacer (sondeo de 30 s con algo de retraso). */
const val FETCH_SLACK_MS = 5_000L

/** En vigilancia: el servicio tiene que seguir vivo por este aviso (activo y sin caducar). */
fun isWatching(rule: AlertRule, nowMs: Long): Boolean = computeAlertStatus(rule, nowMs) == ComputedAlertStatus.ACTIVE

/** Paradas que hay que consultar: las de avisos en vigilancia. */
fun stopsToPoll(rules: List<AlertRule>, nowMs: Long): Set<String> =
    rules.filter { isWatching(it, nowMs) }.mapTo(sortedSetOf()) { it.stopId }

/** Hay que consultar si nunca se ha hecho o la última respuesta tiene casi un intervalo (la UI también sondea). */
fun isFetchDue(lastFetchedAtMs: Long?, nowMs: Long, intervalMs: Long): Boolean =
    lastFetchedAtMs == null || nowMs - lastFetchedAtMs >= intervalMs - FETCH_SLACK_MS

/** Aviso que acaba de pasar a `TRIGGERED` y la llegada que lo dispara: se notifica y se borra (un solo uso). */
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
 * seguir con los que quedan y lleva la cuenta del tiempo seguido sin servicio ([troubleSinceMs]).
 */
fun evaluateTick(rules: List<AlertRule>, views: Map<String, ArrivalsView>, nowMs: Long, troubleSinceMs: Long?): EngineTick {
    val firings = rules.mapNotNull { rule ->
        val item = alertItem(rule, usableArrivals(views[rule.stopId] ?: ArrivalsView.Loading), nowMs)
        val arrival = item.nextArrival
        if (item.status == ComputedAlertStatus.TRIGGERED && arrival != null) Firing(rule, arrival) else null
    }
    val fired = firings.mapTo(HashSet()) { it.rule.id }
    val watching = rules.filter { it.id !in fired && isWatching(it, nowMs) }

    val polled = stopsToPoll(rules, nowMs).mapNotNull { views[it] }.filter { it != ArrivalsView.Loading }
    val trouble = when {
        polled.isEmpty() -> troubleSinceMs // nada consultado todavía: no cuenta ni a favor ni en contra
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

/** Destino para el título de la notificación: el real de la llegada o el filtro del aviso; null si ninguno. */
fun firingDestination(rule: AlertRule, arrival: Arrival): String? =
    arrival.destination.trim().ifEmpty { null }
        ?: rule.destination?.trim()?.takeIf { it.isNotEmpty() && !destinationsMatch(it, UNKNOWN_DESTINATION) }
