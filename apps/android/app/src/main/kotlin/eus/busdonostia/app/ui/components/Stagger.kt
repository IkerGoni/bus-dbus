package eus.busdonostia.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.MotionTokens
import kotlinx.coroutines.delay

/** Retardo del stagger: `minOf(index, maxAnimatedIndex) * itemDelayMs`. Puro y testeable. */
fun staggerDelayMs(index: Int, motion: MotionTokens.ResolvedMotionTokens): Int =
    minOf(index, motion.staggerMaxAnimatedIndex) * motion.staggerItemDelayMs

/**
 * Estado de entrada de una lista: solo las filas compuestas en el primer frame tras cambiar
 * [triggerKey] animan. Las que aparecen después (scroll) se pintan sin animación.
 */
@Stable
class StaggerEntranceState {
    var open by mutableStateOf(true)
        internal set
}

@Composable
fun rememberStaggerEntrance(triggerKey: Any): StaggerEntranceState {
    val state = remember(triggerKey) { StaggerEntranceState() }
    LaunchedEffect(state) {
        withFrameNanos { }
        withFrameNanos { }
        state.open = false
    }
    return state
}

/**
 * Entrada escalonada de una fila. La decisión de animar se toma **una vez** al componer la fila
 * (`remember(state)`), así el scroll no re-anima nada. Respeta reduced motion e [LocalInspectionMode].
 */
@Composable
fun Modifier.staggerEntrance(index: Int, state: StaggerEntranceState): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val isInspection = LocalInspectionMode.current
    val motion = rememberEffectiveMotionTokens()
    val animate = remember(state) { state.open } && !reducedMotion && !isInspection && motion.arrivalsStateFadeInMs > 0
    if (!animate) return this
    val progress = remember(state, index) { Animatable(0f) }
    val density = LocalDensity.current
    val offsetPx = with(density) { DesignTokens.spacingMd.toPx() }
    LaunchedEffect(state, index) {
        delay(staggerDelayMs(index, motion).toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = motion.arrivalsStateFadeInMs,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}
