package eus.busdonostia.app.ui.stopdetail

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalRow
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.ui.components.LocalReducedMotion
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StopDetailArrivalsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `aviso de Dbus y estado vacio en ArrivalsStatus no se solapan`() {
        val notice = "Desvío por obras en Boulevard"
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Column {
                        ArrivalsStatus(
                            view = ArrivalsView.Empty(notice = notice),
                            onRetry = {},
                        )
                    }
                }
            }
        }

        val banner = compose.onNodeWithText(context.getString(R.string.stop_notice, notice)).getBoundsInRoot()
        val emptyTitle = compose.onNodeWithText(context.getString(R.string.stop_empty_title)).getBoundsInRoot()

        assertTrue("El mensaje vacío debe quedar debajo del banner de aviso", emptyTitle.top >= banner.bottom)
    }

    @Test
    fun `NextBusHeroBoard no expone stopId`() {
        val row = ArrivalRow(
            key = "hero-1",
            arrival = Arrival("28", "32", "Ospitaleak", 3, "10:15", ArrivalSource.LIVE, 1_700_000_000_000L, null),
            minutes = 3,
        )
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    NextBusHeroBoard(
                        row = row,
                        colorArgb = null,
                        stopName = "Boulevard 15",
                        starred = false,
                        onStar = {},
                        onBell = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Ospitaleak").assertExists()
        compose.onNodeWithText("1281", substring = true).assertDoesNotExist()
    }
}
