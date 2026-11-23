package eus.busdonostia.app.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import eus.busdonostia.app.R
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimatedMinutesTextTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `de 5 a 4 con animacion y sin residuo`() {
        var minutes by mutableIntStateOf(5)
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides false) {
                    AnimatedMinutesText(
                        minutes = minutes,
                        formattedText = stringResource(R.string.minutes_short, minutes),
                    )
                }
            }
        }
        compose.onNodeWithText("5 min").assertExists()

        minutes = 4
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("4 min").assertExists()
        compose.onNodeWithText("5 min").assertDoesNotExist()
    }

    @Test
    fun `reduced motion cambia al instante`() {
        var minutes by mutableIntStateOf(5)
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    AnimatedMinutesText(
                        minutes = minutes,
                        formattedText = stringResource(R.string.minutes_short, minutes),
                    )
                }
            }
        }
        compose.onNodeWithText("5 min").assertExists()

        minutes = 4
        compose.waitForIdle()
        compose.onNodeWithText("4 min").assertExists()
    }
}
