package eus.busdonostia.app.ui.home

import eus.busdonostia.app.ui.components.motionAnimateItem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import eus.busdonostia.app.ui.components.AnimatedMinutesText
import eus.busdonostia.app.ui.components.LiveSignalDot
import eus.busdonostia.app.ui.components.optionalSharedElement
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.DegradedReason
import eus.busdonostia.app.domain.NextAlertSource
import eus.busdonostia.app.domain.nextAlertSource
import eus.busdonostia.app.domain.remainingMinutes
import eus.busdonostia.app.ui.Destination
import eus.busdonostia.app.ui.alerts.AlertStatusLabel
import eus.busdonostia.app.ui.alerts.alertDestination
import eus.busdonostia.app.ui.alerts.clockOf
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.BrandPullToRefreshBox
import eus.busdonostia.app.ui.components.FavoriteStar
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.MinutesLabel
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SearchPill
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SharedKeys
import eus.busdonostia.app.ui.components.SharedOrigins
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.theme.CountdownHero
import eus.busdonostia.app.ui.theme.LocalStatusColors
import eus.busdonostia.app.ui.theme.MotionTokens
import kotlinx.coroutines.launch

/** A01 · Inicio: próximo aviso, paradas favoritas en vivo y búsqueda universal (paradas y líneas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    onOpenStop: (stopId: String, lineId: String?, from: String?) -> Unit,
    onOpenLine: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollWhileVisible() }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pausedText = stringResource(R.string.alert_paused)
    val undoText = stringResource(R.string.undo)
    val onPause: (AlertRule) -> Unit = { rule ->
        vm.pause(rule)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(pausedText, undoText, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                vm.undoPause(rule)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = stringResource(R.string.app_name)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_open))
                }
            }
            SearchPill(
                query = state.query,
                onQueryChange = vm::onQueryChange,
                placeholder = stringResource(R.string.home_search_hint),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            when {
                state.catalogReady == false ->
                    EmptyState(stringResource(R.string.catalog_error_title), stringResource(R.string.catalog_error_body))
                state.query.isNotBlank() -> SearchResults(state, onOpenStop, onOpenLine)
                state.loading -> SkeletonList(rows = 3, rowHeight = 120.dp)
                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    actions = listOf(
                        EmptyAction(stringResource(R.string.home_cta_nearby), primary = true) { onNavigate(Destination.STOPS) },
                        EmptyAction(stringResource(R.string.home_cta_lines), primary = false) { onNavigate(Destination.LINES) },
                    ),
                )
                else -> BrandPullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.nextAlert?.let { next ->
                            item(key = "next-label", contentType = "section_label") {
                                SectionLabel(stringResource(R.string.home_next_alert), Modifier.padding(horizontal = 0.dp))
                            }
                            item(key = "next", contentType = "next_alert") {
                                NextAlertCard(
                                    item = next,
                                    view = state.nextAlertView,
                                    colorArgb = state.lineColors[next.rule.lineId],
                                    onOpen = { onOpenStop(next.rule.stopId, next.rule.lineId, SharedOrigins.NEXT_ALERT) },
                                    onPause = { onPause(next.rule) },
                                )
                            }
                        }
                        favorites(state, vm, onOpenStop)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun SearchResults(
    state: HomeUiState,
    onOpenStop: (String, String?, String?) -> Unit,
    onOpenLine: (String) -> Unit,
) {
    if (state.stopResults.isEmpty() && state.lineResults.isEmpty()) {
        EmptyState(stringResource(R.string.home_search_empty_title, state.query), stringResource(R.string.home_search_empty_body))
        return
    }
    val motion = rememberEffectiveMotionTokens()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (state.stopResults.isNotEmpty()) {
            item(key = "search-stops-label", contentType = "section_label") { SectionLabel(stringResource(R.string.nav_stops)) }
            items(state.stopResults, key = { "s-${it.cod}" }, contentType = { "stop_result" }) { stop ->
                val origin = SharedOrigins.search(stop.cod)
                ResultRow(
                    onClick = { onOpenStop(stop.cod, null, origin) },
                    modifier = motionAnimateItem(),
                ) {
                    Text(
                        stop.desc,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .optionalSharedElement(SharedKeys.stopTitle(stop.cod, origin)),
                    )
                }
            }
        }
        if (state.lineResults.isNotEmpty()) {
            item(key = "search-lines-label", contentType = "section_label") { SectionLabel(stringResource(R.string.nav_lines)) }
            items(state.lineResults, key = { "l-${it.cod}" }, contentType = { "line_result" }) { line ->
                ResultRow(
                    onClick = { onOpenLine(line.cod) },
                    modifier = motionAnimateItem(),
                ) {
                    LineBadge(line.cod, state.lineColors[line.cod])
                    Text(
                        line.destinations.joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** `TU PRÓXIMO AVISO`: badge + destino | cuenta atrás grande; parada | fuente y antelación; Pausar real. */
@Composable
private fun NextAlertCard(
    item: AlertItem,
    view: ArrivalsView?,
    colorArgb: Int?,
    onOpen: () -> Unit,
    onPause: () -> Unit,
) {
    val rule = item.rule
    val arrival = item.nextArrival
    val colors = LocalStatusColors.current
    BorderedCard(onClick = onOpen) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LineBadge(
                    rule.lineId,
                    colorArgb,
                    size = 36.dp,
                    modifier = Modifier.optionalSharedElement(
                        SharedKeys.line(rule.stopId, rule.lineId, SharedOrigins.NEXT_ALERT),
                    ),
                )
                Text(
                    alertDestination(rule),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (arrival != null) {
                    val minutes = item.remainingMin ?: remainingMinutes(arrival, arrival.fetchedAtMs)
                    val isLive = arrival.source == ArrivalSource.LIVE
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiveSignalDot(
                            isLive = isLive,
                            liveColor = colors.live,
                            scheduledColor = colors.scheduled,
                        )
                        AnimatedMinutesText(
                            minutes = minutes,
                            formattedText = if (minutes <= 0) stringResource(R.string.minutes_now) else stringResource(R.string.minutes_short, minutes),
                            style = CountdownHero,
                            color = if (isLive) colors.live else colors.scheduled,
                        )
                    }
                }
            }
            Text(
                rule.stopName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.optionalSharedElement(
                    SharedKeys.stopTitle(rule.stopId, SharedOrigins.NEXT_ALERT),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertStatusLabel(item)
                Text(
                    nextAlertSourceText(item, view),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.alert_pause), maxLines = 1)
            }
        }
    }
}

/** `En vivo · Aviso 5'`, `Horario · Aviso 5'` o qué pasa con la parada (sin servicio, error, cargando). */
@Composable
private fun nextAlertSourceText(item: AlertItem, view: ArrivalsView?): String {
    val ahead = stringResource(R.string.alert_ahead_short, item.rule.minutesAhead)
    return when (val source = nextAlertSource(item, view)) {
        is NextAlertSource.Known -> stringResource(
            R.string.home_next_source,
            stringResource(if (source.source == ArrivalSource.LIVE) R.string.source_live else R.string.source_scheduled),
            ahead,
        )
        NextAlertSource.Waiting -> ahead
        NextAlertSource.FeedError -> stringResource(R.string.alert_feed_error)
        NextAlertSource.NoService -> stringResource(R.string.alert_feed_no_service)
        NextAlertSource.NoArrivals -> stringResource(R.string.home_next_no_arrivals, ahead)
    }
}

private fun LazyListScope.favorites(state: HomeUiState, vm: HomeViewModel, onOpenStop: (String, String?, String?) -> Unit) {
    if (state.favorites.isEmpty()) return
    item(key = "fav-label", contentType = "section_label") { SectionLabel(stringResource(R.string.home_favorites), Modifier.padding(horizontal = 0.dp)) }
    items(
        items = state.favorites,
        key = { "fav-${it.row.stopId}-${it.row.favoriteLineId.orEmpty()}" },
        contentType = { "favorite_stop" },
    ) { card ->
        val origin = SharedOrigins.favorite(card.row.stopId, card.row.favoriteLineId)
        FavoriteCard(
            card = card,
            state = state,
            origin = origin,
            onOpen = { onOpenStop(card.row.stopId, card.row.favoriteLineId, origin) },
            onUnstar = { vm.unstar(card.row) },
            onRetry = vm::refresh,
            modifier = motionAnimateItem(),
        )
    }
}

@Composable
private fun FavoriteCard(
    card: FavoriteStopCard,
    state: HomeUiState,
    origin: String,
    onOpen: () -> Unit,
    onUnstar: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = card.row
    BorderedCard(modifier = modifier, onClick = onOpen) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .optionalSharedElement(SharedKeys.stopTitle(row.stopId, origin)),
                )
                row.favoriteLineId?.let {
                    LineBadge(
                        it,
                        state.lineColors[it],
                        size = 24.dp,
                        modifier = Modifier.optionalSharedElement(SharedKeys.line(row.stopId, it, origin)),
                    )
                }
                Box(Modifier.weight(1f))
                FavoriteStar(
                    checked = true,
                    onToggle = onUnstar,
                    description = if (row.favoriteLineId != null) {
                        stringResource(R.string.a11y_favorite_stop_line, row.favoriteLineId, row.name)
                    } else {
                        stringResource(R.string.a11y_favorite_stop, row.name)
                    },
                )
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FavoriteArrivals(card, state, onRetry)
            }
        }
    }
}

@Suppress("EXPERIMENTAL_API_USAGE")
@Composable
private fun FavoriteArrivals(card: FavoriteStopCard, state: HomeUiState, onRetry: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val motion = rememberEffectiveMotionTokens()
    AnimatedContent(
        targetState = card.view,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(motion.arrivalsStateFadeInMs)) + slideInVertically(tween(motion.arrivalsStateFadeInMs)) { (it * motion.arrivalsStateSlideFraction).toInt() })
                .togetherWith(fadeOut(tween(motion.arrivalsStateFadeOutMs)))
        },
        label = "arrivalsViewState",
    ) { view ->
        when (view) {
            ArrivalsView.Loading -> SkeletonList(rows = 1, rowHeight = 24.dp)
            ArrivalsView.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stop_error_title), color = muted, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
            }
            is ArrivalsView.Empty -> Text(stringResource(R.string.stop_empty_title), color = muted)
            else -> if (view is ArrivalsView.Degraded && view.reason == DegradedReason.NO_SERVICE) {
                Text(view.notice ?: stringResource(R.string.stop_no_service_title), color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                if (view is ArrivalsView.Degraded) {
                    Text(stringResource(R.string.stop_stale, clockOf(view.fetchedAtMs)), color = muted, style = MaterialTheme.typography.bodySmall)
                }
                if (card.rows.isEmpty()) {
                    Text(
                        card.row.favoriteLineId?.let { stringResource(R.string.stop_line_empty, it) } ?: stringResource(R.string.stop_empty_title),
                        color = muted,
                    )
                }
                card.rows.forEach { arrivalRow ->
                    val arrival = arrivalRow.arrival
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LineBadge(arrival.lineId, state.lineColors[arrival.lineId], size = 24.dp)
                        Text(
                            arrival.destination.ifEmpty { stringResource(R.string.destination_unknown) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        MinutesLabel(arrivalRow.minutes, arrival.source)
                    }
                }
            }
        }
    }
}
