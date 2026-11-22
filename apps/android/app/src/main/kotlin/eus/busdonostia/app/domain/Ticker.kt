package eus.busdonostia.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Reloj reactivo (epoch ms UTC) cada `polling.uiTickerSec`. Solo sirve para recalcular
 * [remainingMinutes] y [computeFreshness]; la UI nunca pinta segundos. Solo late mientras
 * alguien lo observa. [nowMs] se inyecta siempre (KMP: sin `System` en `domain/`; en tests,
 * el tiempo virtual del scheduler).
 */
class Ticker(
    scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val periodMs: () -> Long = { PollingConfig().uiTickerSec * 1_000L },
) {
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(nowMs())
            delay(periodMs().coerceAtLeast(1_000L))
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
        initialValue = nowMs(),
    )
}
