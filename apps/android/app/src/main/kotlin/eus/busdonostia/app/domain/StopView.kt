package eus.busdonostia.app.domain

/*
 * Respuesta de `tiemposParada` + frescura → vista de estado de A05 (las 5 vistas
 * de 00-agent-guardrails.md §5). Puro: la UI solo pinta lo que sale de aquí.
 */

/** Por qué la vista es `degraded`. */
enum class DegradedReason {
    /** Datos viejos según [computeFreshness] (la última consulta falló o no se ha repetido). */
    STALE,

    /** `SIN_SERVICIO` de Dbus: estado operativo real, con su motivo en `notice` si llega. */
    NO_SERVICE,
}

sealed interface ArrivalsView {
    data object Loading : ArrivalsView

    /** `DATOS` sin llegadas. */
    data class Empty(val notice: String?) : ArrivalsView

    data class Live(val arrivals: List<Arrival>, val notice: String?, val fetchedAtMs: Long) : ArrivalsView

    /** [arrivals] vacía en `NO_SERVICE`; con datos viejos en `STALE`. */
    data class Degraded(
        val reason: DegradedReason,
        val arrivals: List<Arrival>,
        val notice: String?,
        val fetchedAtMs: Long,
    ) : ArrivalsView

    /** Sin datos válidos que enseñar (red, HTTP, JSON o `NO DESC`). */
    data object Error : ArrivalsView
}

/**
 * [latest] = último resultado de cualquier tipo (null: aún no ha respondido nada).
 * [lastValid] = última respuesta que no fue `API_ERROR`.
 *
 * Si la última consulta falla se siguen enseñando los datos válidos anteriores
 * mientras sean `FRESH` (live) o `STALE` (degraded); `EXPIRED` → error.
 * Una respuesta válida que envejece sin repetirse pasa a `degraded`, nunca se da por viva.
 */
fun stopArrivalsView(latest: StopFeed?, lastValid: StopFeed?, nowMs: Long): ArrivalsView {
    if (latest == null) return ArrivalsView.Loading
    val failed = latest.status == StopFeedStatus.API_ERROR
    val feed = (if (failed) lastValid else latest) ?: return ArrivalsView.Error
    val freshness = computeFreshness(feed.fetchedAtMs, nowMs)
    if (failed && freshness == FreshnessLevel.EXPIRED) return ArrivalsView.Error
    return when (feed.status) {
        StopFeedStatus.NO_SERVICE ->
            ArrivalsView.Degraded(DegradedReason.NO_SERVICE, emptyList(), feed.notice, feed.fetchedAtMs)
        StopFeedStatus.EMPTY -> ArrivalsView.Empty(feed.notice)
        StopFeedStatus.OK ->
            if (freshness == FreshnessLevel.FRESH) {
                ArrivalsView.Live(feed.arrivals, feed.notice, feed.fetchedAtMs)
            } else {
                ArrivalsView.Degraded(DegradedReason.STALE, feed.arrivals, feed.notice, feed.fetchedAtMs)
            }
        StopFeedStatus.API_ERROR -> ArrivalsView.Error
    }
}

/** Llegadas de una línea (código público) o todas si [lineId] es null. */
fun filterArrivals(arrivals: List<Arrival>, lineId: String?): List<Arrival> =
    if (lineId == null) arrivals else arrivals.filter { normalizeLine(it.lineId) == normalizeLine(lineId) }

/**
 * Líneas para los chips de A05: las del catálogo de la parada más las que hayan
 * llegado en vivo y el catálogo no recoja; sin duplicados, en código público.
 */
fun stopLineIds(catalog: Catalog, stopId: String, arrivals: List<Arrival>): List<String> {
    val fromCatalog = catalog.stopByCode(stopId)?.lines.orEmpty().map { catalog.toPublicLineId(it, stopId) }
    val fromLive = arrivals.map { catalog.toPublicLineId(it.lineId, stopId) }
    return (fromCatalog + fromLive).distinct()
}

/** Fila de llegada de A05 lista para pintar: minutos ya calculados y clave única para `items(key = …)`. */
data class ArrivalRow(val key: String, val arrival: Arrival, val minutes: Int)

/**
 * Filas de A05: las llegadas de la línea elegida ([filterArrivals]) con los minutos de
 * [nowMs]. Dos llamadas dentro del mismo minuto dan filas iguales, así que el estado de
 * A05 solo cambia cuando cambia un minuto mostrado o llega otra respuesta (H10).
 * Clave = línea + destino + nº de aparición: única aunque Dbus repita una llegada y
 * estable entre refrescos.
 */
fun arrivalRows(arrivals: List<Arrival>, lineId: String?, nowMs: Long): List<ArrivalRow> {
    val seen = HashMap<String, Int>()
    return filterArrivals(arrivals, lineId).map { arrival ->
        val base = "${arrival.lineId}|${arrival.destination}"
        val n = seen.merge(base, 1, Int::plus)!!
        ArrivalRow("$base|$n", arrival, remainingMinutes(arrival, nowMs))
    }
}
