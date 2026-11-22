package eus.busdonostia.app.domain

/** Con refresco cada 30 s, FRESH ≤ 45 s deja margen para la latencia; STALE ≤ 90 s. */
fun computeFreshness(fetchedAtMs: Long, nowMs: Long): FreshnessLevel {
    val ageSec = maxOf(0L, Math.floorDiv(nowMs - fetchedAtMs, 1_000L))
    return when {
        ageSec <= 45 -> FreshnessLevel.FRESH
        ageSec <= 90 -> FreshnessLevel.STALE
        else -> FreshnessLevel.EXPIRED
    }
}
