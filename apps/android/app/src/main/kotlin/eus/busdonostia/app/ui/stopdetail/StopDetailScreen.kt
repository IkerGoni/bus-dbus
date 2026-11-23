package eus.busdonostia.app.ui.stopdetail

import eus.busdonostia.app.ui.components.motionAnimateItem

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import eus.busdonostia.app.ui.components.optionalSharedElement
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.DegradedReason
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.BrandPullToRefreshBox
import eus.busdonostia.app.ui.components.FavoriteStar
import eus.busdonostia.app.ui.components.InfoBanner
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.MinutesLabel
import eus.busdonostia.app.ui.components.MotionFilterChip
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SharedKeys
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.LocalStatusColors
import eus.busdonostia.app.ui.theme.MonoFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun clockOf(ms: Long): String = HH_MM.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

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
    // `geo:` solo si la parada tiene coordenadas y hay una app que lo abra (nada de botones sin acción).
    val directions: Intent? = remember(state.lat, state.lon, state.name) {
        val lat = state.lat
        val lon = state.lon
        if (lat == null || lon == null) return@remember null
        val label = Uri.encode(state.name?.takeIf { it.isNotBlank() } ?: unnamedStop)
        Intent(Intent.ACTION_VIEW, "geo:$lat,$lon?q=$lat,$lon($label)".toUri())
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    Column(Modifier.fillMaxSize()) {
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
            if (directions != null) {
                IconButton(onClick = { context.startActivity(directions) }) {
                    Icon(Icons.Filled.Directions, contentDescription = stringResource(R.string.stop_directions))
                }
            }
            FavoriteStar(
                checked = state.starred,
                onToggle = vm::toggleStopStar,
                description = stringResource(R.string.a11y_favorite_stop, state.name ?: stringResource(R.string.stop_unnamed)),
            )
        }
        LineChips(stopId, state, highlightLine, sharedOrigin, vm::selectLine)
        val selected = state.selectedLine
        if (selected != null) {
            TextButton(
                onClick = { vm.toggleLineStar() },
                modifier = Modifier.padding(horizontal = DesignTokens.spacingSm).heightIn(min = DesignTokens.touchTargetMin),
            ) {
                Icon(
                    if (state.lineStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = if (state.lineStarred) LocalStatusColors.current.favorite else LocalStatusColors.current.scheduled,
                )
                Spacer(Modifier.width(DesignTokens.spacingSm))
                Text(
                    stringResource(if (state.lineStarred) R.string.stop_line_starred else R.string.stop_line_star, selected),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            Arrivals(
                state = state,
                onRetry = vm::refresh,
                onStar = { vm.toggleLineStar(it.lineId) },
            ) { arrival -> onCreateAlert(stopId, arrival.lineId, arrival.destination.ifEmpty { null }) }
        }
        // Pie fijo: aviso en esta parada (A06 con la parada precargada).
        HorizontalDivider(color = LocalStatusColors.current.border)
        Button(
            onClick = { onCreateAlert(stopId, null, null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm)
                .heightIn(min = DesignTokens.touchTargetMin),
        ) {
            Icon(Icons.Outlined.NotificationAdd, contentDescription = null)
            Spacer(Modifier.width(DesignTokens.spacingSm))
            Text(stringResource(R.string.stop_create_alert), maxLines = 1)
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
            // Solo el chip de la línea con la que se abrió la parada lleva shared element; el resto, sin él.
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
private fun Arrivals(state: StopDetailUiState, onRetry: () -> Unit, onStar: (Arrival) -> Unit, onBell: (Arrival) -> Unit) {
    BrandPullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = DesignTokens.spacingLg),
        ) {
            stopDetailItems(state = state, onRetry = onRetry, onStar = onStar, onBell = onBell)
        }
    }
}

/**
 * Contenido de A05 como un único `LazyListScope`: cabecera, bloque de estado y llegadas son
 * ítems reales de la lista. Antes se emitían ítems desde dentro de otro `item`, lo que rompía
 * el reciclado de `LazyColumn` y el recuento de claves durante las transiciones.
 */
private fun LazyListScope.stopDetailItems(
    state: StopDetailUiState,
    onRetry: () -> Unit,
    onStar: (Arrival) -> Unit,
    onBell: (Arrival) -> Unit,
) {
    item(key = "stop-arrivals-header", contentType = "arrival_header") {
        ArrivalsHeader(refreshing = state.refreshing, onRetry = onRetry)
    }
    item(key = "stop-arrivals-status", contentType = "arrivals_status") {
        ArrivalsStatus(state = state, onRetry = onRetry)
    }
    if (state.showsArrivals) {
        if (state.rows.isEmpty()) {
            item(key = "stop-line-empty", contentType = "status_message") {
                StatusMessage(
                    Icons.Outlined.Inbox,
                    stringResource(R.string.stop_line_empty, state.selectedLine.orEmpty()),
                    null,
                )
            }
        } else {
            items(state.rows, key = { it.key }, contentType = { "arrival_row" }) { row ->
                val arrival = row.arrival
                Column(modifier = motionAnimateItem()) {
                    ArrivalRow(
                        arrival = arrival,
                        minutes = row.minutes,
                        colorArgb = state.lineColors[arrival.lineId],
                        stopName = state.name ?: stringResource(R.string.stop_unnamed),
                        starred = arrival.lineId in state.starredLines,
                        onStar = { onStar(arrival) },
                    ) { onBell(arrival) }
                    HorizontalDivider(
                        Modifier.padding(start = DesignTokens.arrivalDividerInset),
                        color = LocalStatusColors.current.border,
                    )
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

/** Aviso de Dbus, carga, vacío, sin servicio o error. Solo cabecera y mensajes: nunca filas. */
@Composable
private fun ArrivalsStatus(state: StopDetailUiState, onRetry: () -> Unit) {
    val motion = rememberEffectiveMotionTokens()
    AnimatedContent(
        targetState = state.view,
        contentKey = { it::class },
        transitionSpec = {
            (
                fadeIn(tween(motion.arrivalsStateFadeInMs)) + slideInVertically(tween(motion.arrivalsStateFadeInMs)) {
                    (it * motion.arrivalsStateSlideFraction).toInt()
                }
                ).togetherWith(fadeOut(tween(motion.arrivalsStateFadeOutMs)))
        },
        label = "arrivalsViewState",
    ) { view ->
        when (view) {
            ArrivalsView.Loading -> SkeletonList(rows = 3, rowHeight = DesignTokens.arrivalRowMinHeight)
            is ArrivalsView.Empty -> {
                NoticeBanner(view.notice)
                StatusMessage(Icons.Outlined.Inbox, stringResource(R.string.stop_empty_title), null)
            }
            is ArrivalsView.Live -> NoticeBanner(view.notice)
            is ArrivalsView.Degraded -> when (view.reason) {
                DegradedReason.NO_SERVICE -> StatusMessage(
                    Icons.Outlined.NightsStay,
                    stringResource(R.string.stop_no_service_title),
                    view.notice ?: stringResource(R.string.stop_no_service_body),
                )
                DegradedReason.STALE -> {
                    InfoBanner(
                        visible = true,
                        text = stringResource(R.string.stop_stale, clockOf(view.fetchedAtMs)),
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
                    NoticeBanner(view.notice)
                }
            }
            ArrivalsView.Error -> StatusMessage(
                Icons.Outlined.ErrorOutline,
                stringResource(R.string.stop_error_title),
                stringResource(R.string.stop_error_body),
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

/**
 * Llegada: badge, destino (con la hora debajo), minutos y dos acciones de 48 dp sobre
 * esa línea en esta parada: ★ favorita parada+línea y 🔔 aviso.
 */
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
        // Hora bajo el destino: deja sitio a ★ y 🔔 sin recortar el destino en móviles estrechos.
        Column(Modifier.weight(1f)) {
            Text(
                text = arrival.destination.ifEmpty { stringResource(R.string.destination_unknown) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            arrival.clockTime?.let {
                Text(
                    text = it,
                    fontFamily = MonoFamily,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        MinutesLabel(minutes, arrival.source)
        Row {
            FavoriteStar(
                checked = starred,
                onToggle = onStar,
                description = stringResource(R.string.a11y_favorite_stop_line, arrival.lineId, stopName),
            )
            // 🔔 48 dp: A06 con parada, línea y destino de esta llegada.
            IconButton(onClick = onBell) {
                Icon(
                    Icons.Outlined.NotificationAdd,
                    contentDescription = stringResource(R.string.a11y_alert_for_arrival, arrival.lineId, arrival.destination),
                )
            }
        }
    }
}

@Composable
private fun StatusMessage(icon: ImageVector, title: String, body: String?, action: Pair<String, () -> Unit>? = null) {
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(DesignTokens.iconLarge))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Button(onClick = action.second, modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin)) { Text(action.first) }
        }
    }
}
