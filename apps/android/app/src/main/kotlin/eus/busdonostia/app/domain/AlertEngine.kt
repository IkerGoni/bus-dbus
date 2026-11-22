package eus.busdonostia.app.domain

/*
 * Motor de avisos (04-android-platform.md §1 y 04-background-bateria-y-recurrentes.md):
 * decisión pura de cada vuelta del Foreground Service. El servicio solo consulta,
 * borra lo que avisa y notifica.
 */

/** Tras este tiempo seguido sin servicio o con errores en todas las paradas, el motor se para y lo notifica. */
const val NO_SERVICE_STOP_MS = 60 * 60_000L

/** Margen para no repetir una consulta que la UI acaba de hacer (sondeo de 30 s con algo de retraso). */
const val FETCH_SLACK_MS = 5_000L

/** En vigilancia: el servicio tiene que seguir vivo por este aviso (activo y sin caducar). */
fun isWatching(
    rule: AlertRule,
    nowMs: Long,
    alertsConfig: AlertsConfig = AlertsConfig(),
): Boolean = computeAlertStatus(rule, nowMs, config = alertsConfig) == ComputedAlertStatus.ACTIVE

/** Paradas que hay que consultar: las de avisos en vigilancia. */
fun stopsToPoll(
    rules: List<AlertRule>,
    nowMs: Long,
    alertsConfig: AlertsConfig = AlertsConfig(),
): Set<String> =
    rules.filter { isWatching(it, nowMs, alertsConfig) }.mapTo(sortedSetOf()) { it.stopId }

/**
 * Sondeo adaptativo en 4 escalones desde [config] (`PollingConfig`):
 * cuanto más le falta al bus para entrar en la antelación del aviso
 * ([gapMinutes] = minutos restantes − antelación), menos se consulta:
 * - gap >= 31 min → 600 s (10 min)
 * - gap 15..30 min → 240 s (4 min)
 * - gap 8..14 min → 60 s (1 min)
 * - gap <= 7 min → 30 s
 * - sin bus de la línea en la respuesta (o sin servicio / error: `gapMinutes == null`) →
 *   `baseBackgroundSec * noServiceOrErrorMultiplier` (60 s).
 */
fun pollDelayMs(
    gapMinutes: Int?,
    config: PollingConfig = PollingConfig(),
): Long {
    val baseMs = config.baseBackgroundSec * 1_000L
    if (gapMinutes == null) {
        return baseMs * config.noServiceOrErrorMultiplier
    }
    val tier = config.adaptiveTiers
        .sortedByDescending { it.minGapMin }
        .firstOrNull { gapMinutes >= it.minGapMin }
    return (tier?.delaySec?.times(1_000L)) ?: baseMs
}

/** Overload de compatibilidad con `baseMs` explícito escalando proporcionalmente los escalones de `PollingConfig`. */
fun pollDelayMs(
    gapMinutes: Int?,
    baseMs: Long,
    config: PollingConfig = PollingConfig(),
): Long {
    val defaultBaseMs = config.baseBackgroundSec * 1_000L
    val rawDelayMs = pollDelayMs(gapMinutes, config)
    return if (baseMs == defaultBaseMs || defaultBaseMs <= 0L) {
        rawDelayMs
    } else {
        (rawDelayMs * baseMs) / defaultBaseMs
    }
}

/** Espera hasta la próxima vuelta: la del aviso más urgente entre los que siguen en vigilancia. */
fun nextPollDelayMs(
    watching: List<AlertRule>,
    views: Map<String, ArrivalsView>,
    nowMs: Long,
    config: PollingConfig = PollingConfig(),
): Long {
    val baseMs = config.baseBackgroundSec * 1_000L
    return watching.minOfOrNull { rule ->
        val arrivals = usableArrivals(views[rule.stopId] ?: ArrivalsView.Loading)
        val next = nextArrivalFor(arrivals, rule.lineId, rule.destination, nowMs)
        pollDelayMs(next?.let { remainingMinutes(it, nowMs) - rule.minutesAhead }, config)
    } ?: baseMs
}

fun nextPollDelayMs(
    watching: List<AlertRule>,
    views: Map<String, ArrivalsView>,
    nowMs: Long,
    baseMs: Long,
    config: PollingConfig = PollingConfig(),
): Long =
    watching.minOfOrNull { rule ->
        val arrivals = usableArrivals(views[rule.stopId] ?: ArrivalsView.Loading)
        val next = nextArrivalFor(arrivals, rule.lineId, rule.destination, nowMs)
        pollDelayMs(next?.let { remainingMinutes(it, nowMs) - rule.minutesAhead }, baseMs, config)
    } ?: baseMs

/** Hay que consultar si nunca se ha hecho o la última respuesta tiene casi un intervalo (la UI también sondea). */
fun isFetchDue(lastFetchedAtMs: Long?, nowMs: Long, intervalMs: Long): Boolean =
    lastFetchedAtMs == null || nowMs - lastFetchedAtMs >= intervalMs - FETCH_SLACK_MS

/**
 * Respuesta que el sondeo de la UI puede reutilizar en vez de pedir otra (H16): la de
 * alguien (el motor) que consultó la parada hace menos de un intervalo; null = hay que pedir.
 */
fun reusableFeed(known: StopFeed?, nowMs: Long, intervalMs: Long): StopFeed? =
    known?.takeUnless { isFetchDue(it.fetchedAtMs, nowMs, intervalMs) }

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
fun evaluateTick(
    rules: List<AlertRule>,
    views: Map<String, ArrivalsView>,
    nowMs: Long,
    troubleSinceMs: Long?,
    alertsConfig: AlertsConfig = AlertsConfig(),
): EngineTick {
    val firings = rules.mapNotNull { rule ->
        val item = alertItem(rule, usableArrivals(views[rule.stopId] ?: ArrivalsView.Loading), nowMs, alertsConfig)
        val arrival = item.nextArrival
        if (item.status == ComputedAlertStatus.TRIGGERED && arrival != null) Firing(rule, arrival) else null
    }
    val fired = firings.mapTo(HashSet()) { it.rule.id }
    val watching = rules.filter { it.id !in fired && isWatching(it, nowMs, alertsConfig) }

    val polled = stopsToPoll(rules, nowMs, alertsConfig).mapNotNull { views[it] }.filter { it != ArrivalsView.Loading }
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

/** Vuelta que ha fallado por algo inesperado (BD, bug): cuenta como problema, igual que sin servicio. */
data class FailedTick(val troubleSinceMs: Long, val action: EngineAction)

/**
 * Un fallo inesperado no tumba la vigilancia, pero tampoco la deja sondeando para siempre:
 * suma al tiempo seguido con problemas y, a los [NO_SERVICE_STOP_MS], el motor se para y lo notifica.
 */
fun failedTick(troubleSinceMs: Long?, nowMs: Long): FailedTick {
    val since = troubleSinceMs ?: nowMs
    val action = if (nowMs - since >= NO_SERVICE_STOP_MS) EngineAction.STOP_NO_SERVICE else EngineAction.CONTINUE
    return FailedTick(since, action)
}

/**
 * Avisos disparados que se pueden borrar ya notificados. Si el usuario no puede ver la
 * notificación ([canNotify] false: permiso denegado o canal bloqueado), no se consume el aviso.
 */
fun firingsToDelete(firings: List<Firing>, canNotify: Boolean): List<Firing> = if (canNotify) firings else emptyList()

/**
 * Tras decidir parar y soltar `running`, el servicio vuelve a leer la BD: si entretanto se
 * guardó un aviso, `ensureMonitoring` vio el servicio vivo y no lo arrancó, así que hay que
 * seguir (H15). Solo tras [EngineAction.STOP_IDLE]: sin servicio los avisos siguen ahí a
 * propósito, y sin permiso de notificaciones ([canNotify] false) el disparado no se borra
 * y reanudar sería un bucle.
 */
fun resumeAfterStop(
    action: EngineAction,
    rules: List<AlertRule>,
    nowMs: Long,
    canNotify: Boolean,
    alertsConfig: AlertsConfig = AlertsConfig(),
): Boolean =
    action == EngineAction.STOP_IDLE && canNotify && rules.any { isWatching(it, nowMs, alertsConfig) }
