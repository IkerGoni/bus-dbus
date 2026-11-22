package eus.busdonostia.app.ui.alerts

import androidx.room.withTransaction
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.data.db.toEntity
import eus.busdonostia.app.data.live.feedView
import eus.busdonostia.app.data.live.StopFeeds
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.alertItem
import eus.busdonostia.app.domain.usableArrivals
import eus.busdonostia.app.domain.withActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Estado calculado de cada aviso con las llegadas que haya de su parada (A01, A04 y badge). */
fun alertItems(rules: List<AlertRule>, feeds: Map<String, StopFeeds>, nowMs: Long): List<AlertItem> =
    rules.map { rule -> alertItem(rule, usableArrivals(feedView(feeds, rule.stopId, nowMs)), nowMs) }

/** Paradas que hay que sondear: las de avisos activos (los pausados no necesitan llegadas). */
fun stopsToWatch(rules: List<AlertRule>): Set<String> = rules.filter { it.active }.mapTo(sortedSetOf()) { it.stopId }

/**
 * Sondea las paradas de [stops] cada `pollIntervalSec` mientras se colecta (la UI lo
 * lanza con `repeatOnLifecycle(STARTED)`). Si cambia el conjunto, consulta ya el nuevo.
 * Lo que el motor acaba de consultar no se vuelve a pedir (`LiveRepository.pollStop`).
 */
suspend fun AppContainer.pollStops(stops: Flow<Set<String>>) {
    stops.distinctUntilChanged().collectLatest { set ->
        if (set.isEmpty()) return@collectLatest
        while (true) {
            coroutineScope { set.map { async { live.pollStop(it) } }.awaitAll() }
            delay(configRepository.config.value.pollIntervalSec * 1_000L)
        }
    }
}

/*
 * Mutaciones reales de avisos (guardrails §3). Van en `appScope` para que no se
 * cancelen al salir de la pantalla. Las que admiten «Deshacer» restauran la regla anterior.
 */

fun AppContainer.updateAlert(rule: AlertRule) {
    appScope.launch { database.alerts().update(rule.toEntity()) }
}

fun AppContainer.setAlertActive(rule: AlertRule, active: Boolean) =
    updateAlert(rule.withActive(active, System.currentTimeMillis()))

fun AppContainer.deleteAlert(rule: AlertRule) {
    appScope.launch { database.alerts().delete(rule.id) }
}

/** Deshacer un borrado: vuelve a insertar la misma regla, salvo que ya exista otra para (parada, línea). */
fun AppContainer.restoreAlert(rule: AlertRule) {
    appScope.launch {
        database.withTransaction {
            val dao = database.alerts()
            if (dao.findById(rule.id) == null && dao.findByStopAndLine(rule.stopId, rule.lineId) == null) {
                dao.insert(rule.toEntity())
            }
        }
    }
}
