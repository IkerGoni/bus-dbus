package eus.busdonostia.app.domain

/**
 * Minutos restantes para pintar. Nunca segundos: la API no da esa precisión.
 * El reloj de 1 s / 30 s solo decide cuándo baja el minuto.
 */
fun remainingMinutes(arrival: Arrival, nowMs: Long): Int {
    // Si el reloj va por detrás del fetch, el minuto no sube (el TS de referencia daría +1).
    val elapsedMs = nowMs - arrival.fetchedAtMs
    val elapsedMin = if (elapsedMs <= 0L) 0 else (elapsedMs / 60_000L).toInt()
    return maxOf(0, arrival.minutesAtFetch - elapsedMin)
}

/** Marcador del bot cuando no había destino real al crear el aviso: no filtra. */
const val UNKNOWN_DESTINATION = "Servicio programado"

private val SPACES = Regex("\\s+")

private fun normalizeDestination(value: String) = value.trim().lowercase().replace(SPACES, " ")

/** Comparación tolerante: Dbus varía el texto del destino entre respuestas. */
fun destinationsMatch(actual: String, expected: String): Boolean {
    val a = normalizeDestination(actual)
    val b = normalizeDestination(expected)
    return a.isNotEmpty() && b.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
}

/**
 * Llegada que dispara un aviso (§2; paridad con `findMatchingArrival` de `@dbus/core`):
 * primera de `lineId` (y `destination` si está fijado) con `remainingMinutes <= minutesAhead`,
 * prefiriendo `LIVE` sobre `SCHEDULED`.
 *
 * `lineId` es el código público ya resuelto con [toPublicLineId]: aquí no se acepta el
 * id interno, así `32` nunca casa con la línea pública 28 (diferencia deliberada con
 * `matchesLine` de `@dbus/core`, que acepta ambos).
 */
fun findMatchingArrival(
    arrivals: List<Arrival>,
    lineId: String,
    destination: String?,
    minutesAhead: Int,
    nowMs: Long,
): Arrival? {
    val wanted = normalizeLine(lineId)
    if (wanted.isEmpty()) return null
    val filterDestination = destination?.takeIf {
        normalizeDestination(it) != normalizeDestination(UNKNOWN_DESTINATION)
    }
    val candidates = arrivals.filter { arrival ->
        normalizeLine(arrival.lineId) == wanted &&
            remainingMinutes(arrival, nowMs) <= minutesAhead &&
            (filterDestination == null || destinationsMatch(arrival.destination, filterDestination))
    }
    val live = candidates.filter { it.source == ArrivalSource.LIVE }
    return live.ifEmpty { candidates }.minByOrNull { remainingMinutes(it, nowMs) }
}
