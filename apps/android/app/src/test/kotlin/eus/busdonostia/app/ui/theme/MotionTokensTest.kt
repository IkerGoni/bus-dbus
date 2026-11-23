package eus.busdonostia.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokensTest {
    @Test
    fun zeroNoRalentizaMuelles() {
        val zero = MotionTokens.resolve(true)
        assertTrue(zero.springMediumStiffness >= 10_000f)
        assertEquals(1f, zero.springNoBounceDamping, 0f)
        assertEquals(1f, zero.springMediumBounceDamping, 0f)
    }

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
    fun zeroColapsaTodosLosCampos() {
        val zero = MotionTokens.resolve(true)
        val fields = MotionTokens.ResolvedMotionTokens::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertTrue(fields.size > 20)
        for (field in fields) {
            field.isAccessible = true
            val name = field.name
            val value: Any = field.get(zero)!!
            when {
                name.endsWith("Ms") -> assertEquals("Field $name should be 0", 0, value)
                name.endsWith("Index") -> assertEquals("Field $name should be 0", 0, value)
                name == "springMediumStiffness" -> assertTrue("Stiffness should be >= 10000f", (value as Float) >= 10_000f)
                name.endsWith("Damping") -> assertEquals("Field $name should be 1f", 1f, value as Float, 0f)
                name.endsWith("Scale") || name.endsWith("ScaleFactor") -> assertEquals("Field $name should be 1f", 1f, value as Float, 0f)
                name.endsWith("Fraction") || name.endsWith("Deg") -> assertEquals("Field $name should be 0f", 0f, value as Float, 0f)
                name.endsWith("Alpha") -> assertEquals("Field $name should be 1f", 1f, value as Float, 0f)
            }
        }
    }

    @Test
    fun designTokensGeneradosDesdeJson() {
        assertEquals(8.dp, DesignTokens.liveSignalDotSize)
        assertEquals(12.dp, DesignTokens.spacingMd)
        assertEquals(36.dp, DesignTokens.lineBadgeMinWidth)
        assertEquals(16.dp, DesignTokens.cornerRadiusCard)
    }
}
