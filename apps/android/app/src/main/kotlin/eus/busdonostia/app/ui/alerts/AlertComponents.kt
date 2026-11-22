package eus.busdonostia.app.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertDetail
import eus.busdonostia.app.domain.AlertFrequency
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.ComputedAlertStatus
import eus.busdonostia.app.domain.DaysPreset
import eus.busdonostia.app.domain.presetOf
import eus.busdonostia.app.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun clockOf(ms: Long): String = HH_MM.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private val DAY_SHORT = listOf(
    R.string.day_short_1,
    R.string.day_short_2,
    R.string.day_short_3,
    R.string.day_short_4,
    R.string.day_short_5,
    R.string.day_short_6,
    R.string.day_short_7,
)

/** Inicial del día ISO (1=Lun .. 7=Dom) en el idioma de la app. */
@Composable
fun dayShort(day: Int): String = stringResource(DAY_SHORT[day - 1])

/** `L–V`, `Diario`, `S–D` o los días sueltos (`L X V`). */
@Composable
fun daysText(days: Set<Int>): String = when (presetOf(days)) {
    DaysPreset.WEEKDAYS -> stringResource(R.string.days_summary_weekdays)
    DaysPreset.DAILY -> stringResource(R.string.days_summary_daily)
    DaysPreset.WEEKEND -> stringResource(R.string.days_summary_weekend)
    null -> days.sorted().map { dayShort(it) }.joinToString(" ")
}

/** `Solo esta vez` o `L–V 07:45–08:30`. */
@Composable
fun frequencyText(rule: AlertRule): String =
    if (rule.frequency == AlertFrequency.ONCE) {
        stringResource(R.string.alert_once)
    } else {
        stringResource(R.string.alert_recurring_summary, daysText(rule.daysOfWeek), rule.windowFrom.orEmpty(), rule.windowTo.orEmpty())
    }

/** Destino del aviso o `Cualquier destino`. */
@Composable
fun alertDestination(rule: AlertRule): String = rule.destination ?: stringResource(R.string.alert_any_destination)

/** Estado del aviso con icono + texto (nunca solo color). */
@Composable
fun AlertStatusLabel(item: AlertItem, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val (icon: ImageVector, tint: Color, text: String) = when (item.status) {
        ComputedAlertStatus.TRIGGERED -> Triple(Icons.Filled.Bolt, colors.live, stringResource(R.string.alert_status_triggered))
        ComputedAlertStatus.ACTIVE -> Triple(Icons.Filled.FiberManualRecord, colors.live, stringResource(R.string.alert_status_active))
        ComputedAlertStatus.SNOOZED -> Triple(
            Icons.Outlined.Snooze,
            colors.warning,
            stringResource(R.string.alert_status_snoozed, clockOf(item.rule.snoozedUntilMs ?: 0L)),
        )
        ComputedAlertStatus.SILENCED ->
            if (item.detail == AlertDetail.NOTIFIED_IN_WINDOW) {
                Triple(Icons.Filled.CheckCircle, colors.scheduled, stringResource(R.string.alert_status_notified))
            } else {
                Triple(Icons.Outlined.Bedtime, colors.favorite, stringResource(R.string.alert_status_out_of_window))
            }
        ComputedAlertStatus.INACTIVE ->
            if (item.detail == AlertDetail.EXPIRED) {
                Triple(Icons.Outlined.EventBusy, colors.scheduled, stringResource(R.string.alert_status_expired))
            } else {
                Triple(Icons.Filled.Pause, colors.scheduled, stringResource(R.string.alert_status_paused))
            }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (icon == Icons.Filled.FiberManualRecord) 10.dp else 16.dp))
        // Ámbar y gris no llegan a 4.5:1 como texto: solo el icono lleva el color de estado.
        val textColor = if (tint == colors.live) tint else MaterialTheme.colorScheme.onSurfaceVariant
        Text(text, style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
    }
}
