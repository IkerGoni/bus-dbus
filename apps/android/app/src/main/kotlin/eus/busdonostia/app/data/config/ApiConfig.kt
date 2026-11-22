package eus.busdonostia.app.data.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Configuración de la API de Dbus (ADR 009 § Configuración remota, `schemaVersion` 1).
 * Si Dbus cambia base URL, path, nombre de parámetro o exige una cabecera, se arregla
 * editando este JSON y no publicando versión. La copia por defecto va en `assets/config.json`.
 */
@Serializable
data class ApiConfig(
    val schemaVersion: Int,
    val apiBaseUrl: String,
    val endpoints: Endpoints,
    val params: Params,
    val headers: Map<String, String> = emptyMap(),
    val pollIntervalSec: Int,
    /** `versionCode` mínimo soportado; por debajo, la UI pide actualizar. */
    val minAppVersion: Int,
    val notice: Notice? = null,
) {
    @Serializable
    data class Endpoints(val tiemposParada: String)

    @Serializable
    data class Params(
        val stopCode: String,
        val language: String,
    )

    /** Aviso de incidencia opcional, en los dos idiomas de la app. */
    @Serializable
    data class Notice(val es: String, val eu: String)

    companion object {
        const val SCHEMA_VERSION = 1
        const val MIN_POLL_INTERVAL_SEC = 20
        const val MAX_POLL_INTERVAL_SEC = 300

        /** Único host al que se permite HTTP (también en `network_security_config.xml`). */
        const val CLEARTEXT_HOST = "app.dbus.eus"

        /**
         * Hosts a los que la config (remota incluida) puede mandar las consultas (H21): quien
         * controle el hosting de `config.json` no puede redirigirlas a otro sitio. Ampliarla
         * exige publicar versión.
         */
        val ALLOWED_API_HOSTS = setOf(CLEARTEXT_HOST)

        /**
         * Cabeceras que la config puede añadir (comparación sin mayúsculas). Fuera quedan las
         * de credenciales, cookies, `Host` y proxies.
         */
        val ALLOWED_HEADERS = setOf("accept", "accept-language", "user-agent", "referer", "origin", "x-requested-with")

        private val json = Json { ignoreUnknownKeys = true }

        /** Parsea y valida; null si el JSON no es válido o no pasa [validate]. */
        fun parse(text: String): ApiConfig? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
                ?.takeIf { it.validate().isEmpty() }
    }

    /** Errores de esquema y rangos; vacío = válida. [allowedHosts]: hosts permitidos para `apiBaseUrl`. */
    fun validate(allowedHosts: Set<String> = ALLOWED_API_HOSTS): List<String> = buildList {
        if (schemaVersion != SCHEMA_VERSION) add("schemaVersion $schemaVersion no soportado")
        val base = apiBaseUrl.toHttpUrlOrNull()
        when {
            base == null -> add("apiBaseUrl inválida")
            base.host !in allowedHosts -> add("apiBaseUrl: host ${base.host} no permitido")
            !base.isHttps && base.host != CLEARTEXT_HOST -> add("apiBaseUrl HTTP solo para $CLEARTEXT_HOST")
        }
        if (!endpoints.tiemposParada.startsWith("/")) add("endpoints.tiemposParada debe empezar por /")
        if (params.stopCode.isBlank() || params.language.isBlank()) add("params vacíos")
        if (headers.keys.any { it.isBlank() }) add("cabecera sin nombre")
        headers.keys.filter { it.isNotBlank() && it.lowercase() !in ALLOWED_HEADERS }.forEach { add("cabecera $it no permitida") }
        if (headers.values.any { v -> v.any { it == '\r' || it == '\n' } }) add("cabecera con salto de línea")
        if (pollIntervalSec !in MIN_POLL_INTERVAL_SEC..MAX_POLL_INTERVAL_SEC) add("pollIntervalSec fuera de rango")
        if (minAppVersion < 1) add("minAppVersion < 1")
        if (notice != null && (notice.es.isBlank() || notice.eu.isBlank())) add("notice incompleto")
    }
}
