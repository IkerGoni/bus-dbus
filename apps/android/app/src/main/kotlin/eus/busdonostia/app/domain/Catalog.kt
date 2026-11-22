package eus.busdonostia.app.domain

import kotlinx.serialization.Serializable

/*
 * Catálogo offline: `assets/catalog.json`, generado en cada build por la tarea
 * `merge<Variant>Catalog` a partir de `apps/bot/data/`. Índice de
 * descubrimiento (nombre, código, destinos, itinerarios), nunca horarios.
 */

@Serializable
data class Catalog(
    val schemaVersion: Int,
    val generatedAt: String,
    val source: String,
    val attribution: String,
    val coordinates: CatalogCoordinatesMeta,
    val lines: List<CatalogLine>,
    val itineraries: List<CatalogItinerary>,
    val stops: List<CatalogStop>,
)

@Serializable
data class CatalogCoordinatesMeta(
    val generatedAt: String,
    val source: String,
    /** `© OpenStreetMap contributors, ODbL`: atribución visible obligatoria. */
    val attribution: String,
)

@Serializable
data class CatalogLine(
    /** Id interno de Dbus ("32"). Solo alias de entrada. */
    val id: String,
    /** Código público ("28"). Manda. */
    val cod: String,
    val destinations: List<String>,
    val stops: List<String>,
)

@Serializable
data class CatalogItinerary(
    val id: String,
    /** Id interno de la línea. */
    val lineId: String,
    /** Código público de la línea. */
    val lineCod: String,
    val destination: String,
    /** Orden secuencial de paradas (códigos públicos). */
    val stops: List<String>,
)

@Serializable
data class CatalogStop(
    /** Código público de marquesina = `codParada`. */
    val cod: String,
    val desc: String,
    /** Códigos públicos de línea. */
    val lines: List<String>,
    val destinations: List<String>,
    /** OSM: 514 de 548 paradas. Sin coordenadas → fuera de «Cercanas» y sin «Cómo llegar». */
    val lat: Double? = null,
    val lon: Double? = null,
)
