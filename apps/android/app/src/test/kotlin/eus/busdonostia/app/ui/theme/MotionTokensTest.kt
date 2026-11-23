package eus.busdonostia.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTokensTest {
    @Test
    fun fullExponeLosTokensNuevos() {
        val full = MotionTokens.resolve(false)
        assertEquals(0.8f, full.searchClearScaleFactor, 0f)
        assertEquals(0.0625f, full.arrivalsStateSlideFraction, 0f)
        assertEquals(1100, full.livePulseDurationMs)
        assertEquals(0.3f, full.livePulseMinAlpha, 0f)
        assertEquals(1f, full.livePulseMaxAlpha, 0f)
        assertEquals(200, full.minutesSlideInMs)
        assertEquals(160, full.minutesSlideOutMs)
        assertEquals(35, full.staggerItemDelayMs)
        assertEquals(8, full.staggerMaxAnimatedIndex)
        assertEquals(220, full.arrivalsStateFadeInMs)
        assertEquals(150, full.arrivalsStateFadeOutMs)
        assertEquals(150, full.bottomTabFadeInMs)
    }

    @Test
    fun zeroColapsaLosTokensNuevos() {
        val zero = MotionTokens.resolve(true)
        assertEquals(1f, zero.searchClearScaleFactor, 0f)
        assertEquals(0f, zero.arrivalsStateSlideFraction, 0f)
        assertEquals(0, zero.livePulseDurationMs)
        assertEquals(1f, zero.livePulseMinAlpha, 0f)
        assertEquals(1f, zero.livePulseMaxAlpha, 0f)
        assertEquals(0, zero.minutesSlideInMs)
        assertEquals(0, zero.minutesSlideOutMs)
        assertEquals(0, zero.staggerItemDelayMs)
        assertEquals(0, zero.staggerMaxAnimatedIndex)
        assertEquals(0, zero.arrivalsStateFadeInMs)
        assertEquals(0, zero.arrivalsStateFadeOutMs)
        assertEquals(0, zero.bottomTabFadeInMs)
    }

    @Test
    fun fullYZeroSonSimetricosEnEscalas() {
        val full = MotionTokens.ResolvedMotionTokens.FULL
        val zero = MotionTokens.ResolvedMotionTokens.ZERO
        assertEquals(1f, zero.pressScaleFactor, 0f)
        assertEquals(1f, zero.searchClearScaleFactor, 0f)
        assertEquals(1f, zero.favoriteStarMinScale, 0f)
        assertEquals(1f, zero.favoriteStarPeakScale, 0f)
        assertEquals(0f, zero.arrivalsStateSlideFraction, 0f)
        assertEquals(0f, zero.favoriteStarRotationDeg, 0f)
        assertEquals(0.97f, full.pressScaleFactor, 0f)
        assertEquals(0.8f, full.favoriteStarMinScale, 0f)
        assertEquals(1.15f, full.favoriteStarPeakScale, 0f)
    }

    @Test
    fun designTokensGeneradosDesdeJson() {
        assertEquals(8.dp, DesignTokens.liveSignalDotSize)
        assertEquals(12.dp, DesignTokens.spacingMd)
        assertEquals(36.dp, DesignTokens.lineBadgeMinWidth)
        assertEquals(16.dp, DesignTokens.cornerRadiusCard)
    }
}
