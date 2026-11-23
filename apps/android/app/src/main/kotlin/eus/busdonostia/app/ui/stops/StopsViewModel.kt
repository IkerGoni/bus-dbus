package eus.busdonostia.app.ui.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.data.db.toggle
import eus.busdonostia.app.domain.Catalog
import eus.busdonostia.app.domain.CatalogStop
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.FavoriteType
import eus.busdonostia.app.domain.GeoConfig
import eus.busdonostia.app.domain.UiLimitsConfig
import eus.busdonostia.app.domain.nearbyStops
import eus.busdonostia.app.domain.searchStops
import eus.busdonostia.app.domain.stopByCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StopsTab { NEARBY, FAVORITES, RECENTS }

sealed interface LocationState {
    data object NoPermission : LocationState

    data object Locating : LocationState

    /** En memoria y solo en esta pantalla: nunca se persiste. */
    data class Located(val lat: Double, val lon: Double) : LocationState

    /** Con permiso, pero el sistema no dio posición (GPS apagado, sin señal). */
    data object Unavailable : LocationState
}

data class StopRow(
    val stopId: String,
    val name: String,
    val lineIds: List<String>,
    /** Solo en Cercanas: distancia en línea recta y minutos a pie estimados. */
    val meters: Long? = null,
    val walkMinutes: Int? = null,
    /** Favorito parada+línea (STOP_LINE): la estrella de la fila se refiere a esa línea. */
    val favoriteLineId: String? = null,
    /** Destino principal para desambiguar paradas homónimas (p. ej. "Hacia Amara" vs "Hacia Centro"). */
    val mainDestination: String? = null,
    val starred: Boolean,
)

data class StopsUiState(
    /** null = cargando el catálogo; false = no se pudo leer (bug de build). */
    val catalogReady: Boolean? = null,
    val query: String = "",
    val tab: StopsTab = StopsTab.NEARBY,
    val location: LocationState = LocationState.NoPermission,
    /** Resultados de búsqueda si hay texto; si no, los de la pestaña. */
    val rows: List<StopRow> = emptyList(),
    /** Recientes, para Cercanas sin ubicación (nunca bloquea). */
    val recentRows: List<StopRow> = emptyList(),
    val lineColors: Map<String, Int> = emptyMap(),
    /** `© OpenStreetMap contributors, ODbL` del catálogo, visible cuando se usan coordenadas. */
    val coordinatesAttribution: String? = null,
    /** Metros por minuto a pie derivados de `geo.walkingSpeedKmh` (nota de estimación, A5-10). */
    val walkMetersPerMin: Int = (GeoConfig().walkingSpeedKmh * 1_000 / 60).toInt(),
    /** Límite de recientes de `uiLimits.recentStopsLimit` (estado vacío de Recientes, A5-10). */
    val recentStopsLimit: Int = UiLimitsConfig().recentStopsLimit,
)

/** Límite de resultados de búsqueda en pantalla (el ranking es el de `searchStops`). */
const val STOP_SEARCH_LIMIT = 50

private data class Inputs(
    val catalog: Result<Catalog>?,
    val query: String,
    val tab: StopsTab,
    val location: LocationState,
    val favorites: List<Favorite>,
)

class StopsViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(StopsTab.NEARBY)
    private val location = MutableStateFlow<LocationState>(
        if (container.location.hasPermission()) LocationState.Locating else LocationState.NoPermission,
    )

    private val inputs = combine(container.catalog, query, tab, location, container.favorites, ::Inputs)

    val state: StateFlow<StopsUiState> = combine(
        inputs,
        container.database.recentStops().observe(),
        container.lineColors,
        container.configRepository.config,
    ) { input, recents, colors, cfg ->
        val catalog = input.catalog?.getOrNull()
        if (catalog == null) {
            return@combine StopsUiState(catalogReady = input.catalog?.let { false }, query = input.query, tab = input.tab)
        }
        val starredStops = input.favorites.filter { it.type == FavoriteType.STOP }.mapNotNull { it.stopId }.toSet()
        fun row(stop: CatalogStop, meters: Long? = null, walk: Int? = null) =
            StopRow(
                stopId = stop.cod,
                name = stop.desc,
                lineIds = stop.lines,
                meters = meters,
                walkMinutes = walk,
                mainDestination = stop.destinations.firstOrNull(),
                starred = stop.cod in starredStops,
            )

        val recentRows = recents.mapNotNull { catalog.stopByCode(it.stopId)?.let(::row) }
        val rows = when {
            input.query.isNotBlank() -> searchStops(catalog, input.query, cfg.uiLimits.searchStopsLimit).map(::row)
            input.tab == StopsTab.NEARBY -> (input.location as? LocationState.Located)
                ?.let {
                    nearbyStops(
                        catalog = catalog,
                        lat = it.lat,
                        lon = it.lon,
                        limit = cfg.uiLimits.nearbyStopsLimit,
                        walkingSpeedKmh = cfg.geo.walkingSpeedKmh,
                    ).map { n -> row(n.stop, n.meters, n.walkMinutes) }
                }
                .orEmpty()
            input.tab == StopsTab.FAVORITES -> favoriteRows(catalog, input.favorites)
            else -> recentRows
        }
        StopsUiState(
            catalogReady = true,
            query = input.query,
            tab = input.tab,
            location = input.location,
            rows = rows,
            recentRows = recentRows,
            lineColors = colors,
            coordinatesAttribution = catalog.coordinates.attribution,
            walkMetersPerMin = (cfg.geo.walkingSpeedKmh * 1_000 / 60).toInt(),
            recentStopsLimit = cfg.uiLimits.recentStopsLimit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), StopsUiState())

    init {
        if (location.value == LocationState.Locating) refreshLocation()
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onTabChange(value: StopsTab) {
        tab.value = value
    }

    /** Resultado del diálogo de permisos; con permiso, pide posición. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) refreshLocation() else location.value = LocationState.NoPermission
    }

    fun refreshLocation() {
        if (!container.location.hasPermission()) {
            location.value = LocationState.NoPermission
            return
        }
        location.value = LocationState.Locating
        viewModelScope.launch {
            val fix = container.location.current()
            location.value = fix?.let { LocationState.Located(it.latitude, it.longitude) } ?: LocationState.Unavailable
        }
    }

    /** ★ de una fila: STOP, o STOP_LINE si la fila es un favorito parada+línea. */
    fun toggleStar(row: StopRow) {
        val type = if (row.favoriteLineId != null) FavoriteType.STOP_LINE else FavoriteType.STOP
        container.appScope.launch {
            container.database.favorites().toggle(type, row.stopId, row.favoriteLineId, System.currentTimeMillis())
        }
    }
}

/** Favoritas: paradas (STOP) y parada+línea (STOP_LINE), más recientes primero; sin las que no estén en el catálogo.
 * Deduplica por (stopId, favoriteLineId): si existen varios favoritos idénticos se queda solo el más reciente,
 * evitando tarjetas repetidas en HomeScreen. */
fun favoriteRows(catalog: Catalog, favorites: List<Favorite>): List<StopRow> =
    favorites.sortedByDescending { it.createdAtMs }.mapNotNull { fav ->
        val stopId = fav.stopId ?: return@mapNotNull null
        val stop = catalog.stopByCode(stopId) ?: return@mapNotNull null
        val mainDest = stop.destinations.firstOrNull()
        when (fav.type) {
            FavoriteType.STOP -> StopRow(
                stopId = stop.cod,
                name = stop.desc,
                lineIds = stop.lines,
                mainDestination = mainDest,
                starred = true,
            )
            FavoriteType.STOP_LINE -> StopRow(
                stopId = stop.cod,
                name = stop.desc,
                lineIds = listOfNotNull(fav.lineId),
                favoriteLineId = fav.lineId,
                mainDestination = mainDest,
                starred = true,
            )
            FavoriteType.LINE -> null
        }
    }.distinctBy { it.stopId to it.favoriteLineId }
