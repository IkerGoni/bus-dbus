package eus.busdonostia.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import eus.busdonostia.app.R

/** Los 4 destinos fijos de la NavigationBar (03-android-screens.md §1). */
enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    STOPS("stops", R.string.nav_stops, Icons.Outlined.LocationOn, Icons.Filled.LocationOn),
    LINES("lines", R.string.nav_lines, Icons.Outlined.DirectionsBus, Icons.Filled.DirectionsBus),
    ALERTS("alerts", R.string.nav_alerts, Icons.Outlined.Notifications, Icons.Filled.Notifications),
}
