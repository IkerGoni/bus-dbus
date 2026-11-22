package eus.busdonostia.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.groupAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlertsUiState(
    /** Room aún no ha respondido. */
    val loading: Boolean = true,
    /** ACTIVOS: `TRIGGERED`, `ACTIVE`, `SNOOZED`, en el orden de §3. */
    val active: List<AlertItem> = emptyList(),
    /** PAUSADOS: `INACTIVE` (pausados o caducados). */
    val silenced: List<AlertItem> = emptyList(),
    /** Vista de 5 estados de cada parada vigilada (sin servicio, error, datos viejos). */
    val stopViews: Map<String, ArrivalsView> = emptyMap(),
    val nowMs: Long = 0,
    val refreshing: Boolean = false,
    val lineColors: Map<String, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = !loading && active.isEmpty() && silenced.isEmpty()

    /** Alguna parada vigilada sin datos utilizables: banner con `Reintentar`. */
    val anyError: Boolean get() = active.any { stopViews[it.rule.stopId] == ArrivalsView.Error }
}

/** A04 · Mis avisos. */
class AlertsViewModel(private val container: AppContainer) : ViewModel() {
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<AlertsUiState> = combine(
        container.alerts,
        container.liveFeeds.feeds,
        container.ticker.now,
        refreshing,
        container.lineColors,
    ) { rules, feeds, now, isRefreshing, colors ->
        if (rules == null) return@combine AlertsUiState(lineColors = colors)
        val nowMs = now.toEpochMilli()
        val (active, silenced) = groupAlerts(alertItems(rules, feeds, nowMs))
        AlertsUiState(
            loading = false,
            active = active,
            silenced = silenced,
            stopViews = stopsToWatch(rules).associateWith { feedView(feeds, it, nowMs) },
            nowMs = nowMs,
            refreshing = isRefreshing,
            lineColors = colors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AlertsUiState())

    /** Sondeo de las paradas con avisos activos mientras la pantalla está visible. */
    suspend fun pollWhileVisible() = container.pollStops(container.alerts.filterNotNull().map(::stopsToWatch))

    fun refresh() {
        val rules = container.alerts.value ?: return
        viewModelScope.launch {
            refreshing.value = true
            try {
                container.fetchStops(stopsToWatch(rules))
            } finally {
                refreshing.value = false
            }
        }
    }

    fun setActive(rule: AlertRule, active: Boolean) = container.setAlertActive(rule, active)

    fun delete(rule: AlertRule) = container.deleteAlert(rule)

    fun undoDelete(rule: AlertRule) = container.restoreAlert(rule)
}
