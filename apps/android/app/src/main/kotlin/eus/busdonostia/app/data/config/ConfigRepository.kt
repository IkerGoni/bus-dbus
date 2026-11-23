package eus.busdonostia.app.data.config

import android.content.res.AssetManager
import eus.busdonostia.app.data.Warn
import eus.busdonostia.app.data.await
import eus.busdonostia.app.data.logcatWarn
import eus.busdonostia.app.domain.AlertsConfig
import eus.busdonostia.app.domain.FreshnessConfig
import eus.busdonostia.app.domain.GeoConfig
import eus.busdonostia.app.domain.PollingConfig
import eus.busdonostia.app.domain.UiLimitsConfig
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

/** Origen de la config efectiva (A5-16): un fallback a [DEFAULTS] tras un fallo de parseo se ve aquí y en logcat. */
enum class ConfigSource {
    /** Defaults de Kotlin en memoria: aún no se ha leído el asset (o falló su lectura/parseo). */
    DEFAULTS,

    /** `assets/config.json` empaquetado, leído y parseado correctamente. */
    BUNDLED,

    /** Última config remota válida, desde la caché en disco. */
    REMOTE_CACHE,

    /** Config remota recién descargada y validada. */
    REMOTE,
}

/**
 * Config efectiva: la última remota válida o, si no hay, la empaquetada. Descarga
 * como mucho cada `remoteConfigCacheHours` (cuenta el intento, salga bien o mal), timeout `remoteConfigTimeoutSec`, valida
 * esquema y rangos; ante cualquier fallo se queda con la que tenía. Nunca bloquea
 * el arranque: [config] tiene valor desde el constructor.
 */
class ConfigRepository(
    @Volatile private var bundled: ApiConfig,
    /** Perezoso: `filesDir` toca disco; solo se evalúa en IO ([initializeFromDisk], A5-13). */
    cacheFileProvider: () -> File,
    /** null = sin config remota. Ver [remoteConfigUrl]. */
    private val remoteUrl: HttpUrl?,
    private val http: OkHttpClient,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val warn: Warn = logcatWarn("ConfigRepository"),
) {
    private val cacheFile: File by lazy(cacheFileProvider)

    @Volatile
    private var cache: Cached? = null

    private val state = MutableStateFlow(bundled)
    val config: StateFlow<ApiConfig> = state.asStateFlow()

    private val _configSource = MutableStateFlow(ConfigSource.DEFAULTS)

    /** De dónde sale la config efectiva: hace visible un fallback silencioso a los defaults de Kotlin (A5-16). */
    val configSource: StateFlow<ConfigSource> = _configSource.asStateFlow()

    private val _pollingConfig = MutableStateFlow(state.value.polling)
    val pollingConfig: StateFlow<PollingConfig> = _pollingConfig.asStateFlow()

    private val _freshnessConfig = MutableStateFlow(state.value.freshness)
    val freshnessConfig: StateFlow<FreshnessConfig> = _freshnessConfig.asStateFlow()

    private val _alertsConfig = MutableStateFlow(state.value.alerts)
    val alertsConfig: StateFlow<AlertsConfig> = _alertsConfig.asStateFlow()

    private val _geoConfig = MutableStateFlow(state.value.geo)
    val geoConfig: StateFlow<GeoConfig> = _geoConfig.asStateFlow()

    private val _uiLimitsConfig = MutableStateFlow(state.value.uiLimits)
    val uiLimitsConfig: StateFlow<UiLimitsConfig> = _uiLimitsConfig.asStateFlow()

    /**
     * Inicializa desde disco (`assets/config.json` y `remote-config.json`) fuera del Main Thread.
     */
    fun initializeFromDisk(loadBundledFn: () -> ApiConfig = { bundled }) {
        val loaded = runCatching(loadBundledFn)
        bundled = loaded.getOrElse { e ->
            warn("no se pudo leer assets/config.json", e)
            bundled
        }
        if (remoteUrl != null) {
            cache = readCache()
        }
        val cached = cache?.config
        applyConfig(cached ?: bundled)
        _configSource.value = when {
            cached != null -> ConfigSource.REMOTE_CACHE
            loaded.isSuccess -> ConfigSource.BUNDLED
            else -> ConfigSource.DEFAULTS
        }
    }

    private fun applyConfig(next: ApiConfig) {
        state.value = next
        _pollingConfig.value = next.polling
        _freshnessConfig.value = next.freshness
        _alertsConfig.value = next.alerts
        _geoConfig.value = next.geo
        _uiLimitsConfig.value = next.uiLimits
    }

    /** true si se descargó y aplicó una config nueva válida. */
    suspend fun refreshIfDue(): Boolean {
        val url = remoteUrl ?: return false
        val now = nowMs()
        val last = cache?.lastAttemptMs
        val refreshIntervalMs = state.value.remoteConfigCacheHours * 3_600_000L
        if (last != null && now - last in 0 until refreshIntervalMs) return false

        val fetched = withContext(Dispatchers.IO) {
            try {
                // Timeout de la config efectiva en cada intento (la remota puede cambiarlo).
                http.newBuilder()
                    .callTimeout(state.value.remoteConfigTimeoutSec.toLong(), TimeUnit.SECONDS)
                    .build()
                    .newCall(Request.Builder().url(url).build()).await().use { response ->
                    if (response.isSuccessful) response.body?.string()?.let(ApiConfig::parse) else null
                }
            } catch (e: IOException) {
                warn("config remota: sin respuesta", e)
                null
            }
        }
        val updated = Cached(lastAttemptMs = now, config = fetched ?: cache?.config)
        cache = updated
        withContext(Dispatchers.IO) {
            writeCache(updated)
        }
        if (fetched == null) return false
        applyConfig(fetched)
        _configSource.value = ConfigSource.REMOTE
        return true
    }

    @Serializable
    private data class Cached(val lastAttemptMs: Long, val config: ApiConfig?)

    private fun readCache(): Cached? = if (!cacheFile.exists()) {
        null
    } else {
        try {
            json.decodeFromString(Cached.serializer(), cacheFile.readText())
                .let { it.copy(config = it.config?.takeIf { c -> c.validate().isEmpty() }) }
        } catch (e: Exception) {
            warn("caché de config ilegible: se ignora", e)
            null
        }
    }

    private fun writeCache(value: Cached) {
        try {
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(json.encodeToString(Cached.serializer(), value))
            if (!tmp.renameTo(cacheFile)) tmp.delete()
        } catch (e: IOException) {
            warn("no se pudo guardar la caché de config", e)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Copia por defecto empaquetada. Si no es válida es un bug de build: falla en los tests. */
        fun loadBundled(assets: AssetManager): ApiConfig =
            assets.open("config.json").bufferedReader().use { it.readText() }
                .let { ApiConfig.parse(it) ?: error("assets/config.json inválida") }

        /** La URL de la config remota solo puede ser HTTPS; vacía o HTTP → sin remota. */
        fun remoteConfigUrl(raw: String): HttpUrl? = raw.trim().toHttpUrlOrNull()?.takeIf { it.isHttps }
    }
}
