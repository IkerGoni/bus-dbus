package eus.busdonostia.app.domain

/*
 * Identidad de línea (regla 5 de AGENTS.md; paridad con `packages/core/src/lines.ts`
 * y `catalog.ts`). Manda el código público ("28"); el id interno ("32") es
 * alias de entrada. "32" también es línea pública: en búsquedas gana la pública.
 */

private val LEADING_ZEROS = Regex("^([A-Z]*)0+(?=\\d)")

/** Acepta `28`, `L28`, `028`, `b09` → `28`, `28`, `28`, `B9`. */
fun normalizeLine(lineId: String): String =
    lineId.trim().uppercase().removePrefix("L").replace(LEADING_ZEROS, "$1")

/** Código público o id interno; si coinciden ambos, gana el público. */
fun Catalog.lineByCode(lineId: String): CatalogLine? {
    val key = normalizeLine(lineId)
    return lines.firstOrNull { normalizeLine(it.cod) == key }
        ?: lines.firstOrNull { normalizeLine(it.id) == key }
}

fun Catalog.stopByCode(stopId: String): CatalogStop? {
    val key = stopId.trim()
    return stops.firstOrNull { it.cod == key }
}

/**
 * Código público de una línea. En contexto de parada manda la línea pública que
 * realmente pasa por ella: en la parada `1` (líneas 21, 28, B4, B9) `32` → `28`;
 * sin parada, `32` es la línea pública 32.
 */
fun Catalog.toPublicLineId(lineId: String, stopId: String? = null): String {
    val key = normalizeLine(lineId)
    if (stopId != null) {
        val atStop = stopByCode(stopId)?.lines?.map(::normalizeLine).orEmpty()
        if (key in atStop) return key
        val byInternal = atStop.firstNotNullOfOrNull { code ->
            lineByCode(code)?.takeIf { normalizeLine(it.id) == key }
        }
        if (byInternal != null) return normalizeLine(byInternal.cod)
    }
    return lineByCode(key)?.cod?.let(::normalizeLine) ?: key
}

/** Líneas nocturnas (Búho): código que empieza por "B". */
fun isNightLine(lineId: String): Boolean = normalizeLine(lineId).startsWith("B")
