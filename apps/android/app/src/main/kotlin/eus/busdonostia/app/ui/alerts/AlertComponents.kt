package eus.busdonostia.app.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertDetail
import eus.busdonostia.app.domain.AlertItem
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.ComputedAlertStatus
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.LocalStatusColors

/** Destino del aviso o `Cualquier destino`. */
@Composable
fun alertDestination(rule: AlertRule): String = rule.destination ?: stringResource(R.string.alert_any_destination)

/** Estado del aviso en píldora tonal con icono + texto (WCAG AA ≥ 5.0:1). */
@Composable
fun AlertStatusLabel(item: AlertItem, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val (icon: ImageVector, container: Color, fg: Color, text: String) = when (item.status) {
        ComputedAlertStatus.TRIGGERED -> StatusStyle(
            icon = Icons.Filled.Bolt,
            container = colors.liveContainer,
            fg = colors.onLiveContainer,
            text = stringResource(R.string.alert_status_triggered),
        )
        ComputedAlertStatus.ACTIVE -> StatusStyle(
            icon = Icons.Filled.FiberManualRecord,
            container = colors.liveContainer,
            fg = colors.onLiveContainer,
            text = stringResource(R.string.alert_status_active),
        )
        ComputedAlertStatus.INACTIVE ->
            if (item.detail == AlertDetail.EXPIRED) {
                StatusStyle(
                    icon = Icons.Outlined.EventBusy,
                    container = colors.scheduledContainer,
                    fg = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = stringResource(R.string.alert_status_expired),
                )
            } else {
                StatusStyle(
                    icon = Icons.Filled.Pause,
                    container = colors.scheduledContainer,
                    fg = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = stringResource(R.string.alert_status_paused),
                )
            }
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(DesignTokens.cornerRadiusPill),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            modifier = Modifier.padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(if (icon == Icons.Filled.FiberManualRecord) DesignTokens.liveSignalDotSize else DesignTokens.iconSmall),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

private data class StatusStyle(
    val icon: ImageVector,
    val container: Color,
    val fg: Color,
    val text: String,
)
