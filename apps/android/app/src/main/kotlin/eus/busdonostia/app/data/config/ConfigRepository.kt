package eus.busdonostia.app.data.config

import android.content.res.AssetManager
import eus.busdonostia.app.data.Warn
import eus.busdonostia.app.data.await
import eus.busdonostia.app.data.logcatWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Config efectiva: la última remota válida o, si no hay, la empaquetada. Descarga
 * como mucho cada 12 h (cuenta el intento, salga bien o mal), timeout 5 s, valida
 * esquema y rangos; ante cualquier fallo se queda con la que tenía. Nunca bloquea
 * el arranque: [config] tiene valor desde el constructor.
 */
class ConfigRepository(
    private val bundled: ApiConfig,
    private val cacheFile: File,
    /** null = sin config remota (hasta el Sprint 5). Ver [remoteConfigUrl]. */
    private val remoteUrl: HttpUrl?,
    http: OkHttpClient,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val warn: Warn = logcatWarn("ConfigRepository"),
) {
    private val http = http.newBuilder().callTimeout(TIMEOUT_SEC, TimeUnit.SECONDS).build()
    private var cache: Cached? = if (remoteUrl == null) null else readCache()
    private val state = MutableStateFlow(cache?.config ?: bundled)
    val config: StateFlow<ApiConfig> = state.asStateFlow()

    /** true si se descargó y aplicó una config nueva válida. */
    suspend fun refreshIfDue(): Boolean {
        val url = remoteUrl ?: return false
        val now = nowMs()
        val last = cache?.lastAttemptMs
        if (last != null && now - last in 0 until REFRESH_INTERVAL_MS) return false

        val fetched = withContext(Dispatchers.IO) {
            try {
                http.newCall(Request.Builder().url(url).build()).await().use { response ->
                    if (response.isSuccessful) response.body?.string()?.let(ApiConfig::parse) else null
                }
            } catch (e: IOException) {
                warn("config remota: sin respuesta", e)
                null
            }
        }
        val updated = Cached(lastAttemptMs = now, config = fetched ?: cache?.config)
        cache = updated
        writeCache(updated)
        if (fetched == null) return false
        state.value = fetched
        return true
    }

    @Serializable
    private data class Cached(val lastAttemptMs: Long, val config: ApiConfig?)

    private fun readCache(): Cached? = if (!cacheFile.exists()) {
        null // Primera vez: lo normal, no se avisa.
    } else {
        try {
            json.decodeFromString(Cached.serializer(), cacheFile.readText())
                .let { it.copy(config = it.config?.takeIf { c -> c.validate().isEmpty() }) }
        } catch (e: Exception) {
            warn("caché de config ilegible: se ignora", e)
            null // Corrupta: como si no hubiera.
        }
    }

    private fun writeCache(value: Cached) {
        try {
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(json.encodeToString(Cached.serializer(), value))
            if (!tmp.renameTo(cacheFile)) tmp.delete()
        } catch (e: IOException) {
            warn("no se pudo guardar la caché de config", e)
            // Sin caché en disco solo se pierde el intervalo de 12 h; nunca bloquea.
        }
    }

    companion object {
        const val TIMEOUT_SEC = 5L
        const val REFRESH_INTERVAL_MS = 12 * 3_600_000L
        private val json = Json { ignoreUnknownKeys = true }

        /** Copia por defecto empaquetada. Si no es válida es un bug de build: falla en los tests. */
        fun loadBundled(assets: AssetManager): ApiConfig =
            assets.open("config.json").bufferedReader().use { it.readText() }
                .let { ApiConfig.parse(it) ?: error("assets/config.json inválida") }

        /** La URL de la config remota solo puede ser HTTPS; vacía o HTTP → sin remota. */
        fun remoteConfigUrl(raw: String): HttpUrl? = raw.trim().toHttpUrlOrNull()?.takeIf { it.isHttps }
    }
}
