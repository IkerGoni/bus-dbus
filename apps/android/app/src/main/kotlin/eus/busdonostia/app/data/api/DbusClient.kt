package eus.busdonostia.app.data.api

import eus.busdonostia.app.data.Warn
import eus.busdonostia.app.data.await
import eus.busdonostia.app.data.logcatWarn
import eus.busdonostia.app.data.config.ApiConfig
import eus.busdonostia.app.domain.StopFeed
import eus.busdonostia.app.domain.StopFeedStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente de `tiemposParada`. Lee base URL, path, parámetros y cabeceras de
 * [config] en cada llamada (config remota, ADR 009). Siempre en vivo: sin caché,
 * sin mocks, sin fallbacks. Cualquier fallo de red, HTTP o JSON → `API_ERROR`.
 */
class DbusClient(
    private val http: OkHttpClient,
    private val config: () -> ApiConfig,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val warn: Warn = logcatWarn("DbusClient"),
) {
    /** [stopId] = código público de marquesina (`codParada`); [language] = `es` | `eu`. */
    suspend fun tiemposParada(stopId: String, language: String): StopFeed {
        val cfg = config()
        val url = (cfg.apiBaseUrl.trimEnd('/') + cfg.endpoints.tiemposParada).toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter(cfg.params.stopCode, stopId)
            ?.addQueryParameter(cfg.params.language, language)
            ?.build()
            ?: return error()
        val request = Request.Builder().url(url).apply { cfg.headers.forEach { (k, v) -> header(k, v) } }.build()
        // Timeouts de la config efectiva (empaquetada o remota), no los del cliente compartido.
        val client = http.newBuilder()
            .connectTimeout(cfg.connectTimeoutSec.toLong(), TimeUnit.SECONDS)
            .callTimeout(cfg.callTimeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).await().use { response ->
                    val fetchedAtMs = nowMs()
                    if (!response.isSuccessful) error(fetchedAtMs) else parseStopFeed(response.body?.string().orEmpty(), fetchedAtMs)
                }
            } catch (e: IOException) {
                warn("tiemposParada $stopId: sin respuesta", e)
                error()
            }
        }
    }

    private fun error(atMs: Long = nowMs()) = StopFeed(StopFeedStatus.API_ERROR, emptyList(), null, atMs)
}
