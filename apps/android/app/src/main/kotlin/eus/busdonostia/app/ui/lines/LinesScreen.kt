package eus.busdonostia.app.ui.lines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.FavoriteStar
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SearchPill
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.StopCodePill
import eus.busdonostia.app.ui.components.lineColor
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState

/** A03 · Líneas. */
@Composable
fun LinesScreen(onOpenLine: (String) -> Unit) {
    val vm = appViewModel { LinesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.lines_title),
            subtitle = if (state.total > 0) pluralStringResource(R.plurals.lines_count, state.total, state.total) else null,
        )
        SearchPill(
            query = state.query,
            onQueryChange = vm::onQueryChange,
            placeholder = stringResource(R.string.lines_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinesFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { vm.onFilterChange(filter) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = {
                        Text(
                            stringResource(
                                when (filter) {
                                    LinesFilter.ALL -> R.string.filter_all
                                    LinesFilter.FAVORITES -> R.string.lines_filter_favorites
                                    LinesFilter.NIGHT -> R.string.lines_filter_night
                                },
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                )
            }
        }
        when {
            state.catalogReady == null -> SkeletonList()
            state.catalogReady == false ->
                EmptyState(stringResource(R.string.catalog_error_title), stringResource(R.string.catalog_error_body))
            state.rows.isEmpty() && state.query.isNotBlank() ->
                EmptyState(stringResource(R.string.lines_no_results_title, state.query), stringResource(R.string.lines_no_results_body))
            state.rows.isEmpty() && state.filter == LinesFilter.FAVORITES -> EmptyState(
                stringResource(R.string.lines_favorites_empty_title),
                stringResource(R.string.lines_favorites_empty_body),
                listOf(EmptyAction(stringResource(R.string.lines_show_all), primary = true) { vm.onFilterChange(LinesFilter.ALL) }),
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.rows, key = { it.lineId }) { row ->
                    LineCard(row, state.lineColors[row.lineId], onClick = { onOpenLine(row.lineId) }, onStar = { vm.toggleStar(row.lineId) })
                }
            }
        }
    }
}

@Composable
private fun LineCard(row: LineRow, colorArgb: Int?, onClick: () -> Unit, onStar: () -> Unit) {
    BorderedCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), onClick = onClick) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LineBadge(row.lineId, colorArgb, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.destinations.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.destinations.size > 1) {
                    Text(
                        text = row.destinations.drop(1).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FavoriteStar(row.starred, onStar, stringResource(R.string.a11y_favorite_line, row.lineId))
        }
    }
}

/** A03b · Itinerario: selector de sentido + timeline de paradas reales. Tap en parada → A05 con la línea. */
@Composable
fun LineDetailScreen(lineId: String, onBack: () -> Unit, onOpenStop: (stopId: String, lineId: String) -> Unit) {
    val vm = appViewModel(key = "line:$lineId") { LineDetailViewModel(it, lineId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val line = state.line
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.line_title, line?.cod ?: lineId),
            subtitle = state.itineraries.getOrNull(state.selected)?.let {
                pluralStringResource(R.plurals.stops_count, it.stops.size, it.stops.size)
            },
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
        ) {
            if (line != null) {
                FavoriteStar(state.starred, vm::toggleStar, stringResource(R.string.a11y_favorite_line, line.cod))
            }
        }
        when {
            state.catalogReady == null -> SkeletonList()
            state.catalogReady == false ->
                EmptyState(stringResource(R.string.catalog_error_title), stringResource(R.string.catalog_error_body))
            line == null -> EmptyState(stringResource(R.string.line_not_found_title, lineId), stringResource(R.string.line_not_found_body))
            state.itineraries.isEmpty() -> EmptyState(stringResource(R.string.line_no_itineraries_title), stringResource(R.string.line_no_itineraries_body))
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.itineraries.forEachIndexed { index, itinerary ->
                        FilterChip(
                            selected = index == state.selected,
                            onClick = { vm.select(index) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(stringResource(R.string.line_towards, itinerary.destination), maxLines = 1, softWrap = false) },
                        )
                    }
                }
                val color = lineColor(state.colorArgb)
                val hole = MaterialTheme.colorScheme.background
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                    itemsIndexed(state.stops) { index, stop ->
                        TimelineRow(
                            stop = stop,
                            first = index == 0,
                            last = index == state.stops.lastIndex,
                            color = color,
                            hole = hole,
                            onClick = { onOpenStop(stop.stopId, line.cod) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    stop: ItineraryStop,
    first: Boolean,
    last: Boolean,
    color: androidx.compose.ui.graphics.Color,
    hole: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).fillMaxHeight().heightIn(min = 56.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val x = size.width / 2
                val y = size.height / 2
                val stroke = 4.dp.toPx()
                if (!first) drawLine(color, Offset(x, 0f), Offset(x, y), stroke)
                if (!last) drawLine(color, Offset(x, y), Offset(x, size.height), stroke)
                val radius = if (first || last) 8.dp.toPx() else 6.dp.toPx()
                drawCircle(color, radius, Offset(x, y))
                drawCircle(hole, radius - 2.dp.toPx(), Offset(x, y))
            }
        }
        Text(
            text = stop.name ?: stop.stopId,
            style = if (first || last) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        StopCodePill(stop.stopId)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
