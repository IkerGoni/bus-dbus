package eus.busdonostia.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.navigation.navDeepLink
import eus.busdonostia.app.ui.lines.LineDetailScreen
import eus.busdonostia.app.ui.lines.LinesScreen
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.badgeText
import eus.busdonostia.app.ui.alerts.AlertEditScreen
import eus.busdonostia.app.ui.alerts.AlertsScreen
import eus.busdonostia.app.ui.components.LocalAnimatedVisibilityScope
import eus.busdonostia.app.ui.components.LocalSharedTransitionScope
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import eus.busdonostia.app.ui.home.HomeScreen
import eus.busdonostia.app.ui.settings.SettingsScreen
import eus.busdonostia.app.ui.stopdetail.StopDetailScreen
import eus.busdonostia.app.ui.stops.StopsScreen

/** Rutas de detalle (A05, A03b, A06, A07). Los parámetros son códigos públicos reales. */
object DetailRoutes {
    const val STOP = "stop/{stopId}?line={line}&from={from}"
    const val LINE = "line/{lineId}"
    const val ALERT_NEW = "alert/new?stopId={stopId}&lineId={lineId}&destination={destination}"
    const val ALERT_EDIT = "alert/{id}/edit"
    const val SETTINGS = "settings"

    /** Tap en la notificación de un aviso (manifest: scheme `dbus`, host `stop`). */
    const val STOP_DEEP_LINK = "dbus://stop/{stopId}?highlightLine={line}"

    fun alertNew(stopId: String? = null, lineId: String? = null, destination: String? = null): String =
        "alert/new?" + listOfNotNull(
            stopId?.let { "stopId=${Uri.encode(it)}" },
            lineId?.let { "lineId=${Uri.encode(it)}" },
            destination?.let { "destination=${Uri.encode(it)}" },
        ).joinToString("&")

    fun alertEdit(id: String): String = "alert/${Uri.encode(id)}/edit"

    fun stop(stopId: String, lineId: String? = null, from: String? = null): String =
        "stop/${Uri.encode(stopId)}" + stopRouteQuery(lineId, from)

    fun line(lineId: String): String = "line/${Uri.encode(lineId)}"
}

/**
 * Query de la ruta de parada. Función pura y sin `Uri` para poder testearla: los códigos
 * públicos de línea y los orígenes de shared element son tokens seguros (sin espacios ni `&`).
 */
internal fun stopRouteQuery(lineId: String?, from: String?): String {
    val params = listOfNotNull(
        lineId?.let { "line=$it" },
        from?.let { "from=$it" },
    )
    return if (params.isEmpty()) "" else "?" + params.joinToString("&")
}

@OptIn(ExperimentalSharedTransitionApi::class)
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
    val openStop: (String, String?, String?) -> Unit = { stopId, lineId, from ->
        navController.navigate(DetailRoutes.stop(stopId, lineId, from))
    }
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
                            val motion = rememberEffectiveMotionTokens()
                            BadgedBox(
                                badge = {
                                    if (dest == Destination.ALERTS) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = badgeText != null,
                                            enter = scaleIn(
                                                animationSpec = spring(
                                                    dampingRatio = motion.springMediumBounceDamping,
                                                    stiffness = motion.springMediumStiffness,
                                                ),
                                            ) + fadeIn(tween(motion.bottomTabFadeInMs)),
                                            exit = scaleOut(tween(motion.bottomTabFadeOutMs)) + fadeOut(tween(motion.bottomTabFadeOutMs)),
                                        ) {
                                            val description = pluralStringResource(R.plurals.a11y_alerts_badge, badge, badge)
                                            Badge(Modifier.semantics { contentDescription = description }) {
                                                Text(badgeText.orEmpty(), maxLines = 1)
                                            }
                                        }
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
        val motion = rememberEffectiveMotionTokens()
        val tabRoutes = Destination.entries.map { it.route }.toSet()
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.HOME.route,
                    modifier = Modifier.padding(innerPadding),
                    enterTransition = {
                        val isTabSwitch = initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes
                        if (isTabSwitch) {
                            fadeIn(tween(motion.bottomTabFadeInMs))
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(motion.detailSlideInMs, easing = FastOutSlowInEasing),
                                initialOffsetX = { (it * motion.detailSlideFraction).toInt() },
                            ) + fadeIn(tween(motion.detailFadeInMs))
                        }
                    },
                    exitTransition = {
                        val isTabSwitch = initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes
                        if (isTabSwitch) {
                            fadeOut(tween(motion.bottomTabFadeOutMs))
                        } else {
                            fadeOut(tween(motion.detailFadeOutMs))
                        }
                    },
                    popEnterTransition = {
                        fadeIn(tween(motion.detailFadeInMs))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(motion.detailSlideOutMs, easing = FastOutSlowInEasing),
                            targetOffsetX = { (it * motion.detailSlideFraction).toInt() },
                        ) + fadeOut(tween(motion.detailFadeOutMs))
                    },
                ) {
                    composable(Destination.HOME.route) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            HomeScreen(onNavigate = navigateTo, onOpenStop = openStop, onOpenLine = openLine, onOpenSettings = openSettings)
                        }
                    }
                    composable(Destination.STOPS.route) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            StopsScreen(onOpenStop = openStop)
                        }
                    }
                    composable(Destination.LINES.route) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            LinesScreen(onOpenLine = openLine)
                        }
                    }
                    composable(Destination.ALERTS.route) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            AlertsScreen(
                                onNewAlert = { navController.navigate(DetailRoutes.alertNew()) },
                                onEditAlert = { navController.navigate(DetailRoutes.alertEdit(it)) },
                                onChooseStop = { navigateTo(Destination.STOPS) },
                                onOpenSettings = openSettings,
                            )
                        }
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
                            navArgument("from") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                        deepLinks = listOf(navDeepLink { uriPattern = DetailRoutes.STOP_DEEP_LINK }),
                    ) { entry ->
                        val args = checkNotNull(entry.arguments)
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            StopDetailScreen(
                                stopId = checkNotNull(args.getString("stopId")),
                                highlightLine = args.getString("line"),
                                sharedOrigin = args.getString("from"),
                                onBack = { navController.popBackStack() },
                                onCreateAlert = { stop, line, destination -> navController.navigate(DetailRoutes.alertNew(stop, line, destination)) },
                            )
                        }
                    }
                    composable(DetailRoutes.LINE, arguments = listOf(navArgument("lineId") { type = NavType.StringType })) { entry ->
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            LineDetailScreen(
                                lineId = checkNotNull(checkNotNull(entry.arguments).getString("lineId")),
                                onBack = { navController.popBackStack() },
                                onOpenStop = openStop,
                            )
                        }
                    }
                }
            }
        }
    }
}
