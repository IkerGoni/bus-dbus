package eus.busdonostia.app.ui.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/*
 * Estado real de permisos y ajustes del sistema que afectan a los avisos (A06, A07, A08).
 * Se relee en cada ON_RESUME: el usuario puede cambiarlos en Ajustes y volver.
 */

data class SystemStatus(
    /** Notificaciones de la app activadas (permiso en 13+ y switch del sistema). */
    val notificationsEnabled: Boolean,
    /** La app está fuera de la optimización de batería. */
    val batteryUnrestricted: Boolean,
)

fun readSystemStatus(context: Context): SystemStatus {
    val power = context.getSystemService(PowerManager::class.java)
    return SystemStatus(
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
    )
}

/** `POST_NOTIFICATIONS` existe desde Android 13; antes no hay nada que pedir. */
fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** Ajustes de notificaciones de la app. */
fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Lista de optimización de batería (sin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: el usuario decide). */
fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

/**
 * Abre [intent]; si el fabricante no tiene esa pantalla, abre la ficha de la app en Ajustes.
 * (No se usa `resolveActivity`: con la visibilidad de paquetes de Android 11 da falsos negativos.)
 */
fun Context.openSettings(intent: Intent?) {
    try {
        startActivity(checkNotNull(intent))
    } catch (_: RuntimeException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }
}
