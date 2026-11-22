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

    /** Activo con más de 7 días sin avisar: caducado. */
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

fun alertItem(rule: AlertRule, arrivals: List<Arrival>, nowMs: Long): AlertItem {
    // Misma anticipación que `computeAlertStatus`: el bus que entra en la antelación dentro de 30 s ya cuenta.
    val matching = findMatchingArrival(arrivals, rule.lineId, rule.destination, rule.minutesAhead, nowMs + NOTIFY_EARLY_MS)
    val status = computeAlertStatus(rule, nowMs, matching)
    val detail = when (status) {
        ComputedAlertStatus.INACTIVE -> if (rule.active) AlertDetail.EXPIRED else AlertDetail.PAUSED
        else -> AlertDetail.NONE
    }
    val next = matching ?: nextArrivalFor(arrivals, rule.lineId, rule.destination, nowMs)
    return AlertItem(rule, status, detail, next, next?.let { remainingMinutes(it, nowMs) })
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

/** Texto del badge: null sin badge, `9+` por encima de 9. */
fun badgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 9 -> "9+"
    else -> count.toString()
}
