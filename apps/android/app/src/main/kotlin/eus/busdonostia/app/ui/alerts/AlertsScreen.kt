package eus.busdonostia.app.ui.alerts

import eus.busdonostia.app.ui.components.motionAnimateItem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.FeedHint
import eus.busdonostia.app.domain.feedHint
import eus.busdonostia.app.ui.appViewModel
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.InfoBanner
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.MinutesLabel
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.system.notificationSettingsIntent
import eus.busdonostia.app.ui.system.openSettings
import eus.busdonostia.app.ui.system.readSystemStatus
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
    // Permiso real, releído al volver de Ajustes: sin notificaciones el motor no puede avisar.
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = stringResource(R.string.alerts_title)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_open))
                }
            }
            when {
                state.loading -> SkeletonList(rows = 3, rowHeight = 112.dp)
                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.alerts_empty_title),
                    body = stringResource(R.string.alerts_empty_body),
                    actions = listOf(EmptyAction(stringResource(R.string.alerts_empty_cta), primary = true, onClick = onChooseStop)),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "banner-notifications-off", contentType = "info_banner") {
                        InfoBanner(
                            visible = !system.notificationsEnabled,
                            text = stringResource(R.string.alerts_notifications_off),
                            warning = true,
                        ) {
                            TextButton(
                                onClick = { context.openSettings(notificationSettingsIntent(context)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.alerts_notifications_enable)) }
                        }
                    }
                    item(key = "banner-error", contentType = "info_banner") {
                        InfoBanner(
                            visible = state.anyError,
                            text = stringResource(R.string.alerts_error_banner),
                            warning = true,
                        ) {
                            TextButton(onClick = vm::refresh, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    group(R.string.alerts_section_active, state.active, state, vm, onEditAlert, onDelete)
                    group(R.string.alerts_section_paused, state.silenced, state, vm, onEditAlert, onDelete)
                }
            }
        }
        if (!state.loading && !state.isEmpty) {
            ExtendedFloatingActionButton(
                onClick = onNewAlert,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.alerts_new), maxLines = 1) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

private fun LazyListScope.group(
    title: Int,
    items: List<AlertItem>,
    state: AlertsUiState,
    vm: AlertsViewModel,
    onEdit: (String) -> Unit,
    onDelete: (AlertRule) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "section-$title", contentType = "section_label") {
        SectionLabel(stringResource(title), modifier = Modifier.padding(horizontal = 0.dp), trailing = items.size.toString())
    }
    items(items, key = { it.rule.id }, contentType = { "alert_card" }) { item ->
        AlertCard(
            item = item,
            stopView = state.stopViews[item.rule.stopId],
            colorArgb = state.lineColors[item.rule.lineId],
            onActiveChange = { vm.setActive(item.rule, it) },
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
    BorderedCard(modifier = modifier) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LineBadge(rule.lineId, colorArgb)
                Text(
                    alertDestination(rule),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val arrival = item.nextArrival
                if (rule.active && arrival != null && item.remainingMin != null) {
                    Box(Modifier.padding(end = 12.dp)) { MinutesLabel(item.remainingMin, arrival.source) }
                }
            }
            Text(
                stringResource(
                    R.string.alert_subtitle,
                    rule.stopName.ifBlank { stringResource(R.string.stop_unnamed) },
                    rule.minutesAhead,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, end = 12.dp),
            )
            feedHintText(rule, stopView)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = 12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlertStatusLabel(item, Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.alert_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.alert_delete))
                }
                val switchLabel = stringResource(R.string.alert_active_switch)
                Switch(
                    checked = rule.active,
                    onCheckedChange = onActiveChange,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .heightIn(min = 48.dp)
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
