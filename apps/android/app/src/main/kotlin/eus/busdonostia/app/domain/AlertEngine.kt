package eus.busdonostia.app.domain

/*
 * Motor de avisos (04-android-platform.md §1 y 04-background-bateria-y-recurrentes.md):
 * decisión pura de cada vuelta del Foreground Service. El servicio solo consulta,
 * borra lo que avisa y notifica.
 */

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
fun isFetchDue(
    lastFetchedAtMs: Long?,
    nowMs: Long,
    intervalMs: Long,
    config: PollingConfig = PollingConfig(),
): Boolean = lastFetchedAtMs == null || nowMs - lastFetchedAtMs >= intervalMs - config.fetchSlackSec * 1_000L

/**
 * Respuesta que el sondeo de la UI puede reutilizar en vez de pedir otra (H16): la de
 * alguien (el motor) que consultó la parada hace menos de un intervalo; null = hay que pedir.
 */
fun reusableFeed(known: StopFeed?, nowMs: Long, intervalMs: Long, config: PollingConfig = PollingConfig()): StopFeed? =
    known?.takeUnless { isFetchDue(it.fetchedAtMs, nowMs, intervalMs, config) }

/** Aviso que acaba de pasar a `TRIGGERED` y la llegada que lo dispara: se notifica y se borra (un solo uso). */
data class Firing(val rule: AlertRule, val arrival: Arrival)

enum class EngineAction {
    CONTINUE,

    /** No queda ningún aviso en vigilancia. */
    STOP_IDLE,

    /** `polling.noServiceStopMin` seguidos sin servicio ni datos: se para y se notifica. */
    STOP_NO_SERVICE,
}

data class EngineTick(
    val firings: List<Firing>,
    val action: EngineAction,
    /** Desde cuándo todas las paradas consultadas están sin servicio o con error; null si alguna responde. */
    val troubleSinceMs: Long?,
    /** Avisos en vigilancia tras esta vuelta (notificación «Vigilando …»). */
    val watching: List<AlertRule>,
    /**
     * Avisos que superan `alerts.maxWatchHours` sin un bus que coincida (A5-09): se deja de
     * vigilarlos, se notifica y se eliminan. No están en [watching].
     */
    val watchExpired: List<AlertRule> = emptyList(),
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
    pollingConfig: PollingConfig = PollingConfig(),
): EngineTick {
    val firings = rules.mapNotNull { rule ->
        val item = alertItem(rule, usableArrivals(views[rule.stopId] ?: ArrivalsView.Loading), nowMs, alertsConfig)
        val arrival = item.nextArrival
        if (item.status == ComputedAlertStatus.TRIGGERED && arrival != null) Firing(rule, arrival) else null
    }
    val fired = firings.mapTo(HashSet()) { it.rule.id }
    val candidates = rules.filter { it.id !in fired && isWatching(it, nowMs, alertsConfig) }

    // Tope de vigilancia (A5-09): N horas desde que se guardó sin un bus que coincida ahora.
    // Si la línea sí tiene llegadas (aunque lejos), se sigue vigilando: el aviso es legítimo.
    val maxWatchMs = alertsConfig.maxWatchHours * 3_600_000L
    val (watchExpired, watching) = candidates.partition { rule ->
        nowMs - rule.createdAtMs >= maxWatchMs &&
            nextArrivalFor(usableArrivals(views[rule.stopId] ?: ArrivalsView.Loading), rule.lineId, rule.destination, nowMs) == null
    }

    val polled = stopsToPoll(rules, nowMs, alertsConfig).mapNotNull { views[it] }.filter { it != ArrivalsView.Loading }
    val trouble = when {
        polled.isEmpty() -> troubleSinceMs // nada consultado todavía: no cuenta ni a favor ni en contra
        polled.all(::isTrouble) -> troubleSinceMs ?: nowMs
        else -> null
    }
    val action = when {
        watching.isEmpty() -> EngineAction.STOP_IDLE
        trouble != null && nowMs - trouble >= pollingConfig.noServiceStopMin * 60_000L -> EngineAction.STOP_NO_SERVICE
        else -> EngineAction.CONTINUE
    }
    return EngineTick(firings, action, trouble, watching, watchExpired)
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
 * suma al tiempo seguido con problemas y, a los `polling.noServiceStopMin`, el motor se para y lo notifica.
 */
fun failedTick(
    troubleSinceMs: Long?,
    nowMs: Long,
    pollingConfig: PollingConfig = PollingConfig(),
): FailedTick {
    val since = troubleSinceMs ?: nowMs
    val action = if (nowMs - since >= pollingConfig.noServiceStopMin * 60_000L) EngineAction.STOP_NO_SERVICE else EngineAction.CONTINUE
    return FailedTick(since, action)
}

/**
 * Avisos disparados que se pueden borrar ya notificados. Si el usuario no puede ver la
 * notificación ([canNotify] devuelve false: permiso denegado o canal bloqueado), no se consume el aviso.
 */
fun firingsToDelete(firings: List<Firing>, canNotify: (Firing) -> Boolean): List<Firing> = firings.filter(canNotify)

fun firingsToDelete(firings: List<Firing>, canNotify: Boolean): List<Firing> = firingsToDelete(firings) { canNotify }

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
): Boolean = resumeAfterStop(
    action = action,
    rules = rules,
    nowMs = nowMs,
    canNotify = { canNotify },
    skipRuleIds = emptySet(),
    alertsConfig = alertsConfig,
)

fun resumeAfterStop(
    action: EngineAction,
    rules: List<AlertRule>,
    nowMs: Long,
    canNotify: (AlertRule) -> Boolean,
    skipRuleIds: Set<String> = emptySet(),
    alertsConfig: AlertsConfig = AlertsConfig(),
): Boolean =
    action == EngineAction.STOP_IDLE && rules.any { it.id !in skipRuleIds && isWatching(it, nowMs, alertsConfig) && canNotify(it) }
