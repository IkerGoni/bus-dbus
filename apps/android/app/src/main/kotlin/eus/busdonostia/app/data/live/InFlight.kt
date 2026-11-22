package eus.busdonostia.app.data.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * Peticiones en vuelo por clave (H16), como `inFlight` de `packages/core/src/client.ts`:
 * si la UI y el motor piden la misma parada a la vez, sale una sola petición y las dos
 * reciben su respuesta. No es una caché: al terminar, la siguiente vuelve a pedir.
 * La petición corre en [scope]: si un llamante se cancela, los demás la siguen esperando.
 */
class InFlight<K : Any, V>(private val scope: CoroutineScope) {
    private val running = ConcurrentHashMap<K, Deferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val call = running.computeIfAbsent(key) {
            scope.async(start = CoroutineStart.LAZY) { block() }
                .also { started -> started.invokeOnCompletion { running.remove(key, started) } }
        }
        call.start()
        return call.await()
    }
}
