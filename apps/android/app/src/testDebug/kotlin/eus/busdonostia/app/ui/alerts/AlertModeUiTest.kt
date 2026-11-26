package eus.busdonostia.app.ui.alerts

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.AlertDraft
import eus.busdonostia.app.domain.AlertMode
import eus.busdonostia.app.ui.components.LocalReducedMotion
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertModeUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `selector de modo muestra ayuda segun modo y dispara callback al cambiar`() {
        var mode by mutableStateOf(AlertMode.NOTIFICATION)
        val observed = mutableListOf<AlertMode>()

        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Column {
                        AlertModeSelector(
                            mode = mode,
                            alarmTimeoutSec = 90,
                            onChange = {
                                observed += it
                                mode = it
                            },
                        )
                    }
                }
            }
        }

        val notifLabel = context.getString(R.string.alert_mode_notification)
        val alarmLabel = context.getString(R.string.alert_mode_alarm)
        val notifHint = context.getString(R.string.alert_mode_notification_hint)
        val alarmHint90 = context.getString(R.string.alert_mode_alarm_hint, 90)

        compose.onNodeWithText(notifLabel).assertExists()
        compose.onNodeWithText(alarmLabel).assertExists()
        compose.onNodeWithText(notifHint).assertExists()

        compose.onNodeWithText(alarmLabel).performClick()
        compose.waitForIdle()

        assertEquals(listOf(AlertMode.ALARM), observed)
        compose.onNodeWithText(alarmHint90).assertExists()
    }

    @Test
    fun `vista previa muestra nota de alarma con alarmTimeoutSec y nunca expone stopId`() {
        var state by mutableStateOf(
            AlertEditUiState(
                loading = false,
                draft = AlertDraft(
                    stopId = "340",
                    stopName = "Pío Baroja 10",
                    lineId = "28",
                    destination = "Amara",
                    minutesAhead = 5,
                    mode = AlertMode.NOTIFICATION,
                ),
                stopName = "Pío Baroja 10",
                alarmTimeoutSec = 90,
            ),
        )

        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Column {
                        Preview(state)
                    }
                }
            }
        }

        val note90 = context.getString(R.string.alert_preview_alarm_note, 90)
        compose.onNodeWithText(note90).assertDoesNotExist()

        state = state.copy(draft = state.draft.copy(mode = AlertMode.ALARM))
        compose.waitForIdle()

        compose.onNodeWithText(note90).assertExists()
        compose.onNodeWithText("340", substring = true).assertDoesNotExist()
    }
}
