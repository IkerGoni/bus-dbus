package eus.busdonostia.app.ui.stops

import eus.busdonostia.app.ui.components.motionAnimateItem

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.FavoriteStar
import eus.busdonostia.app.ui.components.InfoBanner
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.MotionFilterChip
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SearchPill
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SharedKeys
import eus.busdonostia.app.ui.components.SharedOrigins
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.optionalSharedElement
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

/** A02 · Paradas. [onOpenStop] recibe parada y, si la fila es parada+línea, la línea. */
@Composable
fun StopsScreen(onOpenStop: (stopId: String, lineId: String?, from: String?) -> Unit) {
    val vm = appViewModel { StopsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Tras una negativa, el sistema puede no volver a mostrar el diálogo: entonces se abre Ajustes.
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        denied = !granted
        vm.onPermissionResult(granted)
    }
    val askLocation: () -> Unit = {
        if (denied) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        } else {
            launcher.launch(LOCATION_PERMISSIONS)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.stops_title)) {
            IconButton(onClick = { if (state.location == LocationState.NoPermission) askLocation() else vm.refreshLocation() }) {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.stops_refresh_location))
            }
        }
        SearchPill(
            query = state.query,
            onQueryChange = vm::onQueryChange,
            placeholder = stringResource(R.string.stops_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (state.query.isBlank()) {
            TabChips(state.tab, vm::onTabChange)
        } else {
            Spacer(Modifier.padding(top = 8.dp))
        }
        when (state.catalogReady) {
            null -> SkeletonList()
            false -> EmptyState(stringResource(R.string.catalog_error_title), stringResource(R.string.catalog_error_body))
            true -> StopsContent(state, onOpenStop, vm::toggleStar, askLocation, vm::refreshLocation) { vm.onTabChange(it) }
        }
    }
}

@Composable
private fun TabChips(selected: StopsTab, onSelect: (StopsTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StopsTab.entries.forEach { tab ->
            MotionFilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        stringResource(
                            when (tab) {
                                StopsTab.NEARBY -> R.string.stops_tab_nearby
                                StopsTab.FAVORITES -> R.string.stops_tab_favorites
                                StopsTab.RECENTS -> R.string.stops_tab_recents
                            },
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                },
                leadingIcon = {
                    Icon(
                        when (tab) {
                            StopsTab.NEARBY -> Icons.Filled.NearMe
                            StopsTab.FAVORITES -> Icons.Filled.Star
                            StopsTab.RECENTS -> Icons.Outlined.History
                        },
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun StopsContent(
    state: StopsUiState,
    onOpenStop: (String, String?, String?) -> Unit,
    onStar: (StopRow) -> Unit,
    askLocation: () -> Unit,
    retryLocation: () -> Unit,
    onTab: (StopsTab) -> Unit,
) {
    if (state.query.isNotBlank()) {
        if (state.rows.isEmpty()) {
            EmptyState(stringResource(R.string.stops_no_results_title, state.query), stringResource(R.string.stops_no_results_body))
        } else {
            StopList {
                section(R.string.stops_section_results)
                rows(state.rows, state, "query", onOpenStop, onStar)
            }
        }
        return
    }
    when (state.tab) {
        StopsTab.NEARBY -> when (val location = state.location) {
            LocationState.Locating -> SkeletonList()
            is LocationState.Located -> StopList {
                section(R.string.stops_section_nearby, R.string.stops_by_distance)
                rows(state.rows, state, state.tab.name, onOpenStop, onStar)
                item(key = "nearby-footnote", contentType = "footnote") { NearbyFootnote(state.coordinatesAttribution) }
            }
            LocationState.NoPermission, LocationState.Unavailable -> StopList {
                item(key = "nearby-location", contentType = "info_banner") {
                    if (location == LocationState.NoPermission) {
                        LocationPrompt(askLocation)
                    } else {
                        InfoBanner(
                            visible = true,
                            text = stringResource(R.string.stops_location_unavailable),
                            warning = true,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            TextButton(onClick = retryLocation, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                if (state.recentRows.isNotEmpty()) {
                    section(R.string.stops_tab_recents)
                    rows(state.recentRows, state, "recent", onOpenStop, onStar)
                }
            }
        }
        StopsTab.FAVORITES ->
            if (state.rows.isEmpty()) {
                EmptyState(
                    stringResource(R.string.stops_favorites_empty_title),
                    stringResource(R.string.stops_favorites_empty_body),
                    listOf(EmptyAction(stringResource(R.string.home_cta_nearby), primary = true) { onTab(StopsTab.NEARBY) }),
                )
            } else {
                StopList { rows(state.rows, state, state.tab.name, onOpenStop, onStar) }
            }
        StopsTab.RECENTS ->
            if (state.rows.isEmpty()) {
                EmptyState(
                    stringResource(R.string.stops_recents_empty_title),
                    stringResource(R.string.stops_recents_empty_body),
                    listOf(EmptyAction(stringResource(R.string.home_cta_nearby), primary = true) { onTab(StopsTab.NEARBY) }),
                )
            } else {
                StopList { rows(state.rows, state, state.tab.name, onOpenStop, onStar) }
            }
    }
}

@Composable
private fun StopList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        content = content,
    )
}

private fun LazyListScope.section(label: Int, trailing: Int? = null) {
    item(key = "section-$label", contentType = "section_label") {
        SectionLabel(stringResource(label), trailing = trailing?.let { stringResource(it) })
    }
}

private fun LazyListScope.rows(
    rows: List<StopRow>,
    state: StopsUiState,
    originTag: String,
    onOpenStop: (String, String?, String?) -> Unit,
    onStar: (StopRow) -> Unit,
) {
    items(rows, key = { "${it.stopId}:${it.favoriteLineId.orEmpty()}" }, contentType = { "stop_card" }) { row ->
        val origin = SharedOrigins.stopRow(originTag, row.stopId, row.favoriteLineId)
        StopCard(
            row = row,
            lineColors = state.lineColors,
            origin = origin,
            onClick = { onOpenStop(row.stopId, row.favoriteLineId, origin) },
            onStar = { onStar(row) },
            modifier = motionAnimateItem(),
        )
    }
}

@Composable
private fun LocationPrompt(onAllow: () -> Unit) {
    BorderedCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stops_location_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.stops_location_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.stops_location_allow))
            }
        }
    }
}

@Composable
private fun NearbyFootnote(attribution: String?) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.stops_walk_estimate_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (attribution != null) {
            Text(
                stringResource(R.string.osm_attribution, attribution),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `140 m` / `1,2 km`. */
@Composable
fun distanceText(meters: Long): String =
    if (meters < 1_000) stringResource(R.string.distance_m, meters) else stringResource(R.string.distance_km, meters / 1_000.0)

@Composable
private fun StopCard(
    row: StopRow,
    lineColors: Map<String, Int>,
    origin: String,
    onClick: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorderedCard(modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp), onClick = onClick) {
        Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .optionalSharedElement(SharedKeys.stopTitle(row.stopId, origin)),
                )
                Spacer(Modifier.weight(1f))
                FavoriteStar(
                    checked = row.starred,
                    onToggle = onStar,
                    description = if (row.favoriteLineId != null) {
                        stringResource(R.string.a11y_favorite_stop_line, row.favoriteLineId, row.name)
                    } else {
                        stringResource(R.string.a11y_favorite_stop, row.name)
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.lineIds.forEach {
                            LineBadge(
                                lineId = it,
                                colorArgb = lineColors[it],
                                modifier = Modifier.optionalSharedElement(SharedKeys.line(row.stopId, it, origin)),
                            )
                        }
                    }
                    // Desambiguación legible para paradas homónimas: sentido/destino principal.
                    row.mainDestination?.let { dest ->
                        Text(
                            text = dest,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // Distancia en la fila de líneas para no recortar el nombre de la parada.
                if (row.meters != null && row.walkMinutes != null) {
                    Text(
                        text = stringResource(R.string.stops_distance_walk, distanceText(row.meters), row.walkMinutes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}
