package eus.busdonostia.app.ui.lines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.data.db.toggle
import eus.busdonostia.app.domain.Catalog
import eus.busdonostia.app.domain.CatalogItinerary
import eus.busdonostia.app.domain.CatalogLine
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.FavoriteType
import eus.busdonostia.app.domain.isNightLine
import eus.busdonostia.app.domain.itinerariesForLine
import eus.busdonostia.app.domain.lineByCode
import eus.busdonostia.app.domain.normalizeLine
import eus.busdonostia.app.domain.searchLines
import eus.busdonostia.app.domain.sortLines
import eus.busdonostia.app.domain.stopByCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LinesFilter { ALL, FAVORITES, NIGHT }

data class LineRow(val lineId: String, val destinations: List<String>, val night: Boolean, val starred: Boolean)

data class LinesUiState(
    val catalogReady: Boolean? = null,
    val query: String = "",
    val filter: LinesFilter = LinesFilter.ALL,
    /** Líneas del catálogo (hoy 39), para la cabecera. */
    val total: Int = 0,
    val rows: List<LineRow> = emptyList(),
    val lineColors: Map<String, Int> = emptyMap(),
)

/** Códigos públicos con ★ de línea (LINE). */
fun starredLines(favorites: List<Favorite>): Set<String> =
    favorites.filter { it.type == FavoriteType.LINE }.mapNotNull { it.lineId?.let(::normalizeLine) }.toSet()

/**
 * Lista de A03: con texto, el ranking de `searchLines` (`32` → gana la pública 32, luego la 28 por alias);
 * sin texto, diurnas por número y luego nocturnas. El filtro se aplica encima.
 */
fun lineRows(catalog: Catalog, query: String, filter: LinesFilter, starred: Set<String>): List<LineRow> {
    val base = if (query.isBlank()) sortLines(catalog.lines) else searchLines(catalog, query, catalog.lines.size)
    return base.map { LineRow(it.cod, it.destinations, isNightLine(it.cod), normalizeLine(it.cod) in starred) }
        .filter {
            when (filter) {
                LinesFilter.ALL -> true
                LinesFilter.FAVORITES -> it.starred
                LinesFilter.NIGHT -> it.night
            }
        }
}

class LinesViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LinesFilter.ALL)

    val state: StateFlow<LinesUiState> = combine(
        container.catalog,
        query,
        filter,
        container.favorites,
        container.lineColors,
    ) { catalogResult, q, f, favorites, colors ->
        val catalog = catalogResult?.getOrNull()
            ?: return@combine LinesUiState(catalogReady = catalogResult?.let { false }, query = q, filter = f)
        LinesUiState(
            catalogReady = true,
            query = q,
            filter = f,
            total = catalog.lines.size,
            rows = lineRows(catalog, q, f, starredLines(favorites)),
            lineColors = colors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LinesUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: LinesFilter) {
        filter.value = value
    }

    fun toggleStar(lineId: String) = container.toggleLineStar(lineId)
}

fun AppContainer.toggleLineStar(lineId: String) {
    appScope.launch { database.favorites().toggle(FavoriteType.LINE, null, normalizeLine(lineId), System.currentTimeMillis()) }
}

/** Parada de un itinerario; [name] null si el catálogo no la recoge (no se inventa). */
data class ItineraryStop(val stopId: String, val name: String?)

data class LineDetailUiState(
    val catalogReady: Boolean? = null,
    /** null si el código no existe en el catálogo. */
    val line: CatalogLine? = null,
    val itineraries: List<CatalogItinerary> = emptyList(),
    val selected: Int = 0,
    val stops: List<ItineraryStop> = emptyList(),
    val starred: Boolean = false,
    val colorArgb: Int? = null,
)

/** A03b. [lineId] = código público o alias interno; se resuelve con `lineByCode` (gana la pública). */
class LineDetailViewModel(private val container: AppContainer, private val lineId: String) : ViewModel() {
    private val selected = MutableStateFlow(0)

    val state: StateFlow<LineDetailUiState> = combine(
        container.catalog,
        selected,
        container.favorites,
        container.lineColors,
    ) { catalogResult, index, favorites, colors ->
        val catalog = catalogResult?.getOrNull()
            ?: return@combine LineDetailUiState(catalogReady = catalogResult?.let { false })
        val line = catalog.lineByCode(lineId) ?: return@combine LineDetailUiState(catalogReady = true)
        val itineraries = catalog.itinerariesForLine(line.cod)
        val chosen = index.coerceIn(0, maxOf(0, itineraries.lastIndex))
        LineDetailUiState(
            catalogReady = true,
            line = line,
            itineraries = itineraries,
            selected = chosen,
            stops = itineraries.getOrNull(chosen)?.stops.orEmpty().map { ItineraryStop(it, catalog.stopByCode(it)?.desc) },
            starred = normalizeLine(line.cod) in starredLines(favorites),
            colorArgb = colors[line.cod],
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LineDetailUiState())

    fun select(index: Int) {
        selected.value = index
    }

    fun toggleStar() {
        state.value.line?.let { container.toggleLineStar(it.cod) }
    }
}
