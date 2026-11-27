package eus.busdonostia.app.ui.stops

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.EmptyStopsIllustration
import eus.busdonostia.app.ui.components.ErrorSignalIllustration
import eus.busdonostia.app.ui.components.FavoriteStar
import eus.busdonostia.app.ui.components.InfoBanner
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.LocalBottomDockInset
import eus.busdonostia.app.ui.components.MotionFilterChip
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SearchEmptyIllustration
import eus.busdonostia.app.ui.components.SearchPill
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SharedKeys
import eus.busdonostia.app.ui.components.SharedOrigins
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.motionAnimateItem
import eus.busdonostia.app.ui.components.optionalSharedElement
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.MonoClock
import kotlinx.coroutines.CancellationException

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A02 · Paradas. [onOpenStop] recibe parada y, si la fila es parada+línea, la línea. */
@Composable
fun StopsScreen(onOpenStop: (stopId: String, lineId: String?, from: String?) -> Unit) {
    val vm = appViewModel { StopsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        askedOnce = true
        vm.onPermissionResult(granted)
    }
    val askLocation: () -> Unit = {
        val activity = context.findActivity()
        val canAskAgain = !askedOnce || (
            activity != null && LOCATION_PERMISSIONS.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            )
        if (!canAskAgain) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        } else {
            launcher.launch(LOCATION_PERMISSIONS)
        }
    }

    PredictiveBackHandler(enabled = state.query.isNotBlank()) { progress ->
        try {
            progress.collect { }
            vm.onQueryChange("")
        } catch (_: CancellationException) {
        }
    }

    StopsContentView(
        state = state,
        onQueryChange = vm::onQueryChange,
        onTabChange = vm::onTabChange,
        onRefreshLocation = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (state.location == LocationState.NoPermission) askLocation() else vm.refreshLocation()
        },
        onOpenStop = onOpenStop,
        onToggleStar = vm::toggleStar,
        onAskLocation = askLocation,
        onRetryLocation = vm::refreshLocation,
    )
}

@Composable
internal fun StopsContentView(
    state: StopsUiState,
    onQueryChange: (String) -> Unit,
    onTabChange: (StopsTab) -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenStop: (stopId: String, lineId: String?, from: String?) -> Unit,
    onToggleStar: (StopRow) -> Unit,
    onAskLocation: () -> Unit,
    onRetryLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.stops_title)) {
            IconButton(onClick = onRefreshLocation) {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.stops_refresh_location))
            }
        }
        SearchPill(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.stops_search_hint),
            modifier = Modifier.padding(horizontal = DesignTokens.spacingLg),
        )
        if (state.query.isBlank()) {
            TabChips(state.tab, onTabChange)
        } else {
            Spacer(Modifier.padding(top = DesignTokens.spacingSm))
        }
        when (state.catalogReady) {
            null -> SkeletonList()
            false -> EmptyState(
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.catalog_error_body),
                illustration = { ErrorSignalIllustration() },
            )
            true -> StopsListContent(state, onOpenStop, onToggleStar, onAskLocation, onRetryLocation, onTabChange)
        }
    }
}

@Composable
private fun TabChips(selected: StopsTab, onSelect: (StopsTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
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
                        modifier = Modifier.size(DesignTokens.iconSmall),
                    )
                },
            )
        }
    }
}

@Composable
private fun StopsListContent(
    state: StopsUiState,
    onOpenStop: (String, String?, String?) -> Unit,
    onStar: (StopRow) -> Unit,
    askLocation: () -> Unit,
    retryLocation: () -> Unit,
    onTab: (StopsTab) -> Unit,
) {
    val bottomDockInset = LocalBottomDockInset.current
    if (state.query.isNotBlank()) {
        if (state.rows.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.stops_no_results_title, state.query),
                body = stringResource(R.string.stops_no_results_body),
                modifier = Modifier.imePadding().padding(bottom = bottomDockInset),
                illustration = { SearchEmptyIllustration() },
            )
        } else {
            StopList(bottomDockInset = bottomDockInset, imeAware = true) {
                section(R.string.stops_section_results)
                rows(state.rows, state, "query", onOpenStop, onStar)
            }
        }
        return
    }
    when (state.tab) {
        StopsTab.NEARBY -> when (val location = state.location) {
            LocationState.Locating -> SkeletonList()
            is LocationState.Located -> StopList(bottomDockInset = bottomDockInset) {
                section(R.string.stops_section_nearby, R.string.stops_by_distance)
                rows(state.rows, state, state.tab.name, onOpenStop, onStar)
                item(key = "nearby-footnote", contentType = "footnote") {
                    NearbyFootnote(state.coordinatesAttribution, state.walkMetersPerMin)
                }
            }
            LocationState.NoPermission, LocationState.Unavailable -> StopList(bottomDockInset = bottomDockInset) {
                item(key = "nearby-location", contentType = "info_banner") {
                    if (location == LocationState.NoPermission) {
                        LocationPrompt(askLocation)
                    } else {
                        InfoBanner(
                            visible = true,
                            text = stringResource(R.string.stops_location_unavailable),
                            warning = true,
                            modifier = Modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
                        ) {
                            TextButton(onClick = retryLocation, modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin)) {
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
                    title = stringResource(R.string.stops_favorites_empty_title),
                    body = stringResource(R.string.stops_favorites_empty_body),
                    actions = listOf(EmptyAction(stringResource(R.string.home_cta_nearby), primary = true) { onTab(StopsTab.NEARBY) }),
                    modifier = Modifier.padding(bottom = bottomDockInset),
                    illustration = { EmptyStopsIllustration() },
                )
            } else {
                StopList(bottomDockInset = bottomDockInset) { rows(state.rows, state, state.tab.name, onOpenStop, onStar) }
            }
        StopsTab.RECENTS ->
            if (state.rows.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.stops_recents_empty_title),
                    body = stringResource(R.string.stops_recents_empty_body, state.recentStopsLimit),
                    actions = listOf(EmptyAction(stringResource(R.string.home_cta_nearby), primary = true) { onTab(StopsTab.NEARBY) }),
                    modifier = Modifier.padding(bottom = bottomDockInset),
                    illustration = { EmptyStopsIllustration() },
                )
            } else {
                StopList(bottomDockInset = bottomDockInset) { rows(state.rows, state, state.tab.name, onOpenStop, onStar) }
            }
    }
}

@Composable
private fun StopList(
    bottomDockInset: Dp,
    imeAware: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(if (imeAware) Modifier.imePadding() else Modifier),
        contentPadding = PaddingValues(bottom = DesignTokens.spacingLg + bottomDockInset),
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
    BorderedCard(
        modifier = Modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
            ) {
                EmptyStopsIllustration(size = DesignTokens.headerMinHeight)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    Text(stringResource(R.string.stops_location_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.stops_location_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().heightIn(min = DesignTokens.touchTargetMin),
            ) {
                Text(stringResource(R.string.stops_location_allow))
            }
        }
    }
}

@Composable
private fun NearbyFootnote(attribution: String?, walkMetersPerMin: Int) {
    Column(
        Modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
    ) {
        Text(
            stringResource(R.string.stops_walk_estimate_note, walkMetersPerMin),
            style = MaterialTheme.typography.bodySmall,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopCard(
    row: StopRow,
    lineColors: Map<String, Int>,
    origin: String,
    onClick: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorderedCard(
        modifier = modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingXxs),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.spacingLg,
                top = DesignTokens.spacingXs,
                bottom = DesignTokens.spacingMd,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = DesignTokens.spacingMd),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
                    ) {
                        row.lineIds.forEach { line ->
                            LineBadge(
                                lineId = line,
                                colorArgb = lineColors[line],
                                modifier = Modifier.optionalSharedElement(SharedKeys.line(row.stopId, line, origin)),
                            )
                        }
                    }
                    row.mainDestination?.let { dest ->
                        Text(
                            text = dest,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = DesignTokens.spacingXs),
                        )
                    }
                }
                if (row.meters != null && row.walkMinutes != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(DesignTokens.cornerRadiusPill),
                        modifier = Modifier.padding(start = DesignTokens.spacingSm),
                    ) {
                        Text(
                            text = stringResource(R.string.stops_distance_walk, distanceText(row.meters), row.walkMinutes),
                            style = MonoClock,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = DesignTokens.spacingXs),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun StopsNearbyPreview() {
    val state = StopsUiState(
        catalogReady = true,
        tab = StopsTab.NEARBY,
        location = LocationState.Located(43.32, -1.98),
        rows = listOf(
            StopRow("1281", "Boulevard 15", listOf("5", "28", "13"), meters = 140, walkMinutes = 2, mainDestination = "Hacia Ospitaleak", starred = true),
            StopRow("340", "Pío Baroja 10", listOf("16", "24"), meters = 420, walkMinutes = 5, mainDestination = "Hacia Centro", starred = false),
        ),
    )
    BusDonostiaTheme {
        StopsContentView(
            state = state,
            onQueryChange = {},
            onTabChange = {},
            onRefreshLocation = {},
            onOpenStop = { _, _, _ -> },
            onToggleStar = {},
            onAskLocation = {},
            onRetryLocation = {},
        )
    }
}
