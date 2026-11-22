package eus.busdonostia.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import eus.busdonostia.app.ui.lines.LineDetailScreen
import eus.busdonostia.app.ui.lines.LinesScreen
import eus.busdonostia.app.ui.screens.AlertsScreen
import eus.busdonostia.app.ui.screens.HomeScreen
import eus.busdonostia.app.ui.stopdetail.StopDetailScreen
import eus.busdonostia.app.ui.stops.StopsScreen

/** Rutas de detalle (A05 y A03b). Los parámetros son códigos públicos reales. */
object DetailRoutes {
    const val STOP = "stop/{stopId}?line={line}"
    const val LINE = "line/{lineId}"

    fun stop(stopId: String, lineId: String? = null): String =
        "stop/${Uri.encode(stopId)}" + (lineId?.let { "?line=${Uri.encode(it)}" } ?: "")

    fun line(lineId: String): String = "line/${Uri.encode(lineId)}"
}

@Composable
fun AppShell(navController: NavHostController = rememberNavController()) {
    // Pestaña activa: la última raíz de pestaña visitada; los detalles (A05, A03b) conservan la suya.
    var currentTab by rememberSaveable { mutableStateOf(Destination.HOME.route) }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(route) {
        if (Destination.entries.any { it.route == route }) currentTab = checkNotNull(route)
    }
    val navigateTo: (Destination) -> Unit = { dest ->
        val reselected = dest.route == currentTab
        currentTab = dest.route
        if (reselected) {
            // Re-seleccionar la pestaña vuelve a su raíz.
            navController.popBackStack(dest.route, inclusive = false)
        } else {
            navController.navigate(dest.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val openStop: (String, String?) -> Unit = { stopId, lineId -> navController.navigate(DetailRoutes.stop(stopId, lineId)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { dest ->
                    val selected = currentTab == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(dest) },
                        icon = {
                            Icon(
                                imageVector = if (selected) dest.selectedIcon else dest.icon,
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(dest.label),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.HOME.route) { HomeScreen(onNavigate = navigateTo) }
            composable(Destination.STOPS.route) { StopsScreen(onOpenStop = openStop) }
            composable(Destination.LINES.route) {
                LinesScreen(onOpenLine = { navController.navigate(DetailRoutes.line(it)) })
            }
            composable(Destination.ALERTS.route) { AlertsScreen() }
            composable(
                DetailRoutes.STOP,
                arguments = listOf(
                    navArgument("stopId") { type = NavType.StringType },
                    navArgument("line") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val args = checkNotNull(entry.arguments)
                StopDetailScreen(
                    stopId = checkNotNull(args.getString("stopId")),
                    highlightLine = args.getString("line"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(DetailRoutes.LINE, arguments = listOf(navArgument("lineId") { type = NavType.StringType })) { entry ->
                LineDetailScreen(
                    lineId = checkNotNull(checkNotNull(entry.arguments).getString("lineId")),
                    onBack = { navController.popBackStack() },
                    onOpenStop = openStop,
                )
            }
        }
    }
}
