package eus.busdonostia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.domain.alertBadgeCount
import eus.busdonostia.app.ui.alerts.alertItems
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** Badge de la pestaña Avisos: `ACTIVE | TRIGGERED` con la misma `computeAlertStatus` que A01 y A04. */
class ShellViewModel(container: AppContainer) : ViewModel() {
    val alertBadge: StateFlow<Int> = combine(container.alerts, container.live.feeds, container.ticker.now) { rules, feeds, now ->
        alertBadgeCount(alertItems(rules.orEmpty(), feeds, now.toEpochMilli()))
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)
}
