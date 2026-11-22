package eus.busdonostia.app.ui.alerts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.DraftError
import eus.busdonostia.app.domain.QUICK_MINUTES
import eus.busdonostia.app.domain.destinationChoices
import eus.busdonostia.app.ui.appViewModel
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.LineBadge
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SearchPill
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.components.StopCodePill
import eus.busdonostia.app.ui.screens.EmptyAction
import eus.busdonostia.app.ui.screens.EmptyState
import eus.busdonostia.app.ui.system.needsNotificationPermission
import eus.busdonostia.app.ui.theme.MonoFamily

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
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    // Se pide al guardar (gesto del usuario); el aviso se guarda lo acepte o no.
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

    Column(Modifier.fillMaxSize()) {
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
            state.catalogError -> EmptyState(stringResource(R.string.catalog_error_title), stringResource(R.string.catalog_error_body))
            state.notFound -> EmptyState(
                stringResource(R.string.alert_not_found_title),
                stringResource(R.string.alert_not_found_body),
                listOf(EmptyAction(stringResource(R.string.back), primary = true, onClick = onDone)),
            )
            else -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StopAndLine(state, vm)
                    MinutesAhead(state.draft.minutesAhead, state.minutesRange, vm::setMinutes)
                    // Un solo uso: avisa una vez y se borra.
                    Text(
                        stringResource(R.string.alert_once_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Preview(state)
                    if (state.showErrors && state.errors.isNotEmpty()) {
                        state.errors.forEach { error ->
                            Text(
                                stringResource(error.message()),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.padding(4.dp))
                }
                HorizontalDivider()
                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.alert_save))
                }
            }
        }
    }

    state.conflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = vm::dismissConflict,
            title = { Text(stringResource(R.string.alert_conflict_title)) },
            text = { Text(stringResource(R.string.alert_conflict_body, conflict.lineId, conflict.stopName)) },
            confirmButton = {
                TextButton(onClick = { vm.save(confirmed = true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.alert_conflict_update))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissConflict, modifier = Modifier.heightIn(min = 48.dp)) {
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

/** Paso 1: parada (precargada + Cambiar) y chips solo con sus líneas reales; destino si hay varios. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopAndLine(state: AlertEditUiState, vm: AlertEditViewModel) {
    SectionLabel(stringResource(R.string.alert_step_stop), modifier = Modifier.padding(horizontal = 0.dp))
    val stopId = state.draft.stopId
    if (state.pickingStop || stopId == null) {
        SearchPill(state.stopQuery, vm::onStopQuery, stringResource(R.string.stops_search_hint))
        state.stopResults.forEach { stop ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { vm.selectStop(stop) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StopCodePill(stop.cod)
                Text(stop.desc, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        if (state.stopQuery.isNotBlank() && state.stopResults.isEmpty()) {
            Text(
                stringResource(R.string.stops_no_results_title, state.stopQuery),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stopId != null) {
            TextButton(onClick = vm::closeStopPicker, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel))
            }
        }
        return
    }
    BorderedCard {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.stopName ?: stringResource(R.string.stop_title_fallback, stopId),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StopCodePill(stopId)
            }
            TextButton(onClick = vm::openStopPicker, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.alert_change_stop))
            }
        }
    }
    if (state.stopLines.isEmpty()) {
        Text(stringResource(R.string.alert_stop_no_lines), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.stopLines.forEach { line ->
            FilterChip(
                selected = state.draft.lineId == line,
                onClick = { vm.selectLine(line) },
                label = { LineBadge(line, state.lineColors[line], size = 24.dp) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
    val destinations = state.destinations
    val chosen = state.draft.destination
    // Selector de destino si la línea tiene varios aquí (o si llega uno precargado desde una llegada).
    if (state.draft.lineId != null && (destinations.size > 1 || chosen != null)) {
        Text(stringResource(R.string.alert_destination_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = chosen == null,
                onClick = { vm.selectDestination(null) },
                label = { Text(stringResource(R.string.alert_any_destination), maxLines = 1) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            destinationChoices(destinations, chosen).forEach { dest ->
                FilterChip(
                    selected = chosen.equals(dest, ignoreCase = true),
                    onClick = { vm.selectDestination(dest) },
                    label = { Text(dest, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** Paso 2: display grande + chips rápidos sincronizados con slider en [range] (`alerts.min/maxMinutesAhead`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinutesAhead(minutes: Int, range: IntRange, onChange: (Int) -> Unit) {
    SectionLabel(stringResource(R.string.alert_step_minutes), modifier = Modifier.padding(horizontal = 0.dp))
    // Mono solo para la cifra (DESIGN.md §2); el resto del texto en la fuente de interfaz.
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            minutes.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            fontFamily = MonoFamily,
            maxLines = 1,
        )
        Text(stringResource(R.string.alert_minutes_before_suffix), style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QUICK_MINUTES.filter { it in range }.forEach { value ->
            FilterChip(
                selected = minutes == value,
                onClick = { onChange(value) },
                label = { Text(stringResource(R.string.alert_minutes_chip, value), fontFamily = MonoFamily, maxLines = 1) },
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
            )
        }
    }
    val sliderLabel = stringResource(R.string.alert_minutes_slider)
    Slider(
        value = minutes.toFloat(),
        onValueChange = { onChange(Math.round(it)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = sliderLabel },
    )
}

/** Vista previa etiquetada de la notificación con los valores elegidos. No tiene botones reales: es un dibujo. */
@Composable
private fun Preview(state: AlertEditUiState) {
    val draft = state.draft
    val line = draft.lineId ?: return
    val stopId = draft.stopId ?: return
    SectionLabel(stringResource(R.string.alert_preview), modifier = Modifier.padding(horizontal = 0.dp))
    BorderedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val title = draft.destination?.let { stringResource(R.string.notif_title, line, it, draft.minutesAhead) }
                ?: stringResource(R.string.notif_title_no_destination, line, draft.minutesAhead)
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.notif_body_stop, state.stopName ?: stopId, stopId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
