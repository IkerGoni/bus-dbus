package eus.busdonostia.app.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.BusDonostiaApp
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.isWatching
import eus.busdonostia.app.domain.nextAlarmAtMs
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * Arranque del motor (04-android-platform.md §1): ONCE por gesto del usuario (guardar,
 * reactivar, Posponer) y RECURRING con una alarma en el próximo `windowFrom`.
 */

private const val ACTION_WINDOW_START = "eus.busdonostia.app.action.WINDOW_START"

private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    0,
    Intent(context, AlertAlarmReceiver::class.java).setAction(ACTION_WINDOW_START),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Alarmas exactas permitidas (antes de Android 12 no hace falta permiso). */
fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

/**
 * Una sola alarma en el próximo inicio de franja de los RECURRING activos (o ninguna).
 * Exacta si hay permiso; si no, inexacta (A04/A06/A07 avisan de que puede llegar tarde).
 */
fun scheduleNextAlarm(context: Context, rules: List<AlertRule>, nowMs: Long) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    val intent = alarmIntent(context)
    val at = nextAlarmAtMs(rules, nowMs)
    when {
        at == null -> alarms.cancel(intent)
        canScheduleExactAlarms(context) -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }
}

/**
 * Arranca el servicio si algún aviso está en vigilancia y no está ya vivo. Se llama en
 * cada cambio de avisos y al abrir la app; si Android no deja arrancar desde segundo plano, no pasa nada.
 */
fun ensureMonitoring(context: Context, rules: List<AlertRule>, nowMs: Long): Boolean {
    if (AlertMonitorService.running || rules.none { isWatching(it, nowMs) }) return true
    return AlertMonitorService.start(context)
}

private val Context.container: AppContainer get() = (applicationContext as BusDonostiaApp).container

/** Corre [block] con los avisos guardados sin bloquear el hilo principal del receptor. */
private fun BroadcastReceiver.withRules(context: Context, block: (List<AlertRule>) -> Unit) {
    val pending = goAsync()
    val container = context.container
    container.appScope.launch {
        try {
            block(container.alerts.filterNotNull().first())
        } finally {
            pending.finish()
        }
    }
}

/** Inicio de franja de un RECURRING: arranca la vigilancia (alarma exacta = arranque permitido) y programa la siguiente. */
class AlertAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WINDOW_START) return
        withRules(context) { rules ->
            val now = System.currentTimeMillis()
            // Con alarma inexacta (sin permiso) Android 12+ no deja arrancar: se avisa para abrir la app.
            if (!ensureMonitoring(context, rules, now)) notifyStopped(context, R.string.notif_start_blocked)
            scheduleNextAlarm(context, rules, now)
        }
    }
}

/** Las alarmas se pierden al reiniciar o actualizar la app y dependen de la hora: se reprograman. */
class AlertRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        withRules(context) { rules -> scheduleNextAlarm(context, rules, System.currentTimeMillis()) }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
