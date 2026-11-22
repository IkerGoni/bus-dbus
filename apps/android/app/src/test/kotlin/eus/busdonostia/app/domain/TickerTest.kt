package eus.busdonostia.app.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TickerTest {
    @Test
    fun lateCadaSegundoConElRelojInyectado() = runTest {
        val ticker = Ticker(backgroundScope, nowMs = { testScheduler.currentTime })
        val seen = mutableListOf<Instant>()
        backgroundScope.launch { ticker.now.collect { seen += it } }
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), seen.map { it.toEpochMilli() })
    }

    @Test
    fun sinObservadoresNoLate() = runTest {
        var calls = 0
        Ticker(backgroundScope, nowMs = { calls++; testScheduler.currentTime })
        advanceTimeBy(10_000)
        assertEquals(1, calls) // solo el valor inicial
    }

    @Test
    fun elMinutoBajaConElReloj() = runTest {
        // remainingMinutes se recalcula en cada tick; solo cambia al cruzar el minuto.
        val arrival = Arrival("28", "32", "Ospitaleak", 2, null, ArrivalSource.LIVE, 0, null)
        val ticker = Ticker(backgroundScope, nowMs = { testScheduler.currentTime })
        val minutes = mutableListOf<Int>()
        backgroundScope.launch { ticker.now.collect { minutes += remainingMinutes(arrival, it.toEpochMilli()) } }
        runCurrent()
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(listOf(2, 1), minutes.distinct())
        assertEquals(60, minutes.count { it == 2 })
    }
}
