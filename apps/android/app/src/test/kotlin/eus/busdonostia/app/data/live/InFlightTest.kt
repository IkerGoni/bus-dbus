package eus.busdonostia.app.data.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Peticiones en vuelo por parada (H16): como `inFlight` de `packages/core/src/client.ts`. */
class InFlightTest {
    @Test
    fun mismaParadaEnVueloUnaSolaPeticion() = runTest {
        val inFlight = InFlight<String, Int>(backgroundScope)
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val call: suspend () -> Int = {
            calls++
            gate.await()
            calls
        }
        val a = async { inFlight.run("2", call) }
        val b = async { inFlight.run("2", call) }
        val other = async { inFlight.run("340", call) }
        runCurrent()
        assertEquals(2, calls) // una para la 2 y otra para la 340
        gate.complete(Unit)
        assertEquals(a.await(), b.await())
        other.await()
        // Terminada, la siguiente vuelve a pedir: no es una caché.
        inFlight.run("2", call)
        assertEquals(3, calls)
    }

    @Test
    fun cancelarAUnoNoCancelaLaPeticionDeLosDemas() = runTest {
        val inFlight = InFlight<String, String>(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        val call: suspend () -> String = {
            gate.await()
            "ok"
        }
        val leaving = async { inFlight.run("2", call) }
        val staying = async { inFlight.run("2", call) }
        runCurrent()
        leaving.cancel() // p. ej. se cierra A05 mientras el motor espera la misma parada
        runCurrent()
        gate.complete(Unit)
        assertEquals("ok", staying.await())
        assertTrue(leaving.isCancelled)
    }
}
