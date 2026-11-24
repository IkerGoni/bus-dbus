package eus.busdonostia.app.domain

/*
 * Modelos canónicos (docs/07-android-mvp/docs/02-domain-contracts.md §1).
 * Kotlin puro: sin dependencias de Android.
 */

/** `tipo=1` estimación GPS · `tipo=2` horario programado. */
enum class ArrivalSource { LIVE, SCHEDULED }

/**
 * OK = `DATOS` con tiempos · NO_SERVICE = `SIN_SERVICIO` (mostrar `avisos`) ·
 * EMPTY = `DATOS` sin tiempos · API_ERROR = red/HTTP/JSON inválido, `NO DESC` o estado desconocido.
 */
enum class StopFeedStatus { OK, NO_SERVICE, EMPTY, API_ERROR }

data class Arrival(
    /** Siempre código público ("28"). */
    val lineId: String,
    /** Id interno de Dbus ("32"): solo alias, nunca se muestra. */
    val internalLineId: String,
    /** Destino real de Dbus; vacío si la API no lo da (la UI lo traduce). */
    val destination: String,
    /** `minutos` de la API: entero, resolución de 1 min. */
    val minutesAtFetch: Int,
    /** `hora` de la API, "HH:mm"; null si no viene. */
    val clockTime: String?,
    val source: ArrivalSource,
    val fetchedAtMs: Long,
    /** `linea.color` de Dbus (ARGB con signo); null si no viene. */
    val colorArgb: Int?,
)

data class StopFeed(
    val status: StopFeedStatus,
    val arrivals: List<Arrival>,
    /** Texto de `avisos` de Dbus (supresiones, traslados). También puede llegar con `DATOS`. */
    val notice: String?,
    val fetchedAtMs: Long,
    /** `parada.desc` real; null si la API no lo da. */
    val stopName: String? = null,
)

enum class FavoriteType { STOP, STOP_LINE, LINE }

data class Favorite(
    val type: FavoriteType,
    val stopId: String?,
    val lineId: String?,
    val createdAtMs: Long,
) {
    /** Dedup determinista: `${type}:${stopId ?: ""}:${lineId ?: ""}`. */
    val id: String get() = favoriteId(type, stopId, lineId)
}

fun favoriteId(type: FavoriteType, stopId: String?, lineId: String?): String =
    "${type.name}:${stopId.orEmpty()}:${lineId.orEmpty()}"

enum class AlertMode { NOTIFICATION, ALARM }

/**
 * Aviso de un solo uso: vigila desde que se guarda y, al avisar, se borra.
 * Caduca a los 7 días si no llega a avisar.
 */
data class AlertRule(
    val id: String,
    val stopId: String,
    val stopName: String,
    /** Siempre código público. */
    val lineId: String,
    /** Filtro de destino (paridad con el bot). */
    val destination: String? = null,
    /** Entero 1..30. */
    val minutesAhead: Int,
    val active: Boolean = true,
    val createdAtMs: Long,
    val mode: AlertMode = AlertMode.NOTIFICATION,
)

enum class ComputedAlertStatus { INACTIVE, TRIGGERED, ACTIVE }

enum class FreshnessLevel { FRESH, STALE, EXPIRED }
