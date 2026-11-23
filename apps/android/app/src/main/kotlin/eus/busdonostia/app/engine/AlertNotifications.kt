package eus.busdonostia.app.engine

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import eus.busdonostia.app.MainActivity
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.AlertsConfig
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.Firing
import eus.busdonostia.app.domain.firingDestination
import eus.busdonostia.app.domain.remainingMinutes

/*
 * Canales y notificaciones del motor (04-android-platform.md §1–§2). Si el usuario
 * deniega `POST_NOTIFICATIONS`, `notify` no hace nada y A04 lo avisa; el motor
 * entonces no borra el aviso disparado ([canNotifyArrivals]).
 */

const val CHANNEL_MONITORING = "dbus_monitoring"
const val CHANNEL_ARRIVALS = "dbus_arrivals_high"

const val MONITORING_NOTIFICATION_ID = 1
private const val STOPPED_NOTIFICATION_ID = 2
private const val ARRIVAL_NOTIFICATION_ID = 3

/** Deep link de A05 con la línea resaltada (también lo declara el manifest). */
fun stopDeepLink(stopId: String, lineId: String): String =
    "dbus://stop/${android.net.Uri.encode(stopId)}?highlightLine=${android.net.Uri.encode(lineId)}"

fun createNotificationChannels(context: Context) {
    val manager = NotificationManagerCompat.from(context)
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_MONITORING, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.channel_monitoring_name))
            .setDescription(context.getString(R.string.channel_monitoring_description))
            .setShowBadge(false)
            .build(),
    )
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_ARRIVALS, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.channel_arrivals_name))
            .setDescription(context.getString(R.string.channel_arrivals_description))
            .build(),
    )
}

/** El aviso guardado sin catálogo no tiene nombre: mostrar «parada sin nombre», nunca el código de poste. */
private fun AlertRule.stopNameForDisplay(context: Context): String =
    stopName.takeIf { it.isNotBlank() } ?: context.getString(R.string.stop_unnamed)

private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** El usuario verá el aviso: notificaciones de la app permitidas y canal de llegadas sin bloquear. */
fun canNotifyArrivals(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    return manager.getNotificationChannelCompat(CHANNEL_ARRIVALS)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
}

/** «Vigilando L5 en Pío Baroja 10» (o «Vigilando N avisos»), silenciosa y fija mientras vive el servicio. */
fun monitoringNotification(context: Context, watching: List<AlertRule>, pollIntervalSec: Int): Notification {
    val title = when (watching.size) {
        0 -> context.getString(R.string.channel_monitoring_name) // aún sin leer los avisos (primer instante tras arrancar)
        1 -> watching.single().let { context.getString(R.string.notif_monitoring_one, it.lineId, it.stopNameForDisplay(context)) }
        else -> context.resources.getQuantityString(R.plurals.notif_monitoring_many, watching.size, watching.size)
    }
    return NotificationCompat.Builder(context, CHANNEL_MONITORING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(context.getString(R.string.notif_monitoring_body, pollIntervalSec))
        .setContentIntent(openAppIntent(context))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()
}

@SuppressLint("MissingPermission") // Sin permiso, NotificationManagerCompat no publica nada (A04 lo avisa).
fun updateMonitoringNotification(context: Context, watching: List<AlertRule>, pollIntervalSec: Int) {
    NotificationManagerCompat.from(context).notify(MONITORING_NOTIFICATION_ID, monitoringNotification(context, watching, pollIntervalSec))
}

/**
 * `L5 → Amara en 4 min` · `Pío Baroja 10 (340) · En vivo`. Sin botones: el aviso ya se ha
 * borrado (un solo uso); tocarla abre A05 y deslizarla la cierra. Una por aviso (tag = id).
 */
@SuppressLint("MissingPermission")
fun notifyArrival(
    context: Context,
    firing: Firing,
    nowMs: Long,
    alertsConfig: AlertsConfig = AlertsConfig(),
) {
    val rule = firing.rule
    val notifyEarlyMs = alertsConfig.triggerBufferSec * 1_000L
    val minutes = remainingMinutes(firing.arrival, nowMs + notifyEarlyMs)
    val title = firingDestination(rule, firing.arrival)
        ?.let { context.getString(R.string.notif_title, rule.lineId, it, minutes) }
        ?: context.getString(R.string.notif_title_no_destination, rule.lineId, minutes)
    val source = context.getString(
        if (firing.arrival.source == ArrivalSource.LIVE) R.string.source_live else R.string.source_scheduled,
    )
    val body = context.getString(R.string.notif_body, rule.stopNameForDisplay(context), source)
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(Intent.ACTION_VIEW, stopDeepLink(rule.stopId, rule.lineId).toUri())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ARRIVALS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setContentIntent(open)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setWhen(nowMs)
        .build()
    NotificationManagerCompat.from(context).notify(rule.id, ARRIVAL_NOTIFICATION_ID, notification)
}

/** El motor se ha parado sin que el usuario lo pida (sin servicio o límite de Android). */
@SuppressLint("MissingPermission")
fun notifyStopped(context: Context, textRes: Int) {
    val text = context.getString(textRes)
    val notification = NotificationCompat.Builder(context, CHANNEL_MONITORING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.notif_stopped_title))
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openAppIntent(context))
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(STOPPED_NOTIFICATION_ID, notification)
}

fun cancelStopped(context: Context) {
    NotificationManagerCompat.from(context).cancel(STOPPED_NOTIFICATION_ID)
}
