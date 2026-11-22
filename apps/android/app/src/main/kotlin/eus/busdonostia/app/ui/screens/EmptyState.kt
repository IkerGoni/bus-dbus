package eus.busdonostia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Acción real de un estado vacío: siempre navega, nunca un Toast (guardrails §3). */
data class EmptyAction(val label: String, val primary: Boolean, val onClick: () -> Unit)

@Composable
fun EmptyState(
    title: String,
    body: String,
    actions: List<EmptyAction> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        actions.forEach { action ->
            val modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
            if (action.primary) {
                Button(onClick = action.onClick, modifier = modifier) { Text(action.label) }
            } else {
                OutlinedButton(onClick = action.onClick, modifier = modifier) { Text(action.label) }
            }
        }
    }
}
