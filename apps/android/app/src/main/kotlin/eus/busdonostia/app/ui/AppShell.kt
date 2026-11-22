package eus.busdonostia.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.badgeText
import eus.busdonostia.app.ui.alerts.AlertEditScreen
import eus.busdonostia.app.ui.alerts.AlertsScreen
import eus.busdonostia.app.ui.home.HomeScreen
import eus.busdonostia.app.ui.settings.SettingsScreen
import eus.busdonostia.app.ui.stopdetail.StopDetailScreen
import eus.busdonostia.app.ui.stops.StopsScreen

/** Rutas de detalle (A05, A03b, A06, A07). Los parámetros son códigos públicos reales. */
object DetailRoutes {
    const val STOP = "stop/{stopId}?line={line}"
    const val LINE = "line/{lineId}"
    const val ALERT_NEW = "alert/new?stopId={stopId}&lineId={lineId}&destination={destination}"
    const val ALERT_EDIT = "alert/{id}/edit"
    const val SETTINGS = "settings"

    fun alertNew(stopId: String? = null, lineId: String? = null, destination: String? = null): String =
        "alert/new?" + listOfNotNull(
            stopId?.let { "stopId=${Uri.encode(it)}" },
            lineId?.let { "lineId=${Uri.encode(it)}" },
            destination?.let { "destination=${Uri.encode(it)}" },
        ).joinToString("&")

    fun alertEdit(id: String): String = "alert/${Uri.encode(id)}/edit"

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
    val openLine: (String) -> Unit = { navController.navigate(DetailRoutes.line(it)) }
    val openSettings: () -> Unit = { navController.navigate(DetailRoutes.SETTINGS) { launchSingleTop = true } }
    val shell = appViewModel { ShellViewModel(it) }
    val badge by shell.alertBadge.collectAsStateWithLifecycle()

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
                            val badgeText = if (dest == Destination.ALERTS) badgeText(badge) else null
                            BadgedBox(
                                badge = {
                                    if (badgeText != null) {
                                        val description = pluralStringResource(R.plurals.a11y_alerts_badge, badge, badge)
                                        Badge(Modifier.semantics { contentDescription = description }) { Text(badgeText, maxLines = 1) }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.icon,
                                    contentDescription = null,
                                )
                            }
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
            composable(Destination.HOME.route) {
                HomeScreen(onNavigate = navigateTo, onOpenStop = openStop, onOpenLine = openLine, onOpenSettings = openSettings)
            }
            composable(Destination.STOPS.route) { StopsScreen(onOpenStop = openStop) }
            composable(Destination.LINES.route) { LinesScreen(onOpenLine = openLine) }
            composable(Destination.ALERTS.route) {
                AlertsScreen(
                    onNewAlert = { navController.navigate(DetailRoutes.alertNew()) },
                    onEditAlert = { navController.navigate(DetailRoutes.alertEdit(it)) },
                    onChooseStop = { navigateTo(Destination.STOPS) },
                    onOpenSettings = openSettings,
                )
            }
            composable(
                DetailRoutes.ALERT_NEW,
                arguments = listOf("stopId", "lineId", "destination").map { name ->
                    navArgument(name) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                },
            ) { entry ->
                val args = checkNotNull(entry.arguments)
                AlertEditScreen(
                    alertId = null,
                    stopId = args.getString("stopId"),
                    lineId = args.getString("lineId"),
                    destination = args.getString("destination"),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(DetailRoutes.ALERT_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                AlertEditScreen(
                    alertId = checkNotNull(checkNotNull(entry.arguments).getString("id")),
                    stopId = null,
                    lineId = null,
                    destination = null,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(DetailRoutes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
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
                    onCreateAlert = { stop, line, destination -> navController.navigate(DetailRoutes.alertNew(stop, line, destination)) },
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
