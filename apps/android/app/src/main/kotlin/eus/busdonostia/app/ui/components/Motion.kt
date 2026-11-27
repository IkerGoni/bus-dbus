package eus.busdonostia.app.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.MotionTokens

/** CompositionLocal para accesibilidad: true cuando el usuario tiene "Quitar animaciones" activado. */
val LocalReducedMotion = compositionLocalOf { false }

/** Espacio inferior reservado para el Floating Pill Dock en pestañas raíz (`0.dp` en detalles). */
val LocalBottomDockInset = compositionLocalOf { 0.dp }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.optionalSharedElement(key: String): Modifier = composed {
    if (LocalReducedMotion.current) return@composed this
    val sharedScope = LocalSharedTransitionScope.current ?: return@composed this
    val visScope = LocalAnimatedVisibilityScope.current ?: return@composed this
    with(sharedScope) {
        this@composed.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visScope,
        )
    }
}

@Composable
fun AnimatedMinutesText(
    minutes: Int,
    formattedText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val reducedMotion = LocalReducedMotion.current
    val motion = rememberEffectiveMotionTokens()
    val tabularStyle = style.copy(fontFeatureSettings = "tnum")

    if (reducedMotion) {
        Text(
            text = formattedText,
            style = tabularStyle,
            color = color,
            modifier = modifier,
            maxLines = 1,
            softWrap = false,
        )
    } else {
        AnimatedContent(
            targetState = minutes to formattedText,
            transitionSpec = {
                val isDecreasing = targetState.first < initialState.first
                val enterOffset: (Int) -> Int = { height -> if (isDecreasing) -height else height }
                val exitOffset: (Int) -> Int = { height -> if (isDecreasing) height else -height }

                (
                    slideInVertically(
                        animationSpec = tween(
                            durationMillis = motion.minutesSlideInMs,
                            easing = FastOutSlowInEasing,
                        ),
                        initialOffsetY = enterOffset,
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = motion.minutesSlideInMs),
                    )
                    ).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(
                            durationMillis = motion.minutesSlideOutMs,
                            easing = FastOutSlowInEasing,
                        ),
                        targetOffsetY = exitOffset,
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = motion.minutesSlideOutMs),
                    ),
                )
            },
            label = "minutesSlide",
            modifier = modifier,
        ) { (_, text) ->
            Text(
                text = text,
                style = tabularStyle,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Lee `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (API real de Android para "Quitar animaciones").
 * En `@Preview` / Robolectric (`LocalInspectionMode.current`) devuelve `true` para no bloquear `waitForIdle()`.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * Devuelve los tokens de motion resueltos según accesibilidad.
 * Cuando `reducedMotion == true`, colapsa todas las duraciones a 0 y desactiva pulsos/rotaciones.
 */
@Composable
fun rememberEffectiveMotionTokens(): MotionTokens.ResolvedMotionTokens {
    val reducedMotion = LocalReducedMotion.current
    return MotionTokens.resolve(reducedMotion)
}

/** Spring de tokens; en reduced motion devuelve snap() (instantáneo). */
@Composable
fun <T> rememberMotionSpring(bouncy: Boolean = false): FiniteAnimationSpec<T> {
    val reduced = LocalReducedMotion.current
    val m = rememberEffectiveMotionTokens()
    return remember(reduced, bouncy) {
        if (reduced) {
            snap()
        } else {
            spring(
                dampingRatio = if (bouncy) m.springMediumBounceDamping else m.springNoBounceDamping,
                stiffness = m.springMediumStiffness,
            )
        }
    }
}

/** animateItem con tokens; en reduced motion, sin animación (specs = null). */
@Composable
fun LazyItemScope.motionAnimateItem(modifier: Modifier = Modifier): Modifier {
    val reduced = LocalReducedMotion.current
    val m = rememberEffectiveMotionTokens()
    return if (reduced) {
        modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        modifier.animateItem(
            fadeInSpec = tween(m.arrivalsStateFadeInMs),
            placementSpec = spring(m.springNoBounceDamping, m.springMediumStiffness),
            fadeOutSpec = tween(m.arrivalsStateFadeOutMs),
        )
    }
}

/**
 * Modifier de escala al pulsar (press feedback) operando EXCLUSIVAMENTE en `graphicsLayer`.
 * No dispara recomposición ni cambios de layout. Respeta `LocalReducedMotion`.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val motion = rememberEffectiveMotionTokens()
    val springSpec = rememberMotionSpring<Float>()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !reducedMotion) motion.pressScaleFactor else 1f,
        animationSpec = springSpec,
        label = "pressScale",
    )
    graphicsLayer {
        this.scaleX = scale
        this.scaleY = scale
    }
}

/**
 * Punto de pulso LIVE (GPS) — 8 dp, alpha animado `1.0 ↔ 0.3` en 1100 ms.
 * El texto de minutos permanece al 100% opacidad con `tnum`; solo pulsa el punto.
 * Respeta `LocalReducedMotion` y `LocalInspectionMode` (no bloquea tests).
 */
@Composable
fun LiveSignalDot(
    isLive: Boolean,
    modifier: Modifier = Modifier,
    dotSize: Dp = DesignTokens.liveSignalDotSize,
    liveColor: Color = MaterialTheme.colorScheme.primary,
    scheduledColor: Color = MaterialTheme.colorScheme.outline,
) {
    val reducedMotion = LocalReducedMotion.current
    val motion = rememberEffectiveMotionTokens()
    val animatePulse = isLive && !reducedMotion && !LocalInspectionMode.current
    val alpha = if (animatePulse) {
        val transition = rememberInfiniteTransition(label = "liveGpsPulse")
        transition.animateFloat(
            initialValue = motion.livePulseMaxAlpha,
            targetValue = motion.livePulseMinAlpha,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = motion.livePulseDurationMs,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "liveDotAlpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(dotSize)
            .graphicsLayer { this.alpha = alpha.value }
            .background(
                color = if (isLive) liveColor else scheduledColor,
                shape = CircleShape,
            ),
    )
}

/**
 * Baliza radar concéntrica para Hero Boards (`NextAlertCard`, `NextBusHeroBoard`).
 * Dibuja un núcleo sólido + halo expansivo sutil en fase de dibujo (`graphicsLayer`).
 * Desactivada bajo `LocalReducedMotion` y `LocalInspectionMode`.
 */
@Composable
fun LiveRadarBeacon(
    isLive: Boolean,
    liveColor: Color,
    scheduledColor: Color,
    modifier: Modifier = Modifier,
    coreSize: Dp = DesignTokens.liveSignalDotSize,
) {
    val reducedMotion = LocalReducedMotion.current
    val motion = rememberEffectiveMotionTokens()
    val animatePulse = isLive && !reducedMotion && !LocalInspectionMode.current

    val pulseProgress = if (animatePulse) {
        val transition = rememberInfiniteTransition(label = "liveRadarBeacon")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = motion.livePulseDurationMs,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "radarProgress",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.size(DesignTokens.iconMedium),
        contentAlignment = Alignment.Center,
    ) {
        if (animatePulse) {
            Box(
                modifier = Modifier
                    .size(DesignTokens.iconMedium)
                    .graphicsLayer {
                        val p = pulseProgress.value
                        val scale = 0.55f + (0.75f * p)
                        scaleX = scale
                        scaleY = scale
                        alpha = (1f - p) * 0.32f
                    }
                    .background(liveColor, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(coreSize)
                .background(if (isLive) liveColor else scheduledColor, CircleShape),
        )
    }
}

/** Protección sutil superior para garantizar legibilidad de iconos de estado al hacer scroll. */
@Composable
fun StatusBarScrim(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
) {
    val density = LocalDensity.current
    val topPx = WindowInsets.statusBars.getTop(density)
    if (topPx <= 0) return
    val heightDp = with(density) { (topPx * 1.15f).toDp() }
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .drawWithCache {
                val brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.92f),
                        color.copy(alpha = 0.55f),
                        Color.Transparent,
                    ),
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            },
    )
}
