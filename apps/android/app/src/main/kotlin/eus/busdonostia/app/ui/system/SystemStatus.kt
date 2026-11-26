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
import eus.busdonostia.app.data.settings.AppLanguage
import eus.busdonostia.app.engine.CHANNEL_ALARM
import eus.busdonostia.app.engine.CHANNEL_ARRIVALS

/*
 * Estado real de permisos y ajustes del sistema que afectan a los avisos (A06, A07, A08).
 * Se relee en cada ON_RESUME: el usuario puede cambiarlos en Ajustes y volver.
 */

private const val PRIVACY_BASE_URL = "https://ikergoni.github.io/bus-dbus/privacy"

/** URL pública de la política de privacidad en GitHub Pages para el idioma activo de la app. */
fun privacyPolicyUrl(language: AppLanguage): String =
    "$PRIVACY_BASE_URL/privacy-policy-${language.tag}.html"

/** Intent para abrir la política de privacidad en el navegador externo. */
fun privacyPolicyIntent(language: AppLanguage): Intent =
    Intent(Intent.ACTION_VIEW, privacyPolicyUrl(language).toUri())

/** Abre un enlace externo en el navegador del sistema sin fallar si no hay visor instalado. */
fun Context.openExternalUrl(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: RuntimeException) {
    }
}

data class SystemStatus(
    /** Notificaciones de la app activadas (permiso en 13+ y switch del sistema). */
    val notificationsEnabled: Boolean,
    /** La app está fuera de la optimización de batería. */
    val batteryUnrestricted: Boolean,
    val arrivalsChannelEnabled: Boolean = true,
    val alarmChannelEnabled: Boolean = true,
)

fun readSystemStatus(context: Context): SystemStatus {
    val power = context.getSystemService(PowerManager::class.java)
    val manager = NotificationManagerCompat.from(context)
    return SystemStatus(
        notificationsEnabled = manager.areNotificationsEnabled(),
        batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
        arrivalsChannelEnabled = manager.getNotificationChannelCompat(CHANNEL_ARRIVALS)?.importance != NotificationManagerCompat.IMPORTANCE_NONE,
        alarmChannelEnabled = manager.getNotificationChannelCompat(CHANNEL_ALARM)?.importance != NotificationManagerCompat.IMPORTANCE_NONE,
    )
}

/** `POST_NOTIFICATIONS` existe desde Android 13; antes no hay nada que pedir. */
fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** Ajustes de notificaciones de la app. */
fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Ajustes de un canal de notificación específico de la app. */
fun channelSettingsIntent(context: Context, channelId: String): Intent =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

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
