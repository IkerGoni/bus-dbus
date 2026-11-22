package eus.busdonostia.app.engine

import android.content.Context
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.isWatching

/**
 * Arranca el servicio si algún aviso está en vigilancia y no está ya vivo. Se llama en
 * cada cambio de avisos (guardar, switch, Deshacer) y al abrir la app; si
 * Android no deja arrancar desde segundo plano, no pasa nada y se retoma al abrir la app.
 */
fun ensureMonitoring(context: Context, rules: List<AlertRule>, nowMs: Long): Boolean {
    if (AlertMonitorService.running || rules.none { isWatching(it, nowMs) }) return true
    return AlertMonitorService.start(context)
}
