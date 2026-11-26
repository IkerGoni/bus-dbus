package eus.busdonostia.app.ui.system

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import eus.busdonostia.app.engine.CHANNEL_ALARM
import eus.busdonostia.app.engine.CHANNEL_ARRIVALS
import eus.busdonostia.app.engine.createNotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SystemStatusTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun `canales no creados aun se consideran habilitados`() {
        val status = readSystemStatus(context)
        assertTrue(status.arrivalsChannelEnabled)
        assertTrue(status.alarmChannelEnabled)
    }

    @Test
    fun `ambos canales activos`() {
        createNotificationChannels(context)
        val status = readSystemStatus(context)
        assertTrue(status.notificationsEnabled)
        assertTrue(status.arrivalsChannelEnabled)
        assertTrue(status.alarmChannelEnabled)
    }

    @Test
    fun `canal de alarma en IMPORTANCE_NONE`() {
        createNotificationChannels(context)
        notificationManager.getNotificationChannel(CHANNEL_ALARM).importance = NotificationManager.IMPORTANCE_NONE

        val status = readSystemStatus(context)
        assertTrue(status.notificationsEnabled)
        assertTrue(status.arrivalsChannelEnabled)
        assertFalse(status.alarmChannelEnabled)
    }

    @Test
    fun `canal de llegadas en IMPORTANCE_NONE`() {
        createNotificationChannels(context)
        notificationManager.getNotificationChannel(CHANNEL_ARRIVALS).importance = NotificationManager.IMPORTANCE_NONE

        val status = readSystemStatus(context)
        assertTrue(status.notificationsEnabled)
        assertFalse(status.arrivalsChannelEnabled)
        assertTrue(status.alarmChannelEnabled)
    }

    @Test
    fun `notificaciones globales desactivadas`() {
        createNotificationChannels(context)
        shadowOf(notificationManager).setNotificationsEnabled(false)

        val status = readSystemStatus(context)
        assertFalse(status.notificationsEnabled)
    }

    @Test
    fun `ambos canales en IMPORTANCE_NONE`() {
        createNotificationChannels(context)
        notificationManager.getNotificationChannel(CHANNEL_ARRIVALS).importance = NotificationManager.IMPORTANCE_NONE
        notificationManager.getNotificationChannel(CHANNEL_ALARM).importance = NotificationManager.IMPORTANCE_NONE

        val status = readSystemStatus(context)
        assertTrue(status.notificationsEnabled)
        assertFalse(status.arrivalsChannelEnabled)
        assertFalse(status.alarmChannelEnabled)
    }

    @Test
    fun `channelSettingsIntent apunta a los ajustes del canal indicado`() {
        val arrivalsIntent = channelSettingsIntent(context, CHANNEL_ARRIVALS)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, arrivalsIntent.action)
        assertEquals(context.packageName, arrivalsIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(CHANNEL_ARRIVALS, arrivalsIntent.getStringExtra(Settings.EXTRA_CHANNEL_ID))

        val alarmIntent = channelSettingsIntent(context, CHANNEL_ALARM)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, alarmIntent.action)
        assertEquals(context.packageName, alarmIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(CHANNEL_ALARM, alarmIntent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }
}
