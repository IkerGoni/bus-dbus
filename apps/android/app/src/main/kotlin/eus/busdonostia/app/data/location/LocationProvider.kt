package eus.busdonostia.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import eus.busdonostia.app.domain.GeoConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Ubicación puntual sin Play Services (ADR 009: sin Firebase ni SDKs de Google).
 * Nunca se persiste: la usa el ViewModel en memoria y se olvida (AGENTS.md).
 * Los tiempos operativos vienen de `GeoConfig` (nivel 1, A5-10).
 */
class LocationProvider(
    context: Context,
    private val geoConfig: () -> GeoConfig = { GeoConfig() },
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }

    /** Proveedores por preferencia; con permiso aproximado Android devuelve una posición difuminada. */
    private fun providers(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { runCatching { manager?.isProviderEnabled(it) == true }.getOrDefault(false) }

    /** Posición actual (o null si no hay permiso, proveedor o respuesta en el timeout de `GeoConfig`). */
    suspend fun current(timeoutMs: Long = geoConfig().locationTimeoutSec * 1_000L): Location? {
        val lm = manager ?: return null
        if (!hasPermission()) return null
        val providers = providers()
        // Atajo: una posición del sistema reciente (lastKnownMaxAgeSec) evita esperar al proveedor.
        val maxAgeNs = geoConfig().lastKnownMaxAgeSec * 1_000_000_000L
        val recent = providers.mapNotNull { lastKnown(lm, it) }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < maxAgeNs }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent
        for (provider in providers) {
            val location = withTimeoutOrNull(timeoutMs) { request(lm, provider) }
            if (location != null) return location
        }
        return null
    }

    private fun lastKnown(lm: LocationManager, provider: String): Location? = try {
        lm.getLastKnownLocation(provider)
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private suspend fun request(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(appContext)) {
                    if (cont.isActive) cont.resume(it)
                }
            } catch (_: SecurityException) {
                cont.resume(null) // p. ej. GPS con solo permiso aproximado en Android < 12
            } catch (_: IllegalArgumentException) {
                cont.resume(null) // proveedor desconocido en este dispositivo
            }
        }
}
