package eus.busdonostia.app.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.room.withTransaction
import eus.busdonostia.app.BusDonostiaApp
import eus.busdonostia.app.data.db.toDomain
import eus.busdonostia.app.data.db.toEntity
import eus.busdonostia.app.domain.AlertFrequency
import eus.busdonostia.app.domain.snoozed
import kotlinx.coroutines.launch

const val ACTION_SNOOZE = "eus.busdonostia.app.action.SNOOZE"
const val ACTION_DISMISS = "eus.busdonostia.app.action.DISMISS"

/**
 * Acciones de la notificación sin abrir la app (04-android-platform.md §2):
 * `Posponer 5'` → `snoozedUntilMs = now + 5 min`, `active = true` y sigue vigilando;
 * `Descartar` → ONCE `active = false`; RECURRING solo se cierra (ya avisó en esta franja).
 */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.data?.lastPathSegment ?: return
        if (intent.action != ACTION_SNOOZE && intent.action != ACTION_DISMISS) return
        cancelArrival(context, alertId)
        val container = (context.applicationContext as BusDonostiaApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val db = container.database
                val now = System.currentTimeMillis()
                val snoozed = db.withTransaction {
                    val rule = db.alerts().findById(alertId)?.toDomain() ?: return@withTransaction null
                    val updated = when {
                        intent.action == ACTION_SNOOZE -> rule.snoozed(now)
                        rule.frequency == AlertFrequency.ONCE -> rule.copy(active = false)
                        else -> null
                    }
                    updated?.let { db.alerts().update(it.toEntity()) }
                    updated?.takeIf { intent.action == ACTION_SNOOZE }
                }
                // Acción de notificación: Android deja arrancar el servicio aunque la app no esté visible.
                if (snoozed != null) ensureMonitoring(context, listOf(snoozed), now)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun pendingIntent(context: Context, action: String, alertId: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlertActionReceiver::class.java)
                .setAction(action)
                .setData("dbus-alert://alert/${android.net.Uri.encode(alertId)}".toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
