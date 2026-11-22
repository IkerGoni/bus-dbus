package eus.busdonostia.app.ui.stopdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.data.db.toggle
import eus.busdonostia.app.domain.ArrivalRow
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.arrivalRows
import eus.busdonostia.app.domain.FavoriteType
import eus.busdonostia.app.domain.StopFeed
import eus.busdonostia.app.domain.StopFeedStatus
import eus.busdonostia.app.domain.favoriteId
import eus.busdonostia.app.domain.normalizeLine
import eus.busdonostia.app.domain.stopArrivalsView
import eus.busdonostia.app.domain.stopByCode
import eus.busdonostia.app.domain.stopLineIds
import eus.busdonostia.app.domain.toPublicLineId
import eus.busdonostia.app.domain.usableArrivals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StopDetailUiState(
    val stopId: String,
    /** Nombre del catálogo o, si no está, el que da la API; null mientras no se sepa. */
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Líneas reales de la parada (catálogo + vistas en vivo), en código público. */
    val lineIds: List<String> = emptyList(),
    /** Chip seleccionado; null = `Todas`. */
    val selectedLine: String? = null,
    val view: ArrivalsView = ArrivalsView.Loading,
    /**
     * Llegadas de [view] de la línea elegida con sus minutos. Sin la hora en el estado:
     * con el reloj de 1 s, el estado solo cambia cuando cambia un minuto mostrado (H10).
     */
    val rows: List<ArrivalRow> = emptyList(),
    val refreshing: Boolean = false,
    val starred: Boolean = false,
    /** ★ parada+línea de [selectedLine]. */
    val lineStarred: Boolean = false,
    /** Líneas guardadas como favorita parada+línea en esta parada (★ de cada llegada). */
    val starredLines: Set<String> = emptySet(),
    val lineColors: Map<String, Int> = emptyMap(),
    /** Atribución ODbL de las coordenadas (se usan en `Cómo llegar`). */
    val coordinatesAttribution: String? = null,
)

private data class FeedState(
    val latest: StopFeed?,
    val lastValid: StopFeed?,
    val refreshing: Boolean,
    val selectedLine: String?,
)

/** A05. [highlightLine]: línea con la que se llega desde un itinerario o un favorito parada+línea. */
class StopDetailViewModel(
    private val container: AppContainer,
    private val stopId: String,
    highlightLine: String?,
) : ViewModel() {
    private val latest = MutableStateFlow<StopFeed?>(null)
    private val lastValid = MutableStateFlow<StopFeed?>(null)
    private val refreshing = MutableStateFlow(false)
    private val selectedLine = MutableStateFlow(highlightLine?.let(::normalizeLine))
    private val fetchLock = Mutex()

    private val feedState = combine(latest, lastValid, refreshing, selectedLine, ::FeedState)
    private val timeAndConfig = combine(
        container.ticker.now,
        container.configRepository.config,
    ) { nowMs, cfg -> nowMs to cfg }

    val state: StateFlow<StopDetailUiState> = combine(
        container.catalog,
        feedState,
        timeAndConfig,
        container.favorites,
        container.lineColors,
    ) { catalogResult, feed, (nowMs, cfg), favorites, colors ->
        val catalog = catalogResult?.getOrNull()
        val stop = catalog?.stopByCode(stopId)
        val valid = feed.lastValid
        val selected = feed.selectedLine?.let { line -> catalog?.toPublicLineId(line, stopId) ?: line }
        val ids = favorites.map { it.id }.toSet()
        val view = stopArrivalsView(feed.latest, valid, nowMs, cfg.freshness)
        StopDetailUiState(
            stopId = stopId,
            name = stop?.desc ?: valid?.stopName,
            lat = stop?.lat,
            lon = stop?.lon,
            lineIds = catalog?.let { stopLineIds(it, stopId, valid?.arrivals.orEmpty()) }.orEmpty(),
            selectedLine = selected,
            view = view,
            rows = arrivalRows(usableArrivals(view), selected, nowMs),
            refreshing = feed.refreshing,
            starred = favoriteId(FavoriteType.STOP, stopId, null) in ids,
            lineStarred = selected != null && favoriteId(FavoriteType.STOP_LINE, stopId, selected) in ids,
            starredLines = favorites
                .filter { it.type == FavoriteType.STOP_LINE && it.stopId == stopId }
                .mapNotNullTo(HashSet()) { it.lineId },
            lineColors = colors,
            coordinatesAttribution = catalog?.coordinates?.attribution,
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), StopDetailUiState(stopId))

    init {
        container.appScope.launch { container.database.recentStops().record(stopId, System.currentTimeMillis()) }
    }

    /** Sondeo mientras la pantalla está visible (la UI lo lanza con `repeatOnLifecycle(STARTED)`). */
    suspend fun pollWhileVisible() {
        while (true) {
            fetch(reuseRecent = true)
            delay(container.configRepository.pollingConfig.value.foregroundVisibleSec * 1_000L)
        }
    }

    fun refresh() {
        viewModelScope.launch { fetch() }
    }

    /** [reuseRecent]: sondeo periódico, que reutiliza lo que el motor acaba de consultar; `Reintentar` siempre pide. */
    private suspend fun fetch(reuseRecent: Boolean = false) = fetchLock.withLock {
        refreshing.value = true
        try {
            val feed = if (reuseRecent) container.live.pollStop(stopId) else container.live.fetchStop(stopId)
            latest.value = feed
            if (feed.status != StopFeedStatus.API_ERROR) lastValid.value = feed
        } finally {
            refreshing.value = false
        }
    }

    fun selectLine(lineId: String?) {
        selectedLine.value = lineId
    }

    fun toggleStopStar() = toggle(FavoriteType.STOP, null)

    fun toggleLineStar() {
        state.value.selectedLine?.let(::toggleLineStar)
    }

    /** ★ de una llegada: favorita parada+línea. */
    fun toggleLineStar(lineId: String) = toggle(FavoriteType.STOP_LINE, lineId)

    private fun toggle(type: FavoriteType, lineId: String?) {
        container.appScope.launch {
            container.database.favorites().toggle(type, stopId, lineId, System.currentTimeMillis())
        }
    }
}
