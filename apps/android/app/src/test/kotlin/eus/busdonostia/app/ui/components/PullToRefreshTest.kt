package eus.busdonostia.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToRefreshTest {
    @Test
    fun `haptica solo al cruzar el umbral hacia arriba`() {
        assertTrue(crossedRefreshThreshold(0.5f, 1.0f))
        assertFalse(crossedRefreshThreshold(1.0f, 1.2f))
        assertFalse(crossedRefreshThreshold(1.2f, 0.8f))
        assertTrue(crossedRefreshThreshold(0.8f, 1.0f))
    }
}
