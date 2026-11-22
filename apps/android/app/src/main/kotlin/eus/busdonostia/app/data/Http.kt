package eus.busdonostia.app.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ejecuta la llamada sin bloquear y la cancela si se cancela la corrutina. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) = cont.resume(response)

            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        },
    )
}
