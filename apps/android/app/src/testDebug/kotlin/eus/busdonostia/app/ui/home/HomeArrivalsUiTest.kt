package eus.busdonostia.app.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalRow
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.ArrivalsView
import eus.busdonostia.app.domain.DegradedReason
import eus.busdonostia.app.ui.components.LocalReducedMotion
import eus.busdonostia.app.ui.stops.StopRow
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeArrivalsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `varias llegadas en FavoriteArrivals se apilan verticalmente sin solaparse`() {
        val now = 1_700_000_000_000L
        val arrivals = listOf(
            Arrival("28", "32", "Ospitaleak", 2, "10:12", ArrivalSource.LIVE, now, null),
            Arrival("5", "5", "Benta Berri", 6, "10:16", ArrivalSource.LIVE, now, null),
            Arrival("13", "13", "Altza", 11, "10:21", ArrivalSource.SCHEDULED, now, null),
        )
        val card = FavoriteStopCard(
            row = StopRow("1281", "Boulevard 15", listOf("28", "5", "13"), starred = true),
            view = ArrivalsView.Degraded(DegradedReason.STALE, arrivals, null, now - 60_000L),
            arrivals = arrivals,
            rows = arrivals.mapIndexed { idx, a -> ArrivalRow("k-$idx", a, a.minutesAtFetch) },
        )

        compose.setContent {
            BusDonostiaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    FavoriteArrivals(
                        card = card,
                        lineColors = emptyMap(),
                        onRetry = {},
                    )
                }
            }
        }

        val first = compose.onNodeWithText("Ospitaleak").getBoundsInRoot()
        val second = compose.onNodeWithText("Benta Berri").getBoundsInRoot()
        val third = compose.onNodeWithText("Altza").getBoundsInRoot()

        assertTrue("La segunda fila debe quedar debajo de la primera", second.top >= first.bottom)
        assertTrue("La tercera fila debe quedar debajo de la segunda", third.top >= second.bottom)
    }
}
