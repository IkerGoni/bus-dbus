package eus.busdonostia.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.data.db.toggle
import eus.busdonostia.app.data.live.StopFeeds
import eus.busdonostia.app.data.live.feedView
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.Catalog
import eus.busdonostia.app.domain.CatalogLine
import eus.busdonostia.app.domain.CatalogStop
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.FavoriteType
import eus.busdonostia.app.domain.filterArrivals
import eus.busdonostia.app.domain.nextAlert
import eus.busdonostia.app.domain.remainingMinutes
import eus.busdonostia.app.domain.searchLines
import eus.busdonostia.app.domain.searchStops
import eus.busdonostia.app.domain.usableArrivals
import eus.busdonostia.app.ui.alerts.alertItems
import eus.busdonostia.app.ui.alerts.pollStops
import eus.busdonostia.app.ui.alerts.setAlertActive
import eus.busdonostia.app.ui.alerts.stopsToWatch
import eus.busdonostia.app.ui.alerts.updateAlert
import eus.busdonostia.app.ui.stops.StopRow
import eus.busdonostia.app.ui.stops.favoriteRows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Máximo de llegadas por tarjeta de favorita (A01: 2–3). */
const val HOME_ARRIVALS_PER_STOP = 3

data class FavoriteStopCard(
    val row: StopRow,
    /** Vista de 5 estados de la parada. */
    val view: ArrivalsView,
    /** Próximas llegadas (de su línea si es parada+línea), como mucho [HOME_ARRIVALS_PER_STOP]. */
    val arrivals: List<Arrival>,
)

data class HomeUiState(
    /** null = cargando el catálogo; false = no se pudo leer. */
    val catalogReady: Boolean? = null,
    val alertsLoaded: Boolean = false,
    val nextAlert: AlertItem? = null,
    val nextAlertView: ArrivalsView? = null,
    val favorites: List<FavoriteStopCard> = emptyList(),
    val query: String = "",
    val stopResults: List<CatalogStop> = emptyList(),
    val lineResults: List<CatalogLine> = emptyList(),
    val nowMs: Long = 0,
    val refreshing: Boolean = false,
    val lineColors: Map<String, Int> = emptyMap(),
) {
    val loading: Boolean get() = catalogReady == null || !alertsLoaded
    val isEmpty: Boolean get() = !loading && nextAlert == null && favorites.isEmpty()
}

/** Paradas favoritas (STOP y STOP_LINE) con sus próximas llegadas en vivo. */
fun favoriteCards(catalog: Catalog, favorites: List<Favorite>, feeds: Map<String, StopFeeds>, nowMs: Long): List<FavoriteStopCard> =
    favoriteRows(catalog, favorites).map { row ->
        val view = feedView(feeds, row.stopId, nowMs)
        val arrivals = filterArrivals(usableArrivals(view), row.favoriteLineId)
            .sortedBy { remainingMinutes(it, nowMs) }
            .take(HOME_ARRIVALS_PER_STOP)
        FavoriteStopCard(row, view, arrivals)
    }

private data class Inputs(val favorites: List<Favorite>, val alerts: List<AlertRule>?, val query: String, val refreshing: Boolean)

/** A01 · Inicio. */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val inputs = combine(container.favorites, container.alerts, query, refreshing, ::Inputs)

    val state: StateFlow<HomeUiState> = combine(
        container.catalog,
        inputs,
        container.liveFeeds.feeds,
        container.ticker.now,
        container.lineColors,
    ) { catalogResult, input, feeds, now, colors ->
        val catalog = catalogResult?.getOrNull()
        if (catalog == null) {
            return@combine HomeUiState(catalogReady = catalogResult?.let { false }, query = input.query, lineColors = colors)
        }
        val nowMs = now.toEpochMilli()
        val next = input.alerts?.let { nextAlert(alertItems(it, feeds, nowMs)) }
        HomeUiState(
            catalogReady = true,
            alertsLoaded = input.alerts != null,
            nextAlert = next,
            nextAlertView = next?.let { feedView(feeds, it.rule.stopId, nowMs) },
            favorites = favoriteCards(catalog, input.favorites, feeds, nowMs),
            query = input.query,
            stopResults = searchStops(catalog, input.query, limit = 6),
            lineResults = searchLines(catalog, input.query, limit = 6),
            nowMs = nowMs,
            refreshing = input.refreshing,
            lineColors = colors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HomeUiState())

    private val watched = combine(container.favorites, container.alerts) { favorites, alerts ->
        favoriteStops(favorites) + stopsToWatch(alerts.orEmpty())
    }

    /** Sondeo de favoritas y avisos activos mientras la pantalla está visible. */
    suspend fun pollWhileVisible() = container.pollStops(watched)

    /** Pull-to-refresh y `Reintentar`. */
    fun refresh() {
        val stops = favoriteStops(container.favorites.value) + stopsToWatch(container.alerts.value.orEmpty())
        viewModelScope.launch {
            refreshing.value = true
            try {
                container.fetchStops(stops)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Pausar real: `active = false`. El Snackbar ofrece [undoPause]. */
    fun pause(rule: AlertRule) = container.setAlertActive(rule, false)

    fun undoPause(previous: AlertRule) = container.updateAlert(previous)

    fun unstar(row: StopRow) {
        val type = if (row.favoriteLineId != null) FavoriteType.STOP_LINE else FavoriteType.STOP
        container.appScope.launch {
            container.database.favorites().toggle(type, row.stopId, row.favoriteLineId, System.currentTimeMillis())
        }
    }
}

private fun favoriteStops(favorites: List<Favorite>): Set<String> =
    favorites.filter { it.type != FavoriteType.LINE }.mapNotNullTo(sortedSetOf()) { it.stopId }
