package eus.busdonostia.app.ui.components

import eus.busdonostia.app.ui.theme.MotionTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class StaggerTest {
    @Test
    fun `retardo acotado a maxAnimatedIndex`() {
        val full = MotionTokens.ResolvedMotionTokens.FULL
        assertEquals(0, staggerDelayMs(0, full))
        assertEquals(105, staggerDelayMs(3, full))
        assertEquals(280, staggerDelayMs(8, full))
        assertEquals(280, staggerDelayMs(35, full))
    }

    @Test
    fun `sin retardo en reduced motion`() {
        assertEquals(0, staggerDelayMs(35, MotionTokens.ResolvedMotionTokens.ZERO))
    }
}
