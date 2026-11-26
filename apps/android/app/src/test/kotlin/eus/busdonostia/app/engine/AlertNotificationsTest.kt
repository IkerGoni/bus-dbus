package eus.busdonostia.app.engine

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import eus.busdonostia.app.domain.AlertMode
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.AlertsConfig
import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.Firing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AlertNotificationsTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun sampleFiring(mode: AlertMode): Firing = Firing(
        rule = AlertRule(
            id = "rule-1",
            stopId = "340",
            stopName = "Pío Baroja 10",
            lineId = "28",
            destination = "Amara",
            minutesAhead = 5,
            createdAtMs = 1_000L,
            mode = mode,
        ),
        arrival = Arrival(
            lineId = "28",
            internalLineId = "32",
            destination = "Amara",
            minutesAtFetch = 5,
            clockTime = null,
            source = ArrivalSource.LIVE,
            fetchedAtMs = 50_000L,
            colorArgb = null,
        ),
    )

    @Test
    fun `notificacion normal - canal dbus_arrivals_high, sin FLAG_INSISTENT, timeout 0`() {
        val firing = sampleFiring(AlertMode.NOTIFICATION)
        val nowMs = 50_000L
        val cfg = AlertsConfig(alarmTimeoutSec = 45)

        notifyArrival(context, firing, nowMs, cfg)

        val shadow = shadowOf(notificationManager)
        val notification = shadow.getNotification(firing.rule.id, 3) // ARRIVAL_NOTIFICATION_ID = 3
        assertNotNull("Notificación no publicada", notification)
        assertEquals(CHANNEL_ARRIVALS, notification.channelId)
        assertEquals(0, notification.flags and Notification.FLAG_INSISTENT)
        assertEquals(0L, notification.timeoutAfter)
        @Suppress("DEPRECATION")
        assertEquals(NotificationCompat.PRIORITY_HIGH, notification.priority)
        assertEquals(NotificationCompat.CATEGORY_REMINDER, notification.category)
    }

    @Test
    fun `modo alarma - canal dbus_arrivals_alarm, FLAG_INSISTENT activo, timeout alarmTimeoutSec x 1000`() {
        val firing = sampleFiring(AlertMode.ALARM)
        val nowMs = 50_000L
        val cfg = AlertsConfig(alarmTimeoutSec = 75)

        notifyArrival(context, firing, nowMs, cfg)

        val shadow = shadowOf(notificationManager)
        val notification = shadow.getNotification(firing.rule.id, 3)
        assertNotNull("Alarma no publicada", notification)
        assertEquals(CHANNEL_ALARM, notification.channelId)
        assertTrue("FLAG_INSISTENT no activo", (notification.flags and Notification.FLAG_INSISTENT) != 0)
        assertEquals(75_000L, notification.timeoutAfter)
        @Suppress("DEPRECATION")
        assertEquals(NotificationCompat.PRIORITY_MAX, notification.priority)
        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
    }

    @Test
    fun `canNotifyAlert para cada modo cuando su canal esta bloqueado`() {
        createNotificationChannels(context)

        assertTrue(canNotifyAlert(context, AlertMode.NOTIFICATION))
        assertTrue(canNotifyAlert(context, AlertMode.ALARM))

        // Bloquear solo canal de alarma
        notificationManager.getNotificationChannel(CHANNEL_ALARM).importance = NotificationManager.IMPORTANCE_NONE
        assertTrue("Modo notificación debe seguir activo", canNotifyAlert(context, AlertMode.NOTIFICATION))
        assertFalse("Modo alarma debe estar deshabilitado", canNotifyAlert(context, AlertMode.ALARM))

        // Restaurar alarma y bloquear notificaciones normales
        notificationManager.getNotificationChannel(CHANNEL_ALARM).importance = NotificationManager.IMPORTANCE_HIGH
        notificationManager.getNotificationChannel(CHANNEL_ARRIVALS).importance = NotificationManager.IMPORTANCE_NONE
        assertFalse("Modo notificación debe estar deshabilitado", canNotifyAlert(context, AlertMode.NOTIFICATION))
        assertTrue("Modo alarma debe seguir activo", canNotifyAlert(context, AlertMode.ALARM))

        // Desactivar notificaciones a nivel de app
        shadowOf(notificationManager).setNotificationsEnabled(false)
        assertFalse(canNotifyAlert(context, AlertMode.NOTIFICATION))
        assertFalse(canNotifyAlert(context, AlertMode.ALARM))
    }

    @Test
    fun `createNotificationChannels crea 3 canales`() {
        createNotificationChannels(context)

        val channels = notificationManager.notificationChannels
        val channelIds = channels.map { it.id }.toSet()
        assertEquals(3, channels.size)
        assertTrue(channelIds.contains(CHANNEL_MONITORING))
        assertTrue(channelIds.contains(CHANNEL_ARRIVALS))
        assertTrue(channelIds.contains(CHANNEL_ALARM))

        val alarmChannel = notificationManager.getNotificationChannel(CHANNEL_ALARM)
        assertNotNull(alarmChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, alarmChannel.importance)
        assertTrue(alarmChannel.shouldVibrate())
    }
}
