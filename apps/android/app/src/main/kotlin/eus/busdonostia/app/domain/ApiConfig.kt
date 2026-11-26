package eus.busdonostia.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class AdaptivePollTier(
    val minGapMin: Int,
    val delaySec: Int,
)

@Serializable
data class PollingConfig(
    val uiTickerSec: Int = 30,
    val foregroundVisibleSec: Int = 30,
    val baseBackgroundSec: Int = 30,
    val noServiceOrErrorMultiplier: Int = 2,
    /** Tras estos minutos seguidos sin servicio o con errores en todas las paradas, el motor se para y lo notifica. */
    val noServiceStopMin: Int = 60,
    /** Margen para no repetir una consulta que la UI acaba de hacer (sondeo de 30 s con algo de retraso). */
    val fetchSlackSec: Int = 5,
    val adaptiveTiers: List<AdaptivePollTier> = listOf(
        AdaptivePollTier(minGapMin = 31, delaySec = 600),
        AdaptivePollTier(minGapMin = 15, delaySec = 240),
        AdaptivePollTier(minGapMin = 8, delaySec = 60),
        AdaptivePollTier(minGapMin = 0, delaySec = 30),
    ),
)

@Serializable
data class FreshnessConfig(
    val freshMaxSec: Int = 45,
    val staleMaxSec: Int = 90,
    val liveFeedHistorySize: Int = 3,
)

@Serializable
data class AlertsConfig(
    val triggerBufferSec: Int = 0,
    val expirationDays: Int = 7,
    val minMinutesAhead: Int = 1,
    val maxMinutesAhead: Int = 30,
    val maxBadgeCount: Int = 9,
    /**
     * Tope de vigilancia continua (A5-09): pasado este tiempo desde que se guardó el aviso
     * sin un bus que coincida, el motor deja de vigilarlo, lo notifica y lo elimina.
     * Evita sondear hasta 7 días una línea que no aparece (p. ej. de noche).
     */
    val maxWatchHours: Int = 3,
    /** Tiempo máximo que suena un aviso en modo alarma; pasado, se silencia solo. */
    val alarmTimeoutSec: Int = 60,
)

@Serializable
data class GeoConfig(
    val walkingSpeedKmh: Double = 4.8,
    /** Antigüedad máxima de la última posición del sistema para usarla como atajo. */
    val lastKnownMaxAgeSec: Int = 120,
    /** Espera máxima por proveedor antes de probar el siguiente. */
    val locationTimeoutSec: Int = 10,
)

@Serializable
data class UiLimitsConfig(
    val homeFavoriteMaxArrivals: Int = 3,
    val homeSearchLimit: Int = 6,
    val searchStopsLimit: Int = 50,
    val nearbyStopsLimit: Int = 10,
    /** Paradas recientes que se conservan (A02 · Recientes). */
    val recentStopsLimit: Int = 10,
)
