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
    val triggerBufferSec: Int = 30,
    val expirationDays: Int = 7,
    val minMinutesAhead: Int = 1,
    val maxMinutesAhead: Int = 30,
    val maxBadgeCount: Int = 9,
    val recurringWakeupBeforeMin: Int = 25,
    val recurringExpiryAfterMin: Int = 25,
    val recurringWindowToleranceMin: Int = 5,
)

@Serializable
data class GeoConfig(
    val walkingSpeedKmh: Double = 4.8,
)

@Serializable
data class UiLimitsConfig(
    val homeFavoriteMaxArrivals: Int = 3,
    val homeSearchLimit: Int = 6,
    val searchStopsLimit: Int = 50,
    val nearbyStopsLimit: Int = 10,
)
