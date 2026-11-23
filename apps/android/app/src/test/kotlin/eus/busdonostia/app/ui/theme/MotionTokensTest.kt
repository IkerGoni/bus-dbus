package eus.busdonostia.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tokens de motion nuevos de Sprint 5.1: valores FULL en paridad con design-tokens.v1.template.json y colapso en ZERO. */
class MotionTokensTest {
    @Test
    fun fullExponeLosTokensNuevos() {
        val full = MotionTokens.resolve(false)
        assertEquals(0.8f, full.searchClearScaleFactor, 0f)
        assertEquals(0.0625f, full.arrivalsStateSlideFraction, 0f)
    }

    @Test
    fun zeroColapsaLosTokensNuevos() {
        val zero = MotionTokens.resolve(true)
        assertEquals(1f, zero.searchClearScaleFactor, 0f)
        assertEquals(0f, zero.arrivalsStateSlideFraction, 0f)
    }

    @Test
    fun fullYZeroSonSimetricosEnEscalas() {
        // Todo factor de escala colapsa a 1 (inactivo) en ZERO; fracciones y grados, a 0.
        val full = MotionTokens.ResolvedMotionTokens.FULL
        val zero = MotionTokens.ResolvedMotionTokens.ZERO
        assertEquals(1f, zero.pressScaleFactor, 0f)
        assertEquals(1f, zero.searchClearScaleFactor, 0f)
        assertEquals(1f, zero.favoriteStarMinScale, 0f)
        assertEquals(1f, zero.favoriteStarPeakScale, 0f)
        assertEquals(0f, zero.arrivalsStateSlideFraction, 0f)
        assertEquals(0f, zero.favoriteStarRotationDeg, 0f)
        // FULL conserva los valores del template sin colapsar.
        assertEquals(0.97f, full.pressScaleFactor, 0f)
        assertEquals(0.8f, full.favoriteStarMinScale, 0f)
        assertEquals(1.15f, full.favoriteStarPeakScale, 0f)
    }
}
