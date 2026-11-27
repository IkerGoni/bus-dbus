package eus.busdonostia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import eus.busdonostia.app.ui.theme.DesignTokens

/** Acción real de un estado vacío: siempre navega, nunca un Toast. */
data class EmptyAction(val label: String, val primary: Boolean, val onClick: () -> Unit)

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: List<EmptyAction> = emptyList(),
    illustration: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DesignTokens.spacingXl, vertical = DesignTokens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (illustration != null) {
            illustration()
            Spacer(Modifier.height(DesignTokens.spacingXs))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = DesignTokens.bottomDockClearance * 4),
        )
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(DesignTokens.spacingXs))
        }
        actions.forEach { action ->
            val btnModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DesignTokens.touchTargetMin)
            if (action.primary) {
                Button(onClick = action.onClick, modifier = btnModifier) {
                    Text(action.label)
                }
            } else {
                OutlinedButton(onClick = action.onClick, modifier = btnModifier) {
                    Text(action.label)
                }
            }
        }
    }
}
