package eus.busdonostia.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.MotionTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El pulso LIVE nunca bloquea `waitForIdle()` en tests (P1-6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveSignalDotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `reduced motion no bloquea waitForIdle`() {
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Box { LiveSignalDot(isLive = true) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `pulso activo avanza sin excepcion`() {
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides false) {
                    Box { LiveSignalDot(isLive = true) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(MotionTokens.LivePulse.DURATION_MS.toLong())
        compose.waitForIdle()
    }
}
