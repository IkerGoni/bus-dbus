package eus.busdonostia.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/**
 * Reloj reactivo de 1 s. Solo sirve para recalcular [remainingMinutes] y
 * [computeFreshness]; la UI nunca pinta segundos. Solo late mientras alguien lo
 * observa. En tests se inyecta [nowMs] (p. ej. el tiempo virtual del scheduler).
 */
class Ticker(
    scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
    periodMs: Long = 1_000L,
) {
    val now: StateFlow<Instant> = flow {
        while (true) {
            emit(Instant.ofEpochMilli(nowMs()))
            delay(periodMs)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000L), Instant.ofEpochMilli(nowMs()))
}
