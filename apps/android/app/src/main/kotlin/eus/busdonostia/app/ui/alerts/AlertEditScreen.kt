package eus.busdonostia.app.ui.alerts

import android.Manifest
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertDraft
import eus.busdonostia.app.domain.AlertMode
import eus.busdonostia.app.domain.CatalogStop
import eus.busdonostia.app.domain.DraftError
import eus.busdonostia.app.domain.QUICK_MINUTES
import eus.busdonostia.app.domain.destinationChoices
import eus.busdonostia.app.engine.CHANNEL_ALARM
import eus.busdonostia.app.engine.CHANNEL_ARRIVALS
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.AccentRailCard
import eus.busdonostia.app.ui.components.AnimatedMinutesText
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.ErrorSignalIllustration
import eus.busdonostia.app.ui.components.InfoBanner
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.MotionFilterChip
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SearchPill
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.lineColor
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.system.BlockedChannelNotice
import eus.busdonostia.app.ui.system.blockedChannelNoticeForMode
import eus.busdonostia.app.ui.system.channelSettingsIntent
import eus.busdonostia.app.ui.system.needsNotificationPermission
import eus.busdonostia.app.ui.system.openSettings
import eus.busdonostia.app.ui.system.readSystemStatus
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.CountdownHero
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.LocalStatusColors
import eus.busdonostia.app.ui.theme.MonoFamily
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** A06 · Configurar aviso. [alertId] null = nuevo (con parada, línea y destino precargados si llegan). */
@Composable
fun AlertEditScreen(
    alertId: String?,
    stopId: String?,
    lineId: String?,
    destination: String?,
    onDone: () -> Unit,
) {
    val vm = appViewModel(key = "alert:${alertId ?: "new:$stopId:$lineId:$destination"}") {
        AlertEditViewModel(it, alertId, stopId, lineId, destination)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var system by remember { mutableStateOf(readSystemStatus(context)) }
    LifecycleResumeEffect(Unit) {
        system = readSystemStatus(context)
        onPauseOrDispose { }
    }
    val modeChannelNotice = blockedChannelNoticeForMode(system, state.draft.mode)
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.save() }
    val onSave: () -> Unit = {
        if (vm.validateForSave()) {
            if (needsNotificationPermission(context)) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.save()
            }
        }
    }

    PredictiveBackHandler(enabled = state.pickingStop && state.draft.stopId != null) { progress ->
        try {
            progress.collect { }
            vm.closeStopPicker()
        } catch (_: CancellationException) {
        }
    }

    AlertEditContentView(
        state = state,
        modeChannelNotice = modeChannelNotice,
        onDone = onDone,
        onOpenChannelSettings = {
            val channelId = if (state.draft.mode == AlertMode.ALARM) CHANNEL_ALARM else CHANNEL_ARRIVALS
            context.openSettings(channelSettingsIntent(context, channelId))
        },
        onOpenStopPicker = vm::openStopPicker,
        onCloseStopPicker = vm::closeStopPicker,
        onStopQuery = vm::onStopQuery,
        onSelectStop = vm::selectStop,
        onSelectLine = vm::selectLine,
        onSelectDestination = vm::selectDestination,
        onSetMinutes = vm::setMinutes,
        onSetMode = vm::setMode,
        onSave = onSave,
        onConfirmConflict = { vm.save(confirmed = true) },
        onDismissConflict = vm::dismissConflict,
    )
}

@Composable
internal fun AlertEditContentView(
    state: AlertEditUiState,
    modeChannelNotice: BlockedChannelNotice,
    onDone: () -> Unit,
    onOpenChannelSettings: () -> Unit,
    onOpenStopPicker: () -> Unit,
    onCloseStopPicker: () -> Unit,
    onStopQuery: (String) -> Unit,
    onSelectStop: (CatalogStop) -> Unit,
    onSelectLine: (String) -> Unit,
    onSelectDestination: (String?) -> Unit,
    onSetMinutes: (Int) -> Unit,
    onSetMode: (AlertMode) -> Unit,
    onSave: () -> Unit,
    onConfirmConflict: () -> Unit,
    onDismissConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(if (state.editing) R.string.alert_edit_title else R.string.alert_new_title),
            navigation = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
        )
        when {
            state.loading -> SkeletonList(rows = 4)
            state.catalogError -> EmptyState(
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.catalog_error_body),
                illustration = { ErrorSignalIllustration() },
            )
            state.notFound -> EmptyState(
                title = stringResource(R.string.alert_not_found_title),
                body = stringResource(R.string.alert_not_found_body),
                actions = listOf(EmptyAction(stringResource(R.string.back), primary = true, onClick = onDone)),
                illustration = { ErrorSignalIllustration() },
            )
            else -> {
                Column(
                    Modifier
                        .weight(1f)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = DesignTokens.spacingLg),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                ) {
                    StopAndLine(
                        state = state,
                        onOpenStopPicker = onOpenStopPicker,
                        onCloseStopPicker = onCloseStopPicker,
                        onStopQuery = onStopQuery,
                        onSelectStop = onSelectStop,
                        onSelectLine = onSelectLine,
                        onSelectDestination = onSelectDestination,
                    )
                    MinutesAhead(state.draft.minutesAhead, state.minutesRange, onSetMinutes)
                    AlertModeSelector(state.draft.mode, state.alarmTimeoutSec, onSetMode)
                    InfoBanner(
                        visible = modeChannelNotice != BlockedChannelNotice.NONE,
                        text = stringResource(
                            if (state.draft.mode == AlertMode.ALARM) {
                                R.string.alerts_channel_alarm_blocked
                            } else {
                                R.string.alerts_channel_arrivals_blocked
                            },
                        ),
                        warning = true,
                    ) {
                        TextButton(
                            onClick = onOpenChannelSettings,
                            modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
                        ) {
                            Text(stringResource(R.string.alerts_notifications_enable))
                        }
                    }
                    Text(
                        stringResource(R.string.alert_once_hint, state.expirationDays),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Preview(state)
                    if (state.showErrors && state.errors.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                            state.errors.forEach { error ->
                                Text(
                                    if (error == DraftError.MINUTES_OUT_OF_RANGE) {
                                        stringResource(error.message(), state.minutesRange.first, state.minutesRange.last)
                                    } else {
                                        stringResource(error.message())
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(DesignTokens.spacingSm))
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Button(
                            onClick = onSave,
                            enabled = !state.saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm)
                                .heightIn(min = DesignTokens.touchTargetMin),
                        ) {
                            Text(stringResource(R.string.alert_save), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    state.conflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = onDismissConflict,
            title = { Text(stringResource(R.string.alert_conflict_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.alert_conflict_body,
                        conflict.lineId,
                        conflict.stopName.ifBlank { stringResource(R.string.stop_unnamed) },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmConflict, modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin)) {
                    Text(stringResource(R.string.alert_conflict_update))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConflict, modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun DraftError.message(): Int = when (this) {
    DraftError.NO_STOP -> R.string.alert_error_no_stop
    DraftError.NO_LINE -> R.string.alert_error_no_line
    DraftError.MINUTES_OUT_OF_RANGE -> R.string.alert_error_minutes
}

/** Paso 1: parada (precargada + Cambiar) y chips animadas con sus líneas reales; destino si hay varios. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopAndLine(
    state: AlertEditUiState,
    onOpenStopPicker: () -> Unit,
    onCloseStopPicker: () -> Unit,
    onStopQuery: (String) -> Unit,
    onSelectStop: (CatalogStop) -> Unit,
    onSelectLine: (String) -> Unit,
    onSelectDestination: (String?) -> Unit,
) {
    SectionLabel(stringResource(R.string.alert_step_stop), horizontalPadding = 0.dp)
    val stopId = state.draft.stopId
    if (state.pickingStop || stopId == null) {
        SearchPill(state.stopQuery, onStopQuery, stringResource(R.string.stops_search_hint))
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
            state.stopResults.forEach { stop ->
                BorderedCard(onClick = { onSelectStop(stop) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = DesignTokens.touchTargetMin)
                            .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stop.desc, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (state.stopQuery.isNotBlank() && state.stopResults.isEmpty()) {
            Text(
                stringResource(R.string.stops_no_results_title, state.stopQuery),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stopId != null) {
            TextButton(onClick = onCloseStopPicker, modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin)) {
                Text(stringResource(R.string.cancel))
            }
        }
        return
    }
    BorderedCard {
        Row(
            Modifier.padding(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingXs,
                top = DesignTokens.spacingXs,
                bottom = DesignTokens.spacingXs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.stopName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.stop_unnamed),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenStopPicker, modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin)) {
                Text(stringResource(R.string.alert_change_stop))
            }
        }
    }
    if (state.stopLines.isEmpty()) {
        Text(stringResource(R.string.alert_stop_no_lines), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
    ) {
        state.stopLines.forEach { line ->
            MotionFilterChip(
                selected = state.draft.lineId == line,
                onClick = { onSelectLine(line) },
                label = { LineBadge(line, state.lineColors[line], size = DesignTokens.lineBadgeChipSize) },
            )
        }
    }
    val destinations = state.destinations
    val chosen = state.draft.destination
    if (state.draft.lineId != null && (destinations.size > 1 || chosen != null)) {
        Text(stringResource(R.string.alert_destination_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            MotionFilterChip(
                selected = chosen == null,
                onClick = { onSelectDestination(null) },
                label = { Text(stringResource(R.string.alert_any_destination), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            destinationChoices(destinations, chosen).forEach { dest ->
                MotionFilterChip(
                    selected = chosen.equals(dest, ignoreCase = true),
                    onClick = { onSelectDestination(dest) },
                    label = { Text(dest, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/**
 * Signature Moment 3 · Tactile Detent Time Dial: readout monumental `42.sp` `tnum`,
 * chips rápidos `MotionFilterChip` y slider con feedback háptico al cruzar cada minuto entero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinutesAhead(minutes: Int, range: IntRange, onChange: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var lastDetent by remember { mutableIntStateOf(minutes) }

    SectionLabel(stringResource(R.string.alert_step_minutes), horizontalPadding = 0.dp)
    BorderedCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                AnimatedMinutesText(
                    minutes = minutes,
                    formattedText = minutes.toString(),
                    style = CountdownHero,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.alert_minutes_before_suffix),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = DesignTokens.spacingXs),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            ) {
                QUICK_MINUTES.filter { it in range }.forEach { value ->
                    MotionFilterChip(
                        selected = minutes == value,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastDetent = value
                            onChange(value)
                        },
                        label = { Text(stringResource(R.string.alert_minutes_chip, value), fontFamily = MonoFamily, maxLines = 1) },
                        modifier = Modifier.widthIn(min = DesignTokens.touchTargetMin),
                    )
                }
            }
            val sliderLabel = stringResource(R.string.alert_minutes_slider)
            Slider(
                value = minutes.toFloat(),
                onValueChange = { raw ->
                    val rounded = raw.roundToInt().coerceIn(range.first, range.last)
                    if (rounded != lastDetent) {
                        lastDetent = rounded
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onChange(rounded)
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.touchTargetMin)
                    .semantics { contentDescription = sliderLabel },
            )
        }
    }
}

/** Paso 3: selector de modo (Notificación o Alarma insistente) con firma estable para `AlertModeUiTest`. */
@Composable
internal fun AlertModeSelector(
    mode: AlertMode,
    alarmTimeoutSec: Int,
    onChange: (AlertMode) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val motion = rememberEffectiveMotionTokens()
    SectionLabel(stringResource(R.string.alert_step_mode), horizontalPadding = 0.dp)
    val modes = listOf(
        Triple(AlertMode.NOTIFICATION, R.string.alert_mode_notification, Icons.Outlined.Notifications),
        Triple(AlertMode.ALARM, R.string.alert_mode_alarm, Icons.Outlined.Alarm),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (m, labelRes, icon) ->
            SegmentedButton(
                selected = mode == m,
                onClick = {
                    if (mode != m) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(m)
                },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                icon = {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(DesignTokens.iconSmall),
                    )
                },
                modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
            ) {
                Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            fadeIn(tween(motion.infoBannerMs)) togetherWith fadeOut(tween(motion.infoBannerMs))
        },
        label = "alertModeHint",
    ) { currentMode ->
        Text(
            text = if (currentMode == AlertMode.NOTIFICATION) {
                stringResource(R.string.alert_mode_notification_hint)
            } else {
                stringResource(R.string.alert_mode_alarm_hint, alarmTimeoutSec)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Signature Moment 3 · Réplica viva de la notificación con firma estable para `AlertModeUiTest`.
 * Nunca muestra `stopId`.
 */
@Composable
internal fun Preview(state: AlertEditUiState) {
    val draft = state.draft
    val line = draft.lineId ?: return
    if (draft.stopId == null) return
    val colors = LocalStatusColors.current
    val isAlarm = draft.mode == AlertMode.ALARM
    val accent = if (isAlarm) colors.alarm else lineColor(state.lineColors[line])

    SectionLabel(stringResource(R.string.alert_preview), horizontalPadding = 0.dp)
    AccentRailCard(accentColor = accent) {
        Column(
            modifier = Modifier.padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                Surface(
                    color = if (isAlarm) colors.alarmContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(DesignTokens.cornerRadiusChip),
                ) {
                    Icon(
                        imageVector = if (isAlarm) Icons.Outlined.Alarm else Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = if (isAlarm) colors.onAlarmContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(DesignTokens.spacingXs)
                            .size(DesignTokens.iconSmall),
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val title = draft.destination?.let { stringResource(R.string.notif_title, line, it, draft.minutesAhead) }
                ?: stringResource(R.string.notif_title_no_destination, line, draft.minutesAhead)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.notif_body,
                    state.stopName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.stop_unnamed),
                    stringResource(R.string.source_live),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isAlarm) {
                Surface(
                    color = colors.alarmContainer,
                    shape = RoundedCornerShape(DesignTokens.cornerRadiusChip),
                    modifier = Modifier.padding(top = DesignTokens.spacingXs),
                ) {
                    Text(
                        text = stringResource(R.string.alert_preview_alarm_note, state.alarmTimeoutSec),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onAlarmContainer,
                        modifier = Modifier.padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
                    )
                }
            }
        }
    }
}

@ComposePreview(showBackground = true)
@ComposePreview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@ComposePreview(showBackground = true, fontScale = 1.5f)
@Composable
private fun AlertEditPreview() {
    val state = AlertEditUiState(
        loading = false,
        draft = AlertDraft(
            stopId = "1281",
            stopName = "Boulevard 15",
            lineId = "28",
            destination = "Ospitaleak",
            minutesAhead = 5,
            mode = AlertMode.ALARM,
        ),
        stopName = "Boulevard 15",
        stopLines = listOf("5", "26", "28"),
        destinations = listOf("Ospitaleak", "Amara"),
    )
    BusDonostiaTheme {
        AlertEditContentView(
            state = state,
            modeChannelNotice = BlockedChannelNotice.NONE,
            onDone = {},
            onOpenChannelSettings = {},
            onOpenStopPicker = {},
            onCloseStopPicker = {},
            onStopQuery = {},
            onSelectStop = {},
            onSelectLine = {},
            onSelectDestination = {},
            onSetMinutes = {},
            onSetMode = {},
            onSave = {},
            onConfirmConflict = {},
            onDismissConflict = {},
        )
    }
}
