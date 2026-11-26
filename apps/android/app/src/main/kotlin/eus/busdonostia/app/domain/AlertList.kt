package eus.busdonostia.app.domain

/*
 * Estado calculado de los avisos para A01 (próximo aviso), A04 (lista) y el badge.
 * Todos pasan por `computeAlertStatus` (00-agent-guardrails.md §3).
 */

/** Por qué un aviso está `INACTIVE` (subetiqueta de A04). */
enum class AlertDetail {
    NONE,

    /** `active = false`. */
    PAUSED,

    /** Activo con más de `expirationDays` días sin avisar: caducado. */
    EXPIRED,
}

data class AlertItem(
    val rule: AlertRule,
    val status: ComputedAlertStatus,
    val detail: AlertDetail,
    /** Próxima llegada de la línea (y destino) en la parada, la haya o no dentro de la antelación. */
    val nextArrival: Arrival?,
    /** Minutos de [nextArrival] ahora; null si no hay. */
    val remainingMin: Int?,
)

/**
 * Próxima llegada para pintar: la de menos minutos de [lineId] (y [destination] si
 * está fijado); a igualdad, `LIVE` antes que `SCHEDULED`.
 */
fun nextArrivalFor(arrivals: List<Arrival>, lineId: String, destination: String?, nowMs: Long): Arrival? {
    val wanted = normalizeLine(lineId)
    val filterDestination = destination?.takeIf { !destinationsMatch(it, UNKNOWN_DESTINATION) }
    return arrivals
        .filter {
            normalizeLine(it.lineId) == wanted &&
                (filterDestination == null || destinationsMatch(it.destination, filterDestination))
        }
        .minWithOrNull(
            compareBy<Arrival> { remainingMinutes(it, nowMs) }.thenBy { if (it.source == ArrivalSource.LIVE) 0 else 1 },
        )
}

/** Llegadas válidas que se pueden usar ahora (vivas o viejas pero no caducadas), o vacía. */
fun usableArrivals(view: ArrivalsView): List<Arrival> = when (view) {
    is ArrivalsView.Live -> view.arrivals
    is ArrivalsView.Degraded -> view.arrivals
    else -> emptyList()
}

fun alertItem(
    rule: AlertRule,
    arrivals: List<Arrival>,
    nowMs: Long,
    config: AlertsConfig = AlertsConfig(),
): AlertItem {
    val notifyEarlyMs = config.triggerBufferSec * 1_000L
    val matching = findMatchingArrival(arrivals, rule.lineId, rule.destination, rule.minutesAhead, nowMs + notifyEarlyMs)
    val status = computeAlertStatus(rule, nowMs, matching, config)
    val detail = when (status) {
        ComputedAlertStatus.INACTIVE -> if (rule.active) AlertDetail.EXPIRED else AlertDetail.PAUSED
        else -> AlertDetail.NONE
    }
    val next = matching ?: nextArrivalFor(arrivals, rule.lineId, rule.destination, nowMs)
    val evalNowMs = if (status == ComputedAlertStatus.TRIGGERED) nowMs + notifyEarlyMs else nowMs
    return AlertItem(rule, status, detail, next, next?.let { remainingMinutes(it, evalNowMs) })
}

private fun ComputedAlertStatus.priority(): Int = when (this) {
    ComputedAlertStatus.TRIGGERED -> 0
    ComputedAlertStatus.ACTIVE -> 1
    ComputedAlertStatus.INACTIVE -> 2
}

/** Orden de §3: TRIGGERED, ACTIVE (menos minutos primero, sin llegada al final), INACTIVE. */
fun sortAlerts(items: List<AlertItem>): List<AlertItem> =
    items.sortedWith(
        compareBy<AlertItem> { it.status.priority() }
            .thenBy { it.remainingMin ?: Int.MAX_VALUE }
            .thenBy { it.rule.createdAtMs },
    )

fun isActiveGroup(status: ComputedAlertStatus): Boolean =
    status == ComputedAlertStatus.TRIGGERED || status == ComputedAlertStatus.ACTIVE

/** A04: ACTIVOS (`TRIGGERED`, `ACTIVE`) y PAUSADOS (`INACTIVE`), ya ordenados. */
fun groupAlerts(items: List<AlertItem>): Pair<List<AlertItem>, List<AlertItem>> =
    sortAlerts(items).partition { isActiveGroup(it.status) }

/** A01 · `TU PRÓXIMO AVISO`: el primero de los activos según §3, o null. */
fun nextAlert(items: List<AlertItem>): AlertItem? = groupAlerts(items).first.firstOrNull()

/** Badge de la pestaña Avisos: cuántos están `ACTIVE` o `TRIGGERED`. */
fun alertBadgeCount(items: List<AlertItem>): Int =
    items.count { it.status == ComputedAlertStatus.ACTIVE || it.status == ComputedAlertStatus.TRIGGERED }

/** Texto del badge: null sin badge, `maxBadgeCount+` por encima de `maxBadgeCount`. */
fun badgeText(count: Int, config: AlertsConfig = AlertsConfig()): String? = when {
    count <= 0 -> null
    count > config.maxBadgeCount -> "${config.maxBadgeCount}+"
    else -> count.toString()
}

/** Línea de estado del próximo aviso (A01) bajo la cuenta atrás. La UI la traduce. */
sealed interface NextAlertSource {
    /** `En vivo · Aviso 5'` u `Horario · Aviso 5'`. */
    data class Known(val source: ArrivalSource) : NextAlertSource

    /** Aún sin respuesta de la parada: solo `Aviso 5'`. */
    data object Waiting : NextAlertSource

    data object FeedError : NextAlertSource

    data object NoService : NextAlertSource

    /** La parada responde, pero sin bus de la línea. */
    data object NoArrivals : NextAlertSource
}

fun nextAlertSource(item: AlertItem, view: ArrivalsView?): NextAlertSource {
    item.nextArrival?.let { return NextAlertSource.Known(it.source) }
    return when {
        view == null || view == ArrivalsView.Loading -> NextAlertSource.Waiting
        view == ArrivalsView.Error -> NextAlertSource.FeedError
        view is ArrivalsView.Degraded && view.reason == DegradedReason.NO_SERVICE -> NextAlertSource.NoService
        else -> NextAlertSource.NoArrivals
    }
}

/** Pista bajo un aviso de A04 sobre su parada: sin servicio, error o datos viejos. */
sealed interface FeedHint {
    data object FeedError : FeedHint

    data object NoService : FeedHint

    /** Datos viejos: se enseña la hora real del dato. */
    data class Stale(val fetchedAtMs: Long) : FeedHint
}

/** null si el aviso está pausado o no hay nada que decir de su parada. */
fun feedHint(rule: AlertRule, view: ArrivalsView?): FeedHint? {
    if (!rule.active) return null
    return when (view) {
        ArrivalsView.Error -> FeedHint.FeedError
        is ArrivalsView.Degraded ->
            if (view.reason == DegradedReason.NO_SERVICE) FeedHint.NoService else FeedHint.Stale(view.fetchedAtMs)
        else -> null
    }
}
