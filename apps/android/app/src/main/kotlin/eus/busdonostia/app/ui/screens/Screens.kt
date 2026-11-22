package eus.busdonostia.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eus.busdonostia.app.R
import eus.busdonostia.app.ui.Destination

// Inicio y Avisos muestran su estado `empty` hasta el Sprint 3. Paradas y Líneas: `ui/stops`, `ui/lines`.

@Composable
fun HomeScreen(onNavigate: (Destination) -> Unit) {
    EmptyState(
        title = stringResource(R.string.home_empty_title),
        body = stringResource(R.string.home_empty_body),
        actions = listOf(
            EmptyAction(stringResource(R.string.home_cta_nearby), primary = true) {
                onNavigate(Destination.STOPS)
            },
            EmptyAction(stringResource(R.string.home_cta_lines), primary = false) {
                onNavigate(Destination.LINES)
            },
        ),
    )
}

@Composable
fun AlertsScreen() {
    EmptyState(stringResource(R.string.alerts_empty_title), stringResource(R.string.alerts_empty_body))
}
