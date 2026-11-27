package eus.busdonostia.app.ui.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertDetail
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertMode
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalRow
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.ComputedAlertStatus
import eus.busdonostia.app.domain.DegradedReason
import eus.busdonostia.app.domain.NextAlertSource
import eus.busdonostia.app.domain.nextAlertSource
import eus.busdonostia.app.domain.remainingMinutes
import eus.busdonostia.app.ui.Destination
import eus.busdonostia.app.ui.alerts.AlertStatusLabel
import eus.busdonostia.app.ui.alerts.alertDestination
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.AccentRailCard
import eus.busdonostia.app.ui.components.AnimatedMinutesText
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.BrandPullToRefreshBox
import eus.busdonostia.app.ui.components.EmptyStopsIllustration
import eus.busdonostia.app.ui.components.ErrorSignalIllustration
import eus.busdonostia.app.ui.components.FavoriteStar
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.LiveRadarBeacon
import eus.busdonostia.app.ui.components.LocalBottomDockInset
import eus.busdonostia.app.ui.components.MinutesLabel
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SearchEmptyIllustration
import eus.busdonostia.app.ui.components.SearchPill
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SharedKeys
import eus.busdonostia.app.ui.components.SharedOrigins
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.SkeletonVariant
import eus.busdonostia.app.ui.components.clockOf
import eus.busdonostia.app.ui.components.lineColor
import eus.busdonostia.app.ui.components.motionAnimateItem
import eus.busdonostia.app.ui.components.optionalSharedElement
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.stops.StopRow
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.CountdownHero
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A01 · Inicio: próximo aviso, paradas favoritas en vivo y búsqueda universal (paradas y líneas). */
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

    PredictiveBackHandler(enabled = state.query.isNotBlank()) { progress ->
        try {
            progress.collect { }
            vm.onQueryChange("")
        } catch (_: CancellationException) {
        }
    }

    HomeContent(
        state = state,
        snackbar = snackbar,
        onNavigate = onNavigate,
        onOpenStop = onOpenStop,
        onOpenLine = onOpenLine,
        onOpenSettings = onOpenSettings,
        onQueryChange = vm::onQueryChange,
        onRefresh = vm::refresh,
        onPause = onPause,
        onUnstar = vm::unstar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onNavigate: (Destination) -> Unit,
    onOpenStop: (stopId: String, lineId: String?, from: String?) -> Unit,
    onOpenLine: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onPause: (AlertRule) -> Unit,
    onUnstar: (StopRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomDockInset = LocalBottomDockInset.current
    val haptic = LocalHapticFeedback.current

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = stringResource(R.string.app_name)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_open))
                }
            }
            SearchPill(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.home_search_hint),
                modifier = Modifier.padding(horizontal = DesignTokens.spacingLg),
            )
            Spacer(Modifier.height(DesignTokens.spacingXs))
            when {
                state.catalogReady == false -> EmptyState(
                    title = stringResource(R.string.catalog_error_title),
                    body = stringResource(R.string.catalog_error_body),
                    illustration = { ErrorSignalIllustration() },
                )
                state.query.isNotBlank() -> SearchResults(state, onOpenStop, onOpenLine, bottomDockInset)
                state.loading -> Column(Modifier.fillMaxSize()) {
                    SkeletonList(rows = 1, variant = SkeletonVariant.HERO)
                    SkeletonList(rows = 2, variant = SkeletonVariant.CARD)
                }
                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    actions = listOf(
                        EmptyAction(stringResource(R.string.home_cta_nearby), primary = true) { onNavigate(Destination.STOPS) },
                        EmptyAction(stringResource(R.string.home_cta_lines), primary = false) { onNavigate(Destination.LINES) },
                    ),
                    modifier = Modifier.padding(bottom = bottomDockInset),
                    illustration = { EmptyStopsIllustration() },
                )
                else -> BrandPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = DesignTokens.spacingLg,
                            end = DesignTokens.spacingLg,
                            top = DesignTokens.spacingSm,
                            bottom = DesignTokens.spacingLg + bottomDockInset,
                        ),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                    ) {
                        state.nextAlert?.let { next ->
                            item(key = "next-label", contentType = "section_label") {
                                SectionLabel(stringResource(R.string.home_next_alert), horizontalPadding = 0.dp)
                            }
                            item(key = "next", contentType = "next_alert") {
                                NextAlertCard(
                                    item = next,
                                    view = state.nextAlertView,
                                    colorArgb = state.lineColors[next.rule.lineId],
                                    onOpen = { onOpenStop(next.rule.stopId, next.rule.lineId, SharedOrigins.NEXT_ALERT) },
                                    onPause = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPause(next.rule)
                                    },
                                )
                            }
                        }
                        favorites(state, onOpenStop, onUnstar, onRefresh)
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = DesignTokens.spacingLg, end = DesignTokens.spacingLg, bottom = DesignTokens.spacingLg + bottomDockInset),
        )
    }
}

@Composable
private fun SearchResults(
    state: HomeUiState,
    onOpenStop: (String, String?, String?) -> Unit,
    onOpenLine: (String) -> Unit,
    bottomDockInset: androidx.compose.ui.unit.Dp,
) {
    if (state.stopResults.isEmpty() && state.lineResults.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.home_search_empty_title, state.query),
            body = stringResource(R.string.home_search_empty_body),
            modifier = Modifier.imePadding().padding(bottom = bottomDockInset),
            illustration = { SearchEmptyIllustration() },
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = DesignTokens.spacingLg,
            end = DesignTokens.spacingLg,
            top = DesignTokens.spacingXs,
            bottom = DesignTokens.spacingLg + bottomDockInset,
        ),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        if (state.stopResults.isNotEmpty()) {
            item(key = "search-stops-label", contentType = "section_label") {
                SectionLabel(stringResource(R.string.nav_stops), horizontalPadding = 0.dp)
            }
            items(state.stopResults, key = { "s-${it.cod}" }, contentType = { "stop_result" }) { stop ->
                val origin = SharedOrigins.search(stop.cod)
                ResultCardRow(
                    onClick = { onOpenStop(stop.cod, null, origin) },
                    modifier = motionAnimateItem(),
                ) {
                    Text(
                        text = stop.desc,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .optionalSharedElement(SharedKeys.stopTitle(stop.cod, origin)),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.lineResults.isNotEmpty()) {
            item(key = "search-lines-label", contentType = "section_label") {
                SectionLabel(stringResource(R.string.nav_lines), horizontalPadding = 0.dp)
            }
            items(state.lineResults, key = { "l-${it.cod}" }, contentType = { "line_result" }) { line ->
                ResultCardRow(
                    onClick = { onOpenLine(line.cod) },
                    modifier = motionAnimateItem(),
                ) {
                    LineBadge(line.cod, state.lineColors[line.cod], size = DesignTokens.lineBadgeMinWidth)
                    Text(
                        text = line.destinations.joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCardRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    BorderedCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DesignTokens.headerMinHeight)
                .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
            content = content,
        )
    }
}

/**
 * Signature Moment 1 (`TU PRÓXIMO AVISO`): tablero Hero con raíl oficial de línea,
 * baliza radar GPS / icono reloj, cuenta atrás `42.sp` `tnum` y acción rápida `Pausar`.
 */
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
    val accent = lineColor(colorArgb)

    AccentRailCard(accentColor = accent, onClick = onOpen) {
        Column(
            modifier = Modifier.padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
            ) {
                LineBadge(
                    lineId = rule.lineId,
                    colorArgb = colorArgb,
                    size = DesignTokens.lineBadgeLarge,
                    modifier = Modifier.optionalSharedElement(
                        SharedKeys.line(rule.stopId, rule.lineId, SharedOrigins.NEXT_ALERT),
                    ),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    Text(
                        text = alertDestination(rule),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = rule.stopName.ifBlank { stringResource(R.string.stop_unnamed) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.optionalSharedElement(
                            SharedKeys.stopTitle(rule.stopId, SharedOrigins.NEXT_ALERT),
                        ),
                    )
                }
            }

            if (arrival != null) {
                val minutes = item.remainingMin ?: remainingMinutes(arrival, arrival.fetchedAtMs)
                val isLive = arrival.source == ArrivalSource.LIVE
                val metricColor = if (isLive) colors.live else colors.scheduled
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
                ) {
                    if (isLive) {
                        LiveRadarBeacon(
                            isLive = true,
                            liveColor = colors.live,
                            scheduledColor = colors.scheduled,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = colors.scheduled,
                            modifier = Modifier.size(DesignTokens.iconMedium),
                        )
                    }
                    AnimatedMinutesText(
                        minutes = minutes,
                        formattedText = if (minutes <= 0) stringResource(R.string.minutes_now) else stringResource(R.string.minutes_short, minutes),
                        style = CountdownHero,
                        color = metricColor,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                AlertStatusLabel(item)
                if (rule.mode == AlertMode.ALARM) {
                    Surface(
                        color = colors.alarmContainer,
                        shape = RoundedCornerShape(DesignTokens.cornerRadiusPill),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                            modifier = Modifier.padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
                        ) {
                            Icon(
                                Icons.Outlined.Alarm,
                                contentDescription = stringResource(R.string.alert_mode_alarm),
                                modifier = Modifier.size(DesignTokens.iconSmall),
                                tint = colors.onAlarmContainer,
                            )
                            Text(
                                text = stringResource(R.string.alert_mode_alarm),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onAlarmContainer,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Text(
                    text = nextAlertSourceText(item, view),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedButton(
                onClick = onPause,
                modifier = Modifier.fillMaxWidth().heightIn(min = DesignTokens.touchTargetMin),
            ) {
                Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(DesignTokens.iconMedium))
                Spacer(Modifier.width(DesignTokens.spacingSm))
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

private fun LazyListScope.favorites(
    state: HomeUiState,
    onOpenStop: (String, String?, String?) -> Unit,
    onUnstar: (StopRow) -> Unit,
    onRetry: () -> Unit,
) {
    if (state.favorites.isEmpty()) return
    item(key = "fav-label", contentType = "section_label") {
        SectionLabel(
            text = stringResource(R.string.home_favorites),
            trailing = state.favorites.size.toString(),
            horizontalPadding = 0.dp,
        )
    }
    items(
        items = state.favorites,
        key = { "fav-${it.row.stopId}-${it.row.favoriteLineId.orEmpty()}" },
        contentType = { "favorite_stop" },
    ) { card ->
        val origin = SharedOrigins.favorite(card.row.stopId, card.row.favoriteLineId)
        FavoriteCard(
            card = card,
            lineColors = state.lineColors,
            origin = origin,
            onOpen = { onOpenStop(card.row.stopId, card.row.favoriteLineId, origin) },
            onUnstar = { onUnstar(card.row) },
            onRetry = onRetry,
            modifier = motionAnimateItem(),
        )
    }
}

@Composable
private fun FavoriteCard(
    card: FavoriteStopCard,
    lineColors: Map<String, Int>,
    origin: String,
    onOpen: () -> Unit,
    onUnstar: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = card.row
    BorderedCard(modifier = modifier, onClick = onOpen) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingXs,
                top = DesignTokens.spacingXs,
                bottom = DesignTokens.spacingMd,
            ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .optionalSharedElement(SharedKeys.stopTitle(row.stopId, origin)),
                )
                row.favoriteLineId?.let { lineId ->
                    LineBadge(
                        lineId = lineId,
                        colorArgb = lineColors[lineId],
                        size = DesignTokens.lineBadgeChipSize,
                        modifier = Modifier.optionalSharedElement(SharedKeys.line(row.stopId, lineId, origin)),
                    )
                }
                Spacer(Modifier.weight(1f))
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
            Column(
                modifier = Modifier.padding(end = DesignTokens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
            ) {
                FavoriteArrivals(card = card, lineColors = lineColors, onRetry = onRetry)
            }
        }
    }
}

/**
 * Contenido de llegadas en una tarjeta favorita.
 * Cada rama multi-línea se envuelve explícitamente en un `Column` dentro de `AnimatedContent`
 * para evitar solapamientos visuales en `(0, 0)`.
 */
@Composable
internal fun FavoriteArrivals(
    card: FavoriteStopCard,
    lineColors: Map<String, Int>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val motion = rememberEffectiveMotionTokens()
    AnimatedContent(
        targetState = card.view,
        contentKey = { it::class },
        transitionSpec = {
            (
                fadeIn(tween(motion.arrivalsStateFadeInMs)) +
                    slideInVertically(
                        tween(motion.arrivalsStateFadeInMs, easing = FastOutSlowInEasing),
                    ) { (it * motion.arrivalsStateSlideFraction).toInt() }
                ).togetherWith(fadeOut(tween(motion.arrivalsStateFadeOutMs)))
        },
        label = "arrivalsViewState",
        modifier = modifier,
    ) { view ->
        when (view) {
            ArrivalsView.Loading -> SkeletonList(
                rows = 2,
                variant = SkeletonVariant.ARRIVAL_ROW,
                modifier = Modifier.padding(vertical = DesignTokens.spacingXs),
            )
            ArrivalsView.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.stop_error_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
            is ArrivalsView.Empty -> Text(
                text = stringResource(R.string.stop_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            else -> if (view is ArrivalsView.Degraded && view.reason == DegradedReason.NO_SERVICE) {
                Text(
                    text = view.notice ?: stringResource(R.string.stop_no_service_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
                    if (view is ArrivalsView.Degraded) {
                        Text(
                            text = stringResource(R.string.stop_stale, clockOf(view.fetchedAtMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalStatusColors.current.warning,
                        )
                    }
                    if (card.rows.isEmpty()) {
                        Text(
                            text = card.row.favoriteLineId?.let { stringResource(R.string.stop_line_empty, it) }
                                ?: stringResource(R.string.stop_empty_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                        )
                    } else {
                        card.rows.forEach { arrivalRow ->
                            val arrival = arrivalRow.arrival
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                            ) {
                                LineBadge(
                                    lineId = arrival.lineId,
                                    colorArgb = lineColors[arrival.lineId],
                                    size = DesignTokens.lineBadgeChipSize,
                                )
                                Text(
                                    text = arrival.destination.ifEmpty { stringResource(R.string.destination_unknown) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
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
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun HomeLivePreview() {
    val now = 1_700_000_000_000L
    val rule = AlertRule("a1", "1281", "Boulevard 15", "28", "Ospitaleak", 5, true, now, AlertMode.ALARM)
    val a1 = Arrival("28", "32", "Ospitaleak", 3, "10:15", ArrivalSource.LIVE, now, null)
    val a2 = Arrival("5", "5", "Benta Berri", 1, "10:13", ArrivalSource.LIVE, now, null)
    val a3 = Arrival("13", "13", "Altza", 9, "10:21", ArrivalSource.SCHEDULED, now, null)
    val sample = HomeUiState(
        catalogReady = true,
        alertsLoaded = true,
        nextAlert = AlertItem(rule, ComputedAlertStatus.TRIGGERED, AlertDetail.NONE, a1, 3),
        nextAlertView = ArrivalsView.Live(listOf(a1), null, now),
        favorites = listOf(
            FavoriteStopCard(
                row = StopRow("1281", "Boulevard 15", listOf("5", "13"), starred = true),
                view = ArrivalsView.Live(listOf(a2, a3), null, now),
                arrivals = listOf(a2, a3),
                rows = listOf(ArrivalRow("r1", a2, 1), ArrivalRow("r2", a3, 9)),
            ),
        ),
    )
    BusDonostiaTheme {
        HomeContent(
            state = sample,
            snackbar = remember { SnackbarHostState() },
            onNavigate = {},
            onOpenStop = { _, _, _ -> },
            onOpenLine = {},
            onOpenSettings = {},
            onQueryChange = {},
            onRefresh = {},
            onPause = {},
            onUnstar = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    BusDonostiaTheme {
        HomeContent(
            state = HomeUiState(catalogReady = true, alertsLoaded = true),
            snackbar = remember { SnackbarHostState() },
            onNavigate = {},
            onOpenStop = { _, _, _ -> },
            onOpenLine = {},
            onOpenSettings = {},
            onQueryChange = {},
            onRefresh = {},
            onPause = {},
            onUnstar = {},
        )
    }
}
