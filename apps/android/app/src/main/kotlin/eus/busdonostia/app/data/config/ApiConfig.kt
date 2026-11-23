package eus.busdonostia.app.data.config

import eus.busdonostia.app.domain.AlertsConfig
import eus.busdonostia.app.domain.FreshnessConfig
import eus.busdonostia.app.domain.GeoConfig
import eus.busdonostia.app.domain.PollingConfig
import eus.busdonostia.app.domain.UiLimitsConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Configuración operativa y de la API de Dbus (ADR 009 § Configuración remota, `schemaVersion` 2).
 * La copia por defecto va en `assets/config.json`.
 */
@Serializable
data class ApiConfig(
    val schemaVersion: Int,
    val apiBaseUrl: String,
    val endpoints: Endpoints,
    val params: Params,
    val headers: Map<String, String> = emptyMap(),
    val pollIntervalSec: Int = 30,
    val connectTimeoutSec: Int = 10,
    val callTimeoutSec: Int = 15,
    val remoteConfigCacheHours: Int = 12,
    val remoteConfigTimeoutSec: Int = 5,
    val polling: PollingConfig = PollingConfig(),
    val freshness: FreshnessConfig = FreshnessConfig(),
    val alerts: AlertsConfig = AlertsConfig(),
    val geo: GeoConfig = GeoConfig(),
    val uiLimits: UiLimitsConfig = UiLimitsConfig(),
    val minAppVersion: Int = 1,
    val notice: Notice? = null,
) {
    @Serializable
    data class Endpoints(val tiemposParada: String)

    @Serializable
    data class Params(
        val stopCode: String,
        val language: String,
    )

    /** Aviso de incidencia opcional, en los dos idiomas de la app. */
    @Serializable
    data class Notice(val es: String, val eu: String)

    companion object {
        const val SCHEMA_VERSION = 2
        const val MIN_POLL_INTERVAL_SEC = 20
        const val MAX_POLL_INTERVAL_SEC = 300

        /** Único host al que se permite HTTP (también en `network_security_config.xml`). */
        const val CLEARTEXT_HOST = "app.dbus.eus"

        val ALLOWED_API_HOSTS = setOf(CLEARTEXT_HOST)

        val ALLOWED_HEADERS = setOf("accept", "accept-language", "user-agent", "referer", "origin", "x-requested-with")

        private val json = Json { ignoreUnknownKeys = true }

        /** Config base en memoria para arrancar sin I/O en el Main Thread antes de leer `assets/config.json`. */
        fun default(): ApiConfig = ApiConfig(
            schemaVersion = SCHEMA_VERSION,
            apiBaseUrl = "http://$CLEARTEXT_HOST/SSIIMovilWSv2/ws/cons",
            endpoints = Endpoints("/tiemposParada"),
            params = Params(stopCode = "codParada", language = "idioma"),
        )

        /** Parsea y valida; null si el JSON no es válido o no pasa [validate]. */
        fun parse(text: String): ApiConfig? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
                ?.takeIf { it.validate().isEmpty() }
    }

    /** Errores de esquema y rangos; vacío = válida. [allowedHosts]: hosts permitidos para `apiBaseUrl`. */
    fun validate(allowedHosts: Set<String> = ALLOWED_API_HOSTS): List<String> = buildList {
        if (schemaVersion != SCHEMA_VERSION) add("schemaVersion $schemaVersion no soportado")
        val base = apiBaseUrl.toHttpUrlOrNull()
        when {
            base == null -> add("apiBaseUrl inválida")
            base.host !in allowedHosts -> add("apiBaseUrl: host ${base.host} no permitido")
            !base.isHttps && base.host != CLEARTEXT_HOST -> add("apiBaseUrl HTTP solo para $CLEARTEXT_HOST")
        }
        if (!endpoints.tiemposParada.startsWith("/")) add("endpoints.tiemposParada debe empezar por /")
        if (params.stopCode.isBlank() || params.language.isBlank()) add("params vacíos")
        if (headers.keys.any { it.isBlank() }) add("cabecera sin nombre")
        headers.keys.filter { it.isNotBlank() && it.lowercase() !in ALLOWED_HEADERS }.forEach { add("cabecera $it no permitida") }
        if (headers.values.any { v -> v.any { it == '\r' || it == '\n' } }) add("cabecera con salto de línea")
        if (pollIntervalSec !in MIN_POLL_INTERVAL_SEC..MAX_POLL_INTERVAL_SEC) add("pollIntervalSec fuera de rango")
        if (connectTimeoutSec !in 1..60) add("connectTimeoutSec fuera de rango")
        if (callTimeoutSec !in 1..60) add("callTimeoutSec fuera de rango")
        if (remoteConfigCacheHours !in 1..168) add("remoteConfigCacheHours fuera de rango")
        if (remoteConfigTimeoutSec !in 1..30) add("remoteConfigTimeoutSec fuera de rango")

        if (polling.uiTickerSec !in 5..120) add("polling.uiTickerSec fuera de rango")
        if (polling.foregroundVisibleSec !in 15..120) add("polling.foregroundVisibleSec fuera de rango")
        if (polling.baseBackgroundSec !in 15..120) add("polling.baseBackgroundSec fuera de rango")
        if (polling.noServiceOrErrorMultiplier !in 1..10) add("polling.noServiceOrErrorMultiplier fuera de rango")
        if (polling.noServiceStopMin !in 5..360) add("polling.noServiceStopMin fuera de rango")
        if (polling.fetchSlackSec !in 0..30) add("polling.fetchSlackSec fuera de rango")
        if (polling.adaptiveTiers.isEmpty()) {
            add("polling.adaptiveTiers vacío")
        } else {
            if (polling.adaptiveTiers.any { it.delaySec < 15 || it.minGapMin < 0 }) {
                add("polling.adaptiveTiers con valores fuera de rango")
            }
            val strictlyDescending = polling.adaptiveTiers
                .zipWithNext()
                .all { (prev, next) -> prev.minGapMin > next.minGapMin }
            if (!strictlyDescending) {
                add("polling.adaptiveTiers debe estar ordenado descendentemente por minGapMin")
            }
        }

        if (freshness.freshMaxSec < 10 || freshness.staleMaxSec <= freshness.freshMaxSec) {
            add("freshness umbrales inválidos")
        }
        if (freshness.liveFeedHistorySize !in 1..20) add("freshness.liveFeedHistorySize fuera de rango")

        if (alerts.triggerBufferSec !in 0..120) add("alerts.triggerBufferSec fuera de rango")
        if (alerts.expirationDays !in 1..30) add("alerts.expirationDays fuera de rango")
        if (alerts.minMinutesAhead < 1 || alerts.minMinutesAhead >= alerts.maxMinutesAhead || alerts.maxMinutesAhead > 120) {
            add("alerts rango minutesAhead inválido")
        }
        if (alerts.maxBadgeCount !in 1..99) add("alerts.maxBadgeCount fuera de rango")
        if (alerts.maxWatchHours !in 1..24) add("alerts.maxWatchHours fuera de rango")

        if (geo.walkingSpeedKmh !in 1.0..15.0) add("geo.walkingSpeedKmh fuera de rango")
        if (geo.lastKnownMaxAgeSec !in 10..600) add("geo.lastKnownMaxAgeSec fuera de rango")
        if (geo.locationTimeoutSec !in 1..60) add("geo.locationTimeoutSec fuera de rango")
        if (uiLimits.homeFavoriteMaxArrivals !in 1..20) add("uiLimits.homeFavoriteMaxArrivals fuera de rango")
        if (uiLimits.homeSearchLimit !in 1..50) add("uiLimits.homeSearchLimit fuera de rango")
        if (uiLimits.searchStopsLimit !in 1..200) add("uiLimits.searchStopsLimit fuera de rango")
        if (uiLimits.nearbyStopsLimit !in 1..50) add("uiLimits.nearbyStopsLimit fuera de rango")
        if (uiLimits.recentStopsLimit !in 1..50) add("uiLimits.recentStopsLimit fuera de rango")

        if (minAppVersion < 1) add("minAppVersion < 1")
        if (notice != null && (notice.es.isBlank() || notice.eu.isBlank())) add("notice incompleto")
    }
}
