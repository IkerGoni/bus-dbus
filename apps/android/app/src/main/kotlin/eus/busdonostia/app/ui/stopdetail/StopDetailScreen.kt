package eus.busdonostia.app.ui.stopdetail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalRow
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.DegradedReason
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.AccentRailCard
import eus.busdonostia.app.ui.components.AnimatedMinutesText
import eus.busdonostia.app.ui.components.BrandPullToRefreshBox
import eus.busdonostia.app.ui.components.EmptyStopsIllustration
import eus.busdonostia.app.ui.components.ErrorSignalIllustration
import eus.busdonostia.app.ui.components.FavoriteStar
import eus.busdonostia.app.ui.components.InfoBanner
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.LiveRadarBeacon
import eus.busdonostia.app.ui.components.MinutesLabel
import eus.busdonostia.app.ui.components.MotionFilterChip
import eus.busdonostia.app.ui.components.NoServiceIllustration
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SharedKeys
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.SkeletonVariant
import eus.busdonostia.app.ui.components.clockOf
import eus.busdonostia.app.ui.components.lineColor
import eus.busdonostia.app.ui.components.motionAnimateItem
import eus.busdonostia.app.ui.components.optionalSharedElement
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.CountdownHero
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.LocalStatusColors
import eus.busdonostia.app.ui.theme.MonoClock

/** A05 · Detalle de parada. Sondea `tiemposParada` cada `pollIntervalSec` mientras está visible. */
@Composable
fun StopDetailScreen(
    stopId: String,
    highlightLine: String?,
    sharedOrigin: String?,
    onBack: () -> Unit,
    onCreateAlert: (stopId: String, lineId: String?, destination: String?) -> Unit,
) {
    val vm = appViewModel(key = "stop:$stopId") { StopDetailViewModel(it, stopId, highlightLine) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollWhileVisible() }
    }
    val context = LocalContext.current
    val unnamedStop = stringResource(R.string.stop_unnamed)
    val directions: Intent? = remember(state.lat, state.lon, state.name) {
        val lat = state.lat
        val lon = state.lon
        if (lat == null || lon == null) return@remember null
        val label = Uri.encode(state.name?.takeIf { it.isNotBlank() } ?: unnamedStop)
        Intent(Intent.ACTION_VIEW, "geo:$lat,$lon?q=$lat,$lon($label)".toUri())
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    StopDetailContent(
        state = state,
        highlightLine = highlightLine,
        sharedOrigin = sharedOrigin,
        hasDirections = directions != null,
        onBack = onBack,
        onOpenDirections = { directions?.let { context.startActivity(it) } },
        onToggleStopStar = vm::toggleStopStar,
        onSelectLine = vm::selectLine,
        onToggleFilteredLineStar = vm::toggleLineStar,
        onToggleArrivalStar = vm::toggleLineStar,
        onRefresh = vm::refresh,
        onCreateAlert = onCreateAlert,
    )
}

@Composable
internal fun StopDetailContent(
    state: StopDetailUiState,
    highlightLine: String?,
    sharedOrigin: String?,
    hasDirections: Boolean,
    onBack: () -> Unit,
    onOpenDirections: () -> Unit,
    onToggleStopStar: () -> Unit,
    onSelectLine: (String?) -> Unit,
    onToggleFilteredLineStar: () -> Unit,
    onToggleArrivalStar: (String) -> Unit,
    onRefresh: () -> Unit,
    onCreateAlert: (stopId: String, lineId: String?, destination: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stopId = state.stopId
    val haptic = LocalHapticFeedback.current

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = state.name ?: stringResource(R.string.stop_unnamed),
            titleModifier = if (sharedOrigin != null) {
                Modifier.optionalSharedElement(SharedKeys.stopTitle(stopId, sharedOrigin))
            } else {
                Modifier
            },
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
        ) {
            if (hasDirections) {
                IconButton(onClick = onOpenDirections) {
                    Icon(Icons.Filled.Directions, contentDescription = stringResource(R.string.stop_directions))
                }
            }
            if (!state.notFound) {
                FavoriteStar(
                    checked = state.starred,
                    onToggle = onToggleStopStar,
                    description = stringResource(R.string.a11y_favorite_stop, state.name ?: stringResource(R.string.stop_unnamed)),
                )
            }
        }

        if (state.notFound) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    title = stringResource(R.string.stop_not_found_title),
                    body = stringResource(R.string.stop_not_found_body),
                    actions = listOf(EmptyAction(stringResource(R.string.back), primary = true, onClick = onBack)),
                    illustration = { ErrorSignalIllustration() },
                )
            }
        } else {
            LineChips(stopId, state, highlightLine, sharedOrigin, onSelectLine)
            val selected = state.selectedLine
            if (selected != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(DesignTokens.cornerRadiusPill),
                    modifier = Modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingXs),
                ) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFilteredLineStar()
                        },
                        modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
                    ) {
                        Icon(
                            if (state.lineStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (state.lineStarred) LocalStatusColors.current.favorite else LocalStatusColors.current.scheduled,
                            modifier = Modifier.size(DesignTokens.iconMedium),
                        )
                        Spacer(Modifier.width(DesignTokens.spacingSm))
                        Text(
                            text = stringResource(if (state.lineStarred) R.string.stop_line_starred else R.string.stop_line_star, selected),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                Arrivals(
                    state = state,
                    onRetry = onRefresh,
                    onStar = { onToggleArrivalStar(it.lineId) },
                ) { arrival -> onCreateAlert(stopId, arrival.lineId, arrival.destination.ifEmpty { null }) }
            }
            // Dock inferior fijo exclusivo de A05 (AppShell oculta el dock principal en esta ruta).
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Button(
                        onClick = { onCreateAlert(stopId, null, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm)
                            .heightIn(min = DesignTokens.touchTargetMin),
                    ) {
                        Icon(Icons.Outlined.NotificationAdd, contentDescription = null)
                        Spacer(Modifier.width(DesignTokens.spacingSm))
                        Text(stringResource(R.string.stop_create_alert), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineChips(
    stopId: String,
    state: StopDetailUiState,
    highlightLine: String?,
    sharedOrigin: String?,
    onSelect: (String?) -> Unit,
) {
    if (state.lineIds.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        MotionFilterChip(
            selected = state.selectedLine == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all), maxLines = 1, softWrap = false) },
        )
        state.lineIds.forEach { line ->
            val sharedKey = if (sharedOrigin != null && line == highlightLine) {
                SharedKeys.line(stopId, line, sharedOrigin)
            } else {
                null
            }
            MotionFilterChip(
                selected = state.selectedLine == line,
                onClick = { onSelect(if (state.selectedLine == line) null else line) },
                label = {
                    LineBadge(
                        lineId = line,
                        colorArgb = state.lineColors[line],
                        size = DesignTokens.lineBadgeChipSize,
                        modifier = if (sharedKey != null) Modifier.optionalSharedElement(sharedKey) else Modifier,
                    )
                },
            )
        }
    }
}

@Composable
private fun Arrivals(
    state: StopDetailUiState,
    onRetry: () -> Unit,
    onStar: (Arrival) -> Unit,
    onBell: (Arrival) -> Unit,
) {
    val unnamedStop = stringResource(R.string.stop_unnamed)
    BrandPullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = DesignTokens.spacingLg),
        ) {
            stopDetailItems(state = state, unnamedStop = unnamedStop, onRetry = onRetry, onStar = onStar, onBell = onBell)
        }
    }
}

private fun LazyListScope.stopDetailItems(
    state: StopDetailUiState,
    unnamedStop: String,
    onRetry: () -> Unit,
    onStar: (Arrival) -> Unit,
    onBell: (Arrival) -> Unit,
) {
    item(key = "stop-arrivals-header", contentType = "arrival_header") {
        ArrivalsHeader(refreshing = state.refreshing, onRetry = onRetry)
    }
    item(key = "stop-arrivals-status", contentType = "arrivals_status") {
        ArrivalsStatus(view = state.view, onRetry = onRetry)
    }
    if (state.showsArrivals) {
        if (state.rows.isEmpty()) {
            item(key = "stop-line-empty", contentType = "status_message") {
                StatusMessage(
                    illustration = { EmptyStopsIllustration() },
                    title = stringResource(R.string.stop_line_empty, state.selectedLine.orEmpty()),
                    body = null,
                )
            }
        } else {
            val hero = state.rows.first()
            val stopName = state.name ?: unnamedStop
            item(key = "hero-${hero.key}", contentType = "hero_arrival") {
                NextBusHeroBoard(
                    row = hero,
                    colorArgb = state.lineColors[hero.arrival.lineId],
                    stopName = stopName,
                    starred = hero.arrival.lineId in state.starredLines,
                    onStar = { onStar(hero.arrival) },
                    onBell = { onBell(hero.arrival) },
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.spacingLg,
                        vertical = DesignTokens.spacingXs,
                    ),
                )
            }
            val following = state.rows.drop(1)
            if (following.isNotEmpty()) {
                item(key = "following-label", contentType = "section_label") {
                    SectionLabel(stringResource(R.string.stop_following_arrivals))
                }
                items(following, key = { it.key }, contentType = { "arrival_row" }) { row ->
                    val arrival = row.arrival
                    Column(modifier = motionAnimateItem()) {
                        ArrivalRow(
                            arrival = arrival,
                            minutes = row.minutes,
                            colorArgb = state.lineColors[arrival.lineId],
                            stopName = stopName,
                            starred = arrival.lineId in state.starredLines,
                            onStar = { onStar(arrival) },
                        ) { onBell(arrival) }
                        HorizontalDivider(
                            Modifier.padding(start = DesignTokens.arrivalDividerInset),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
    val attribution = state.coordinatesAttribution
    if (state.lat != null && attribution != null) {
        item(key = "stop-attribution", contentType = "attribution") {
            Text(
                stringResource(R.string.osm_attribution, attribution),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingMd),
            )
        }
    }
}

/** `Live` y `STALE` traen filas; `NO_SERVICE`, `Empty` y `Error` no. */
private val StopDetailUiState.showsArrivals: Boolean
    get() = when (val current = view) {
        ArrivalsView.Loading, ArrivalsView.Error, is ArrivalsView.Empty -> false
        is ArrivalsView.Live -> true
        is ArrivalsView.Degraded -> current.reason == DegradedReason.STALE
    }

@Composable
private fun ArrivalsHeader(refreshing: Boolean, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.stop_next_arrivals), modifier = Modifier.weight(1f))
        Box(Modifier.size(DesignTokens.touchTargetMin), contentAlignment = Alignment.Center) {
            if (refreshing) {
                CircularProgressIndicator(
                    Modifier.size(DesignTokens.progressIndicatorSize),
                    strokeWidth = DesignTokens.progressIndicatorStroke,
                )
            } else {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        }
    }
}

/**
 * Signature Moment 1 (`NextBusHeroBoard`): eleva la primera llegada en vivo o programada
 * a un tablero protagonista con raíl cromático Dbus, cuenta atrás `42.sp` y acciones rápidas `48.dp`.
 */
@Composable
internal fun NextBusHeroBoard(
    row: ArrivalRow,
    colorArgb: Int?,
    stopName: String,
    starred: Boolean,
    onStar: () -> Unit,
    onBell: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrival = row.arrival
    val colors = LocalStatusColors.current
    val isLive = arrival.source == ArrivalSource.LIVE
    val metricColor = if (isLive) colors.live else colors.scheduled
    val accent = lineColor(colorArgb)

    AccentRailCard(accentColor = accent, modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingSm,
                top = DesignTokens.spacingMd,
                bottom = DesignTokens.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                modifier = Modifier.padding(end = DesignTokens.spacingSm),
            ) {
                Text(
                    text = stringResource(R.string.stop_next_bus).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = if (isLive) colors.liveContainer else colors.scheduledContainer,
                    shape = RoundedCornerShape(DesignTokens.cornerRadiusPill),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                        modifier = Modifier.padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
                    ) {
                        if (isLive) {
                            LiveRadarBeacon(
                                isLive = true,
                                liveColor = colors.onLiveContainer,
                                scheduledColor = colors.scheduled,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = colors.scheduled,
                                modifier = Modifier.size(DesignTokens.iconSmall),
                            )
                        }
                        Text(
                            text = stringResource(if (isLive) R.string.source_live else R.string.source_scheduled),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLive) colors.onLiveContainer else colors.scheduled,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                modifier = Modifier.padding(end = DesignTokens.spacingSm),
            ) {
                LineBadge(arrival.lineId, colorArgb, size = DesignTokens.lineBadgeLarge)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = arrival.destination.ifEmpty { stringResource(R.string.destination_unknown) },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    arrival.clockTime?.let { clock ->
                        Text(
                            text = clock,
                            style = MonoClock,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedMinutesText(
                    minutes = row.minutes,
                    formattedText = if (row.minutes <= 0) stringResource(R.string.minutes_now) else stringResource(R.string.minutes_short, row.minutes),
                    style = CountdownHero,
                    color = metricColor,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FavoriteStar(
                        checked = starred,
                        onToggle = onStar,
                        description = stringResource(R.string.a11y_favorite_stop_line, arrival.lineId, stopName),
                    )
                    IconButton(
                        onClick = onBell,
                        modifier = Modifier.size(DesignTokens.touchTargetMin),
                    ) {
                        Icon(
                            Icons.Outlined.NotificationAdd,
                            contentDescription = stringResource(R.string.a11y_alert_for_arrival, arrival.lineId, arrival.destination),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Aviso de Dbus, carga, vacío, sin servicio o error.
 * Todas las ramas con múltiples elementos se envuelven en un `Column` para evitar solapamiento en `AnimatedContent`.
 */
@Composable
internal fun ArrivalsStatus(
    view: ArrivalsView,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberEffectiveMotionTokens()
    AnimatedContent(
        targetState = view,
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
    ) { currentView ->
        when (currentView) {
            ArrivalsView.Loading -> Column {
                SkeletonList(rows = 1, variant = SkeletonVariant.HERO)
                SkeletonList(rows = 3, variant = SkeletonVariant.ARRIVAL_ROW)
            }
            is ArrivalsView.Empty -> Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                NoticeBanner(currentView.notice)
                StatusMessage(
                    illustration = { EmptyStopsIllustration() },
                    title = stringResource(R.string.stop_empty_title),
                    body = null,
                )
            }
            is ArrivalsView.Live -> NoticeBanner(currentView.notice)
            is ArrivalsView.Degraded -> when (currentView.reason) {
                DegradedReason.NO_SERVICE -> StatusMessage(
                    illustration = { NoServiceIllustration() },
                    title = stringResource(R.string.stop_no_service_title),
                    body = currentView.notice ?: stringResource(R.string.stop_no_service_body),
                )
                DegradedReason.STALE -> Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    InfoBanner(
                        visible = true,
                        text = stringResource(R.string.stop_stale, clockOf(currentView.fetchedAtMs)),
                        warning = true,
                        modifier = Modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingXs),
                    ) {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    NoticeBanner(currentView.notice)
                }
            }
            ArrivalsView.Error -> StatusMessage(
                illustration = { ErrorSignalIllustration() },
                title = stringResource(R.string.stop_error_title),
                body = stringResource(R.string.stop_error_body),
                action = stringResource(R.string.retry) to onRetry,
            )
        }
    }
}

/** `avisos` de Dbus: visibles también cuando hay datos. */
@Composable
private fun NoticeBanner(text: String?) {
    if (text == null) return
    InfoBanner(
        visible = true,
        text = stringResource(R.string.stop_notice, text),
        warning = true,
        modifier = Modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingXs),
    )
}

@Composable
private fun ArrivalRow(
    arrival: Arrival,
    minutes: Int,
    colorArgb: Int?,
    stopName: String,
    starred: Boolean,
    onStar: () -> Unit,
    onBell: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.arrivalRowMinHeight)
            .padding(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingXs,
                top = DesignTokens.spacingXs,
                bottom = DesignTokens.spacingXs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        LineBadge(arrival.lineId, colorArgb)
        Column(Modifier.weight(1f)) {
            Text(
                text = arrival.destination.ifEmpty { stringResource(R.string.destination_unknown) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            arrival.clockTime?.let { clock ->
                Text(
                    text = clock,
                    style = MonoClock,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        MinutesLabel(minutes, arrival.source)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FavoriteStar(
                checked = starred,
                onToggle = onStar,
                description = stringResource(R.string.a11y_favorite_stop_line, arrival.lineId, stopName),
            )
            IconButton(
                onClick = onBell,
                modifier = Modifier.size(DesignTokens.touchTargetMin),
            ) {
                Icon(
                    Icons.Outlined.NotificationAdd,
                    contentDescription = stringResource(R.string.a11y_alert_for_arrival, arrival.lineId, arrival.destination),
                )
            }
        }
    }
}

@Composable
private fun StatusMessage(
    illustration: @Composable () -> Unit,
    title: String,
    body: String?,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DesignTokens.spacingXl,
                vertical = DesignTokens.stateMessageVerticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        illustration()
        Spacer(Modifier.size(DesignTokens.spacingXs))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Button(
                onClick = action.second,
                modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
            ) {
                Text(action.first)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun StopDetailLivePreview() {
    val now = 1_700_000_000_000L
    val a1 = Arrival("28", "32", "Ospitaleak", 2, "10:14", ArrivalSource.LIVE, now, null)
    val a2 = Arrival("5", "5", "Benta Berri", 7, "10:19", ArrivalSource.LIVE, now, null)
    val a3 = Arrival("26", "26", "Martutene", 14, "10:26", ArrivalSource.SCHEDULED, now, null)
    val state = StopDetailUiState(
        stopId = "1281",
        name = "Boulevard 15",
        lat = 43.32,
        lon = -1.98,
        lineIds = listOf("5", "26", "28"),
        view = ArrivalsView.Live(listOf(a1, a2, a3), null, now),
        rows = listOf(ArrivalRow("1", a1, 2), ArrivalRow("2", a2, 7), ArrivalRow("3", a3, 14)),
        starred = true,
        starredLines = setOf("28"),
    )
    BusDonostiaTheme {
        StopDetailContent(
            state = state,
            highlightLine = null,
            sharedOrigin = null,
            hasDirections = true,
            onBack = {},
            onOpenDirections = {},
            onToggleStopStar = {},
            onSelectLine = {},
            onToggleFilteredLineStar = {},
            onToggleArrivalStar = {},
            onRefresh = {},
            onCreateAlert = { _, _, _ -> },
        )
    }
}
