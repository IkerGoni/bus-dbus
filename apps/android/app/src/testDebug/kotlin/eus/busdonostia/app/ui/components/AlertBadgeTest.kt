package eus.busdonostia.app.ui.components

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.badgeText
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun description(count: Int): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.resources.getQuantityString(R.plurals.a11y_alerts_badge, count, count)
    }

    @Test
    fun `anima los cambios de numero hasta 9+`() {
        var count by mutableIntStateOf(0)
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides false) {
                    AlertBadge(count = count, text = badgeText(count))
                }
            }
        }
        compose.onAllNodesWithText("0").assertCountEquals(0)
        compose.onAllNodesWithContentDescription(description(0)).assertCountEquals(0)

        count = 1
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("1").assertExists()
        compose.onNodeWithContentDescription(description(1)).assertExists()

        count = 2
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("2").assertExists()

        count = 12
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("9+").assertExists()
        compose.onNodeWithContentDescription(description(12)).assertExists()
    }

    @Test
    fun `reduced motion cambia al instante`() {
        var count by mutableIntStateOf(1)
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    AlertBadge(count = count, text = badgeText(count))
                }
            }
        }
        compose.onNodeWithText("1").assertExists()

        count = 12
        compose.waitForIdle()
        compose.onNodeWithText("9+").assertExists()
    }
}
