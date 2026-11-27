package eus.busdonostia.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import eus.busdonostia.app.domain.badgeText
import eus.busdonostia.app.ui.alerts.AlertEditScreen
import eus.busdonostia.app.ui.alerts.AlertsScreen
import eus.busdonostia.app.ui.components.AlertBadge
import eus.busdonostia.app.ui.components.LocalAnimatedVisibilityScope
import eus.busdonostia.app.ui.components.LocalBottomDockInset
import eus.busdonostia.app.ui.components.LocalSharedTransitionScope
import eus.busdonostia.app.ui.components.StatusBarScrim
import eus.busdonostia.app.ui.components.pressScale
import eus.busdonostia.app.ui.components.rememberEffectiveMotionTokens
import eus.busdonostia.app.ui.components.rememberMotionSpring
import eus.busdonostia.app.ui.home.HomeScreen
import eus.busdonostia.app.ui.lines.LineDetailScreen
import eus.busdonostia.app.ui.lines.LinesScreen
import eus.busdonostia.app.ui.settings.SettingsScreen
import eus.busdonostia.app.ui.stopdetail.StopDetailScreen
import eus.busdonostia.app.ui.stops.StopsScreen
import eus.busdonostia.app.ui.theme.DesignTokens

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
    var currentTab by rememberSaveable { mutableStateOf(Destination.HOME.route) }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val tabRoutes = remember { Destination.entries.map { it.route }.toSet() }
    LaunchedEffect(route) {
        if (route in tabRoutes) currentTab = checkNotNull(route)
    }

    val showBottomDock = route == null || route in tabRoutes
    val navigateTo: (Destination) -> Unit = { dest ->
        val reselected = dest.route == currentTab
        currentTab = dest.route
        if (reselected) {
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
    val motion = rememberEffectiveMotionTokens()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomDockInset = if (showBottomDock) DesignTokens.bottomDockClearance + navBarBottom else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalBottomDockInset provides bottomDockInset,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.HOME.route,
                    modifier = Modifier.fillMaxSize(),
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

        StatusBarScrim(modifier = Modifier.align(Alignment.TopCenter))

        AnimatedVisibility(
            visible = showBottomDock,
            enter = slideInVertically(
                animationSpec = tween(motion.dockVisibilityMs, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(tween(motion.dockVisibilityMs)),
            exit = slideOutVertically(
                animationSpec = tween(motion.dockVisibilityMs, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ) + fadeOut(tween(motion.dockVisibilityMs)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingNavDock(
                currentTab = currentTab,
                alertBadge = badge,
                onSelect = navigateTo,
            )
        }
    }
}

/**
 * Signature Moment 2 · Floating Pill Dock: cápsula flotante sobre las 4 pestañas raíz
 * con indicador spring, badge animado de avisos y ocultación limpia en rutas secundarias.
 */
@Composable
private fun FloatingNavDock(
    currentTab: String,
    alertBadge: Int,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = DesignTokens.spacingXs,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingSm)
            .fillMaxWidth()
            .heightIn(min = DesignTokens.floatingDockHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingXs, vertical = DesignTokens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            Destination.entries.forEach { dest ->
                val selected = currentTab == dest.route
                FloatingDockItem(
                    destination = dest,
                    selected = selected,
                    alertBadge = alertBadge,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(dest)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FloatingDockItem(
    destination: Destination,
    selected: Boolean,
    alertBadge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val motion = rememberEffectiveMotionTokens()
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = rememberMotionSpring(),
        label = "dockSelection",
    )
    val pillColor = MaterialTheme.colorScheme.primaryContainer
    val contentTint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .heightIn(min = DesignTokens.touchTargetMin)
            .clip(CircleShape)
            .pressScale(interactionSource)
            .drawBehind {
                if (progress <= 0f) return@drawBehind
                val start = motion.chipIndicatorStartScale
                val w = size.width * (start + (1f - start) * progress)
                val h = size.height * (start + (1f - start) * progress)
                drawRoundRect(
                    color = pillColor.copy(alpha = progress),
                    topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = DesignTokens.spacingXs, vertical = DesignTokens.spacingXxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val badgeLabel = if (destination == Destination.ALERTS) badgeText(alertBadge) else null
        BadgedBox(
            badge = {
                if (destination == Destination.ALERTS) {
                    AlertBadge(count = alertBadge, text = badgeLabel)
                }
            },
        ) {
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.icon,
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(DesignTokens.iconDefault),
            )
        }
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.labelSmall,
            color = contentTint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
