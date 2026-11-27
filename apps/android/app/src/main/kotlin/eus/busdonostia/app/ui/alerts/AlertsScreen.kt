package eus.busdonostia.app.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertDetail
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertMode
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.ComputedAlertStatus
import eus.busdonostia.app.domain.FeedHint
import eus.busdonostia.app.domain.feedHint
import eus.busdonostia.app.engine.CHANNEL_ALARM
import eus.busdonostia.app.engine.CHANNEL_ARRIVALS
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.AccentRailCard
import eus.busdonostia.app.ui.components.BrandPullToRefreshBox
import eus.busdonostia.app.ui.components.EmptyAlertsIllustration
import eus.busdonostia.app.ui.components.InfoBanner
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.LocalBottomDockInset
import eus.busdonostia.app.ui.components.MinutesLabel
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.SkeletonVariant
import eus.busdonostia.app.ui.components.clockOf
import eus.busdonostia.app.ui.components.lineColor
import eus.busdonostia.app.ui.components.motionAnimateItem
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.system.BlockedChannelNotice
import eus.busdonostia.app.ui.system.SystemStatus
import eus.busdonostia.app.ui.system.blockedChannelNoticeForAlerts
import eus.busdonostia.app.ui.system.channelSettingsIntent
import eus.busdonostia.app.ui.system.notificationSettingsIntent
import eus.busdonostia.app.ui.system.openSettings
import eus.busdonostia.app.ui.system.readSystemStatus
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

/** A04 · Mis avisos. */
@Composable
fun AlertsScreen(
    onNewAlert: () -> Unit,
    onEditAlert: (String) -> Unit,
    onChooseStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { AlertsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollWhileVisible() }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var system by remember { mutableStateOf(readSystemStatus(context)) }
    LifecycleResumeEffect(Unit) {
        system = readSystemStatus(context)
        onPauseOrDispose { }
    }
    val deletedText = stringResource(R.string.alert_deleted)
    val undoText = stringResource(R.string.undo)
    val onDelete: (AlertRule) -> Unit = { rule ->
        vm.delete(rule)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(deletedText, undoText, withDismissAction = false, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete(rule)
        }
    }

    AlertsContentView(
        state = state,
        system = system,
        snackbar = snackbar,
        onNewAlert = onNewAlert,
        onEditAlert = onEditAlert,
        onChooseStop = onChooseStop,
        onOpenSettings = onOpenSettings,
        onOpenNotificationSettings = { context.openSettings(notificationSettingsIntent(context)) },
        onOpenChannelSettings = { notice ->
            val intent = when (notice) {
                BlockedChannelNotice.BOTH -> notificationSettingsIntent(context)
                BlockedChannelNotice.ARRIVALS -> channelSettingsIntent(context, CHANNEL_ARRIVALS)
                BlockedChannelNotice.ALARM, BlockedChannelNotice.NONE -> channelSettingsIntent(context, CHANNEL_ALARM)
            }
            context.openSettings(intent)
        },
        onRefresh = vm::refresh,
        onSetActive = vm::setActive,
        onDelete = onDelete,
    )
}

@Composable
internal fun AlertsContentView(
    state: AlertsUiState,
    system: SystemStatus,
    snackbar: SnackbarHostState,
    onNewAlert: () -> Unit,
    onEditAlert: (String) -> Unit,
    onChooseStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenChannelSettings: (BlockedChannelNotice) -> Unit,
    onRefresh: () -> Unit,
    onSetActive: (AlertRule, Boolean) -> Unit,
    onDelete: (AlertRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomDockInset = LocalBottomDockInset.current
    val haptic = LocalHapticFeedback.current
    val channelNotice = blockedChannelNoticeForAlerts(system, state.active.map { it.rule.mode })
    val channelsBannerVisible = channelNotice != BlockedChannelNotice.NONE
    val channelsBannerTextRes = when (channelNotice) {
        BlockedChannelNotice.BOTH -> R.string.alerts_channels_blocked
        BlockedChannelNotice.ARRIVALS -> R.string.alerts_channel_arrivals_blocked
        BlockedChannelNotice.ALARM, BlockedChannelNotice.NONE -> R.string.alerts_channel_alarm_blocked
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = stringResource(R.string.alerts_title)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_open))
                }
            }
            when {
                state.loading -> SkeletonList(
                    rows = 3,
                    rowHeight = DesignTokens.alertSkeletonHeight,
                    variant = SkeletonVariant.CARD,
                )
                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.alerts_empty_title),
                    body = stringResource(R.string.alerts_empty_body),
                    actions = listOf(EmptyAction(stringResource(R.string.alerts_empty_cta), primary = true, onClick = onChooseStop)),
                    modifier = Modifier.padding(bottom = bottomDockInset),
                    illustration = { EmptyAlertsIllustration() },
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
                            bottom = DesignTokens.bottomDockClearance + bottomDockInset,
                        ),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                    ) {
                        item(key = "banner-notifications-off", contentType = "info_banner") {
                            InfoBanner(
                                visible = !system.notificationsEnabled,
                                text = stringResource(R.string.alerts_notifications_off),
                                warning = true,
                            ) {
                                TextButton(
                                    onClick = onOpenNotificationSettings,
                                    modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
                                ) { Text(stringResource(R.string.alerts_notifications_enable)) }
                            }
                        }
                        item(key = "banner-channels-blocked", contentType = "info_banner") {
                            InfoBanner(
                                visible = channelsBannerVisible,
                                text = stringResource(channelsBannerTextRes),
                                warning = true,
                            ) {
                                TextButton(
                                    onClick = { onOpenChannelSettings(channelNotice) },
                                    modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
                                ) { Text(stringResource(R.string.alerts_notifications_enable)) }
                            }
                        }
                        item(key = "banner-error", contentType = "info_banner") {
                            InfoBanner(
                                visible = state.anyError,
                                text = stringResource(R.string.alerts_error_banner),
                                warning = true,
                            ) {
                                TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin)) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                        group(
                            title = R.string.alerts_section_active,
                            items = state.active,
                            state = state,
                            onSetActive = { rule, active ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetActive(rule, active)
                            },
                            onEdit = onEditAlert,
                            onDelete = onDelete,
                        )
                        group(
                            title = R.string.alerts_section_paused,
                            items = state.silenced,
                            state = state,
                            onSetActive = { rule, active ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetActive(rule, active)
                            },
                            onEdit = onEditAlert,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
        if (!state.loading && !state.isEmpty) {
            ExtendedFloatingActionButton(
                onClick = onNewAlert,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.alerts_new), maxLines = 1) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = DesignTokens.spacingLg, bottom = DesignTokens.spacingLg + bottomDockInset),
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = DesignTokens.spacingLg,
                    end = DesignTokens.spacingLg,
                    bottom = DesignTokens.floatingDockHeight + DesignTokens.spacingLg + bottomDockInset,
                ),
        )
    }
}

private fun LazyListScope.group(
    title: Int,
    items: List<AlertItem>,
    state: AlertsUiState,
    onSetActive: (AlertRule, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (AlertRule) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "section-$title", contentType = "section_label") {
        SectionLabel(stringResource(title), horizontalPadding = 0.dp, trailing = items.size.toString())
    }
    items(items, key = { it.rule.id }, contentType = { "alert_card" }) { item ->
        AlertCard(
            item = item,
            stopView = state.stopViews[item.rule.stopId],
            colorArgb = state.lineColors[item.rule.lineId],
            onActiveChange = { onSetActive(item.rule, it) },
            onEdit = { onEdit(item.rule.id) },
            onDelete = { onDelete(item.rule) },
            modifier = motionAnimateItem(),
        )
    }
}

@Composable
private fun AlertCard(
    item: AlertItem,
    stopView: ArrivalsView?,
    colorArgb: Int?,
    onActiveChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = item.rule
    val colors = LocalStatusColors.current
    val accent = if (rule.active) lineColor(colorArgb) else MaterialTheme.colorScheme.outlineVariant

    AccentRailCard(accentColor = accent, modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.spacingLg,
                top = DesignTokens.spacingMd,
                end = DesignTokens.spacingXs,
                bottom = DesignTokens.spacingXs,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
            ) {
                LineBadge(rule.lineId, colorArgb, size = DesignTokens.lineBadgeMinWidth)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    Text(
                        text = alertDestination(rule),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.alert_subtitle,
                            rule.stopName.ifBlank { stringResource(R.string.stop_unnamed) },
                            rule.minutesAhead,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val arrival = item.nextArrival
                if (rule.active && arrival != null && item.remainingMin != null) {
                    Box(Modifier.padding(end = DesignTokens.spacingMd)) {
                        MinutesLabel(item.remainingMin, arrival.source)
                    }
                }
            }
            feedHintText(rule, stopView)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.padding(top = DesignTokens.spacingXs, end = DesignTokens.spacingMd),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                modifier = Modifier.padding(top = DesignTokens.spacingXs),
            ) {
                AlertStatusLabel(item)
                if (rule.mode == AlertMode.ALARM) {
                    Surface(
                        color = colors.alarmContainer,
                        shape = RoundedCornerShape(DesignTokens.cornerRadiusPill),
                    ) {
                        Icon(
                            Icons.Outlined.Alarm,
                            contentDescription = stringResource(R.string.alert_mode_alarm),
                            modifier = Modifier
                                .padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs)
                                .size(DesignTokens.iconSmall),
                            tint = colors.onAlarmContainer,
                        )
                    }
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(DesignTokens.touchTargetMin)) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.alert_edit))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(DesignTokens.touchTargetMin)) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.alert_delete))
                }
                val switchLabel = stringResource(R.string.alert_active_switch)
                Switch(
                    checked = rule.active,
                    onCheckedChange = onActiveChange,
                    modifier = Modifier
                        .padding(end = DesignTokens.spacingSm)
                        .heightIn(min = DesignTokens.touchTargetMin)
                        .semantics { contentDescription = switchLabel },
                )
            }
        }
    }
}

/** Sin servicio, error o datos viejos de la parada de un aviso activo; null si no hay nada que decir. */
@Composable
private fun feedHintText(rule: AlertRule, view: ArrivalsView?): String? = when (val hint = feedHint(rule, view)) {
    FeedHint.FeedError -> stringResource(R.string.alert_feed_error)
    FeedHint.NoService -> stringResource(R.string.alert_feed_no_service)
    is FeedHint.Stale -> stringResource(R.string.stop_stale, clockOf(hint.fetchedAtMs))
    null -> null
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlertsListPreview() {
    val now = 1_700_000_000_000L
    val rule1 = AlertRule("1", "1281", "Boulevard 15", "28", "Ospitaleak", 5, true, now, AlertMode.ALARM)
    val rule2 = AlertRule("2", "340", "Pío Baroja 10", "5", "Benta Berri", 8, false, now, AlertMode.NOTIFICATION)
    val arrival = Arrival("28", "32", "Ospitaleak", 4, "10:15", ArrivalSource.LIVE, now, null)
    val state = AlertsUiState(
        loading = false,
        active = listOf(AlertItem(rule1, ComputedAlertStatus.TRIGGERED, AlertDetail.NONE, arrival, 4)),
        silenced = listOf(AlertItem(rule2, ComputedAlertStatus.INACTIVE, AlertDetail.PAUSED, null, null)),
    )
    BusDonostiaTheme {
        AlertsContentView(
            state = state,
            system = SystemStatus(notificationsEnabled = true, batteryUnrestricted = true),
            snackbar = remember { SnackbarHostState() },
            onNewAlert = {},
            onEditAlert = {},
            onChooseStop = {},
            onOpenSettings = {},
            onOpenNotificationSettings = {},
            onOpenChannelSettings = {},
            onRefresh = {},
            onSetActive = { _, _ -> },
            onDelete = {},
        )
    }
}
