package eus.busdonostia.app.domain

/** Frescura de una respuesta según `freshness` de la config: FRESH ≤ `freshMaxSec`, STALE ≤ `staleMaxSec`, si no EXPIRED. */
fun computeFreshness(
    fetchedAtMs: Long,
    nowMs: Long,
    config: FreshnessConfig = FreshnessConfig(),
): FreshnessLevel {
    val ageSec = maxOf(0L, (nowMs - fetchedAtMs) / 1_000L)
    return when {
        ageSec <= config.freshMaxSec -> FreshnessLevel.FRESH
        ageSec <= config.staleMaxSec -> FreshnessLevel.STALE
        else -> FreshnessLevel.EXPIRED
    }
}
