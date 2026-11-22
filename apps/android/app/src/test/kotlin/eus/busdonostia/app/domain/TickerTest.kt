package eus.busdonostia.app.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TickerTest {
    @Test
    fun lateConElPeriodoYElRelojInyectados() = runTest {
        val ticker = Ticker(backgroundScope, nowMs = { testScheduler.currentTime }, periodMs = { 1_000L })
        val seen = mutableListOf<Long>()
        backgroundScope.launch { ticker.now.collect { seen += it } }
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), seen)
    }

    @Test
    fun porDefectoLateCada30Segundos() = runTest {
        val ticker = Ticker(backgroundScope, nowMs = { testScheduler.currentTime })
        val seen = mutableListOf<Long>()
        backgroundScope.launch { ticker.now.collect { seen += it } }
        runCurrent()
        advanceTimeBy(90_000)
        runCurrent()
        assertEquals(listOf(0L, 30_000L, 60_000L, 90_000L), seen)
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
        val ticker = Ticker(backgroundScope, nowMs = { testScheduler.currentTime }, periodMs = { 1_000L })
        val minutes = mutableListOf<Int>()
        backgroundScope.launch { ticker.now.collect { minutes += remainingMinutes(arrival, it) } }
        runCurrent()
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(listOf(2, 1), minutes.distinct())
        assertEquals(60, minutes.count { it == 2 })
    }
}
