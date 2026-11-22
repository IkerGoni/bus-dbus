package eus.busdonostia.app.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil

/*
 * Búsqueda offline y cercanía sobre el catálogo (paridad con `searchKey`,
 * `searchStops`, `searchLines` e `getItinerariesForLine` de `packages/core/src/catalog.ts`).
 */

private val MARKS = Regex("\\p{M}+")
private val SPACES = Regex("\\s+")
private val ES = Locale.forLanguageTag("es")

/** Clave de búsqueda sin acentos, mayúsculas ni espacios duplicados (incluye `normalizeLine`, como en TS). */
fun searchKey(value: String): String =
    Normalizer.normalize(normalizeLine(value), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase(ES)
        .replace(SPACES, " ")
        .trim()

private val DIGITS = Regex("^\\d+$")

/** Mejor primero; empate → orden del catálogo (sort estable, como `Array.sort` en JS). */
private fun <T> best(items: List<T>, limit: Int, rank: (T) -> Int?): List<T> =
    items.mapNotNull { item -> rank(item)?.let { item to it } }
        .sortedBy { it.second }
        .take(limit)
        .map { it.first }

/** Paradas por código exacto (0), código parcial numérico (1), nombre que empieza (2) o contiene (3). */
fun searchStops(catalog: Catalog, query: String, limit: Int = 8): List<CatalogStop> {
    val q = searchKey(query)
    if (q.isEmpty()) return emptyList()
    val numeric = DIGITS.matches(q)
    return best(catalog.stops, limit) { stop ->
        val cod = searchKey(stop.cod)
        val desc = searchKey(stop.desc)
        when {
            cod == q -> 0
            numeric && cod.startsWith(q) -> 1
            desc.startsWith(q) -> 2
            desc.contains(q) -> 3
            else -> null
        }
    }
}

/** Líneas por código público (0), id interno (1), prefijo de cualquiera (2) o destino (3): `32` → gana la pública. */
fun searchLines(catalog: Catalog, query: String, limit: Int = 8): List<CatalogLine> {
    val q = searchKey(query)
    if (q.isEmpty()) return emptyList()
    return best(catalog.lines, limit) { line ->
        val publicCode = searchKey(line.cod)
        val internalId = searchKey(line.id)
        when {
            publicCode == q -> 0
            internalId == q -> 1
            publicCode.startsWith(q) || internalId.startsWith(q) -> 2
            line.destinations.any { searchKey(it).contains(q) } -> 3
            else -> null
        }
    }
}

/** Itinerarios reales de una línea (código público o alias interno). */
fun Catalog.itinerariesForLine(lineId: String): List<CatalogItinerary> {
    val line = lineByCode(lineId) ?: return emptyList()
    return itineraries.filter { it.lineId == line.id }
}

private val LINE_NUMBER = Regex("\\d+")

/** Orden de listado: diurnas por número y después nocturnas (`B1`…`B10`). */
fun sortLines(lines: List<CatalogLine>): List<CatalogLine> =
    lines.sortedWith(
        compareBy<CatalogLine> { isNightLine(it.cod) }
            .thenBy { LINE_NUMBER.find(it.cod)?.value?.toIntOrNull() ?: Int.MAX_VALUE }
            .thenBy { it.cod },
    )

/** Velocidad a pie para la estimación en línea recta (`140 m · 2 min`). */
const val WALK_METERS_PER_MIN = 80.0

/** Minutos a pie en línea recta, redondeando hacia arriba (mínimo 1). */
fun walkMinutes(meters: Long): Int = maxOf(1, ceil(meters / WALK_METERS_PER_MIN).toInt())

data class NearbyStop(val stop: CatalogStop, val meters: Long, val walkMinutes: Int)

/** Las [limit] paradas más cercanas; solo las que tienen coordenadas (514 de 548). Sin red. */
fun nearbyStops(catalog: Catalog, lat: Double, lon: Double, limit: Int = 20): List<NearbyStop> =
    catalog.stops.mapNotNull { stop ->
        val sLat = stop.lat ?: return@mapNotNull null
        val sLon = stop.lon ?: return@mapNotNull null
        val meters = haversineMeters(lat, lon, sLat, sLon)
        NearbyStop(stop, meters, walkMinutes(meters))
    }.sortedBy { it.meters }.take(limit)
