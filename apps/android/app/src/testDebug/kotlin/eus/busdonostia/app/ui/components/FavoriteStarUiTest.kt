package eus.busdonostia.app.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoriteStarUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `estrella mantiene target de 48dp y dispara onToggle al pulsar`() {
        var checked by mutableStateOf(false)
        var toggles = 0
        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    FavoriteStar(
                        checked = checked,
                        onToggle = {
                            toggles++
                            checked = !checked
                        },
                        description = "Favorito",
                    )
                }
            }
        }

        val node = compose.onNodeWithContentDescription("Favorito")
        node.assertExists()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        node.performClick()
        compose.waitForIdle()

        assertEquals(1, toggles)
        assertEquals(true, checked)
    }
}
