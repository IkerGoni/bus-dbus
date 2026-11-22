package eus.busdonostia.app.engine

import android.content.Context
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.isWatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Arranca el servicio si algún aviso está en vigilancia y no está ya vivo. Se llama en
 * cada cambio de avisos (guardar, switch, Deshacer) y al abrir la app; si
 * Android no deja arrancar desde segundo plano, no pasa nada y se retoma al abrir la app.
 */
fun ensureMonitoring(context: Context, rules: List<AlertRule>, nowMs: Long): Boolean {
    if (AlertMonitorService.running || rules.none { isWatching(it, nowMs) }) return true
    return AlertMonitorService.start(context)
}

/**
 * Arranque del motor (H9): con cada cambio de avisos (guardar, switch, Deshacer) y al
 * abrir la app ([onAppOpened]); si algo queda en vigilancia, arranca el servicio.
 */
class MonitoringStarter(
    private val context: Context,
    private val alerts: StateFlow<List<AlertRule>?>,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            alerts.filterNotNull().collect { rules -> ensureMonitoring(context, rules, System.currentTimeMillis()) }
        }
    }

    /** Al abrir la app (tras reiniciar el móvil es la única forma de retomar un aviso). */
    fun onAppOpened() {
        alerts.value?.let { ensureMonitoring(context, it, System.currentTimeMillis()) }
    }
}
