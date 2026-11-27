package eus.busdonostia.app.ui.lines

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.CatalogItinerary
import eus.busdonostia.app.domain.CatalogLine
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.AccentRailCard
import eus.busdonostia.app.ui.components.EmptyStopsIllustration
import eus.busdonostia.app.ui.components.ErrorSignalIllustration
import eus.busdonostia.app.ui.components.FavoriteStar
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.LocalBottomDockInset
import eus.busdonostia.app.ui.components.MotionFilterChip
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SearchEmptyIllustration
import eus.busdonostia.app.ui.components.SearchPill
import eus.busdonostia.app.ui.components.SharedKeys
import eus.busdonostia.app.ui.components.SharedOrigins
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.lineColor
import eus.busdonostia.app.ui.components.motionAnimateItem
import eus.busdonostia.app.ui.components.optionalSharedElement
import eus.busdonostia.app.ui.components.pressScale
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import eus.busdonostia.app.ui.components.rememberStaggerEntrance
import eus.busdonostia.app.ui.components.staggerEntrance
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.MonoClock
import kotlinx.coroutines.CancellationException

/** A03 · Líneas. */
@Composable
fun LinesScreen(onOpenLine: (String) -> Unit) {
    val vm = appViewModel { LinesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    PredictiveBackHandler(enabled = state.query.isNotBlank()) { progress ->
        try {
            progress.collect { }
            vm.onQueryChange("")
        } catch (_: CancellationException) {
        }
    }

    LinesContentView(
        state = state,
        onQueryChange = vm::onQueryChange,
        onFilterChange = vm::onFilterChange,
        onOpenLine = onOpenLine,
        onToggleStar = vm::toggleStar,
    )
}

@Composable
internal fun LinesContentView(
    state: LinesUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (LinesFilter) -> Unit,
    onOpenLine: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomDockInset = LocalBottomDockInset.current

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.lines_title),
            subtitle = if (state.total > 0) pluralStringResource(R.plurals.lines_count, state.total, state.total) else null,
        )
        SearchPill(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.lines_search_hint),
            modifier = Modifier.padding(horizontal = DesignTokens.spacingLg),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            LinesFilter.entries.forEach { filter ->
                MotionFilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter) },
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
            state.catalogReady == false -> EmptyState(
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.catalog_error_body),
                illustration = { ErrorSignalIllustration() },
            )
            state.rows.isEmpty() && state.query.isNotBlank() -> EmptyState(
                title = stringResource(R.string.lines_no_results_title, state.query),
                body = stringResource(R.string.lines_no_results_body),
                modifier = Modifier.imePadding().padding(bottom = bottomDockInset),
                illustration = { SearchEmptyIllustration() },
            )
            state.rows.isEmpty() && state.filter == LinesFilter.FAVORITES -> EmptyState(
                title = stringResource(R.string.lines_favorites_empty_title),
                body = stringResource(R.string.lines_favorites_empty_body),
                actions = listOf(EmptyAction(stringResource(R.string.lines_show_all), primary = true) { onFilterChange(LinesFilter.ALL) }),
                modifier = Modifier.padding(bottom = bottomDockInset),
                illustration = { EmptyStopsIllustration() },
            )
            else -> {
                val entrance = rememberStaggerEntrance(state.filter)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(
                        start = DesignTokens.spacingLg,
                        end = DesignTokens.spacingLg,
                        bottom = DesignTokens.spacingLg + bottomDockInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                ) {
                    itemsIndexed(state.rows, key = { _, it -> it.lineId }, contentType = { _, _ -> "line_card" }) { index, row ->
                        LineCard(
                            row = row,
                            colorArgb = state.lineColors[row.lineId],
                            onClick = { onOpenLine(row.lineId) },
                            onStar = { onToggleStar(row.lineId) },
                            modifier = motionAnimateItem()
                                .staggerEntrance(index = index, state = entrance),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LineCard(
    row: LineRow,
    colorArgb: Int?,
    onClick: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = lineColor(colorArgb)
    AccentRailCard(accentColor = accent, modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.padding(
                start = DesignTokens.spacingMd,
                end = DesignTokens.spacingXs,
                top = DesignTokens.spacingSm,
                bottom = DesignTokens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        ) {
            LineBadge(row.lineId, colorArgb, size = DesignTokens.lineBadgeLarge)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                ) {
                    Text(
                        text = row.destinations.firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.night) {
                        Icon(
                            imageVector = Icons.Outlined.NightsStay,
                            contentDescription = stringResource(R.string.lines_filter_night),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(DesignTokens.iconSmall),
                        )
                    }
                }
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

/**
 * A03b · Itinerario: Signature Moment 4 — Donostia Transit Schematic Timeline.
 * Cabecera con lavado tonal oficial, selector de sentido y espina dorsal continua en `drawWithCache`.
 */
@Composable
fun LineDetailScreen(
    lineId: String,
    onBack: () -> Unit,
    onOpenStop: (stopId: String, lineId: String, from: String) -> Unit,
) {
    val vm = appViewModel(key = "line:$lineId") { LineDetailViewModel(it, lineId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LineDetailContentView(
        lineId = lineId,
        state = state,
        onBack = onBack,
        onSelectDirection = vm::select,
        onToggleStar = vm::toggleStar,
        onOpenStop = onOpenStop,
    )
}

@Composable
internal fun LineDetailContentView(
    lineId: String,
    state: LineDetailUiState,
    onBack: () -> Unit,
    onSelectDirection: (Int) -> Unit,
    onToggleStar: () -> Unit,
    onOpenStop: (stopId: String, lineId: String, from: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = state.line
    val officialColor = lineColor(state.colorArgb)
    val headerWash = if (line != null) officialColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.background

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(headerWash)) {
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
                    LineBadge(line.cod, state.colorArgb, size = DesignTokens.lineBadgeMinWidth)
                    FavoriteStar(state.starred, onToggleStar, stringResource(R.string.a11y_favorite_line, line.cod))
                }
            }
            if (line != null && state.itineraries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                ) {
                    state.itineraries.forEachIndexed { index, itinerary ->
                        MotionFilterChip(
                            selected = index == state.selected,
                            onClick = { onSelectDirection(index) },
                            label = { Text(stringResource(R.string.line_towards, itinerary.destination), maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
        }
        when {
            state.catalogReady == null -> SkeletonList()
            state.catalogReady == false -> EmptyState(
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.catalog_error_body),
                illustration = { ErrorSignalIllustration() },
            )
            line == null -> EmptyState(
                title = stringResource(R.string.line_not_found_title, lineId),
                body = stringResource(R.string.line_not_found_body),
                illustration = { ErrorSignalIllustration() },
            )
            state.itineraries.isEmpty() -> EmptyState(
                title = stringResource(R.string.line_no_itineraries_title),
                body = stringResource(R.string.line_no_itineraries_body),
                illustration = { EmptyStopsIllustration() },
            )
            else -> {
                val hole = MaterialTheme.colorScheme.background
                val motion = rememberEffectiveMotionTokens()
                Crossfade(
                    targetState = state.selected to state.stops,
                    animationSpec = tween(motion.lineDirectionCrossfadeMs),
                    label = "lineDirectionCrossfade",
                ) { (selectedIndex, stops) ->
                    val entrance = rememberStaggerEntrance(selectedIndex)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(top = DesignTokens.spacingSm, bottom = DesignTokens.spacingXl),
                    ) {
                        itemsIndexed(
                            stops,
                            key = { index, stop -> "$index-${stop.stopId}" },
                            contentType = { _, _ -> "timeline_row" },
                        ) { index, stop ->
                            val origin = SharedOrigins.timeline(selectedIndex, index)
                            TimelineRow(
                                index = index,
                                stop = stop,
                                first = index == 0,
                                last = index == stops.lastIndex,
                                color = officialColor,
                                hole = hole,
                                origin = origin,
                                onClick = { onOpenStop(stop.stopId, line.cod, origin) },
                                modifier = motionAnimateItem()
                                    .staggerEntrance(index = index, state = entrance),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    index: Int,
    stop: ItineraryStop,
    first: Boolean,
    last: Boolean,
    color: Color,
    hole: Color,
    origin: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val strokeToken = DesignTokens.timelineStroke
    val terminalRadiusToken = DesignTokens.timelineTerminalNode
    val intermediateRadiusToken = DesignTokens.timelineIntermediateNode
    val horizontalPadToken = DesignTokens.spacingLg
    val columnWidthToken = DesignTokens.timelineColumnWidth

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.arrivalRowMinHeight)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .drawWithCache {
                val strokePx = strokeToken.toPx()
                val padPx = horizontalPadToken.toPx()
                val colPx = columnWidthToken.toPx()
                val outerR = if (first || last) terminalRadiusToken.toPx() else intermediateRadiusToken.toPx()
                val innerR = (outerR - strokePx * 0.55f).coerceAtLeast(1f)
                onDrawBehind {
                    val x = if (layoutDirection == LayoutDirection.Rtl) {
                        size.width - padPx - colPx / 2f
                    } else {
                        padPx + colPx / 2f
                    }
                    val y = size.height / 2f
                    if (!first) drawLine(color, Offset(x, 0f), Offset(x, y), strokePx)
                    if (!last) drawLine(color, Offset(x, y), Offset(x, size.height), strokePx)
                    drawCircle(color, outerR, Offset(x, y))
                    drawCircle(hole, innerR, Offset(x, y))
                }
            }
            .padding(horizontal = horizontalPadToken),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(columnWidthToken))
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = MonoClock,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = DesignTokens.spacingXs),
        )
        Text(
            text = stop.name ?: stringResource(R.string.stop_unnamed),
            style = if (first || last) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = DesignTokens.spacingMd)
                .optionalSharedElement(SharedKeys.stopTitle(stop.stopId, origin)),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinesListPreview() {
    val state = LinesUiState(
        catalogReady = true,
        total = 39,
        rows = listOf(
            LineRow("28", listOf("Amara", "Ospitaleak"), night = false, starred = true),
            LineRow("B4", listOf("Amara", "Riberas", "Loiola"), night = true, starred = false),
        ),
    )
    BusDonostiaTheme {
        LinesContentView(
            state = state,
            onQueryChange = {},
            onFilterChange = {},
            onOpenLine = {},
            onToggleStar = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LineDetailTimelinePreview() {
    val line = CatalogLine("32", "28", listOf("Boulevard", "Ospitaleak"), listOf("1", "2", "3"))
    val state = LineDetailUiState(
        catalogReady = true,
        line = line,
        itineraries = listOf(CatalogItinerary("it-1", "32", "28", "Ospitaleak", listOf("1", "2", "3"))),
        stops = listOf(
            ItineraryStop("1", "Boulevard 15"),
            ItineraryStop("2", "Easo 19"),
            ItineraryStop("3", "Ospitaleak"),
        ),
        starred = true,
    )
    BusDonostiaTheme {
        LineDetailContentView(
            lineId = "28",
            state = state,
            onBack = {},
            onSelectDirection = {},
            onToggleStar = {},
            onOpenStop = { _, _, _ -> },
        )
    }
}
