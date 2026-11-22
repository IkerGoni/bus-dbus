package eus.busdonostia.app.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.EventBusy
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
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.ComputedAlertStatus
import eus.busdonostia.app.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun clockOf(ms: Long): String = HH_MM.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

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
