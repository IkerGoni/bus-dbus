package eus.busdonostia.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.LocalStatusColors

/**
 * Ilustraciones vectoriales cívicas dibujadas nativamente con `drawWithCache` (cero PNGs ni Lottie).
 * Se adaptan dinámicamente al tema Light/Dark actual.
 */

@Composable
fun OnboardingBeaconIllustration(
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.illustrationSize,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val live = LocalStatusColors.current.live
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val stroke = w * 0.045f
                val busW = w * 0.44f
                val busH = h * 0.48f
                val busLeft = (w - busW) / 2f
                val busTop = h * 0.30f
                val arcPath = Path().apply {
                    moveTo(w * 0.28f, h * 0.22f)
                    quadraticTo(w * 0.50f, h * 0.08f, w * 0.72f, h * 0.22f)
                }
                val outerArc = Path().apply {
                    moveTo(w * 0.20f, h * 0.14f)
                    quadraticTo(w * 0.50f, -h * 0.02f, w * 0.80f, h * 0.14f)
                }
                onDrawBehind {
                    drawCircle(
                        color = primaryContainer,
                        radius = w * 0.46f,
                        center = Offset(w / 2f, h * 0.54f),
                    )
                    // Ondas de señal en tiempo real
                    drawPath(
                        path = arcPath,
                        color = live,
                        style = Stroke(width = stroke * 0.85f, cap = StrokeCap.Round),
                    )
                    drawPath(
                        path = outerArc,
                        color = live.copy(alpha = 0.45f),
                        style = Stroke(width = stroke * 0.75f, cap = StrokeCap.Round),
                    )
                    // Silueta limpia de autobús frontal
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(busLeft, busTop),
                        size = Size(busW, busH),
                        cornerRadius = CornerRadius(w * 0.09f),
                    )
                    // Parabrisas
                    drawRoundRect(
                        color = surface.copy(alpha = 0.92f),
                        topLeft = Offset(busLeft + busW * 0.13f, busTop + busH * 0.14f),
                        size = Size(busW * 0.74f, busH * 0.40f),
                        cornerRadius = CornerRadius(w * 0.04f),
                    )
                    // Faros
                    val lampR = w * 0.038f
                    drawCircle(
                        color = onPrimaryContainer.copy(alpha = 0.25f),
                        radius = lampR * 1.4f,
                        center = Offset(busLeft + busW * 0.24f, busTop + busH * 0.78f),
                    )
                    drawCircle(
                        color = surface,
                        radius = lampR,
                        center = Offset(busLeft + busW * 0.24f, busTop + busH * 0.78f),
                    )
                    drawCircle(
                        color = surface,
                        radius = lampR,
                        center = Offset(busLeft + busW * 0.76f, busTop + busH * 0.78f),
                    )
                }
            },
    )
}

@Composable
fun EmptyStopsIllustration(
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.illustrationSize,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val primary = MaterialTheme.colorScheme.primary
    val accent = LocalStatusColors.current.favorite
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val stroke = w * 0.045f
                onDrawBehind {
                    drawCircle(color = container, radius = w * 0.44f, center = Offset(w / 2f, h / 2f))
                    // Ruta horizontal esquemática
                    drawLine(
                        color = lineColor,
                        start = Offset(w * 0.16f, h * 0.66f),
                        end = Offset(w * 0.84f, h * 0.66f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    // Poste de parada
                    drawLine(
                        color = primary,
                        start = Offset(w * 0.50f, h * 0.30f),
                        end = Offset(w * 0.50f, h * 0.66f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = primary,
                        radius = w * 0.13f,
                        center = Offset(w * 0.50f, h * 0.30f),
                    )
                    drawCircle(
                        color = container,
                        radius = w * 0.055f,
                        center = Offset(w * 0.50f, h * 0.30f),
                    )
                    // Destello favorito
                    drawCircle(
                        color = accent,
                        radius = w * 0.05f,
                        center = Offset(w * 0.72f, h * 0.28f),
                    )
                }
            },
    )
}

@Composable
fun EmptyAlertsIllustration(
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.illustrationSize,
) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val primary = MaterialTheme.colorScheme.primary
    val live = LocalStatusColors.current.live
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val stroke = w * 0.048f
                val bellPath = Path().apply {
                    moveTo(w * 0.30f, h * 0.62f)
                    lineTo(w * 0.35f, h * 0.38f)
                    cubicTo(w * 0.37f, h * 0.24f, w * 0.63f, h * 0.24f, w * 0.65f, h * 0.38f)
                    lineTo(w * 0.70f, h * 0.62f)
                    close()
                }
                onDrawBehind {
                    drawCircle(color = container, radius = w * 0.44f, center = Offset(w / 2f, h / 2f))
                    drawPath(bellPath, color = primary)
                    drawLine(
                        color = primary,
                        start = Offset(w * 0.24f, h * 0.62f),
                        end = Offset(w * 0.76f, h * 0.62f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(color = primary, radius = w * 0.055f, center = Offset(w * 0.50f, h * 0.71f))
                    drawCircle(color = live, radius = w * 0.06f, center = Offset(w * 0.69f, h * 0.27f))
                }
            },
    )
}

@Composable
fun NoServiceIllustration(
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.illustrationSize,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val primary = MaterialTheme.colorScheme.primary
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                onDrawBehind {
                    drawCircle(color = container, radius = w * 0.44f, center = Offset(w / 2f, h / 2f))
                    // Luna creciente nocturna
                    drawCircle(color = primary, radius = w * 0.20f, center = Offset(w * 0.46f, h * 0.50f))
                    drawCircle(color = container, radius = w * 0.17f, center = Offset(w * 0.55f, h * 0.44f))
                    // Estrellas sutiles
                    drawCircle(color = tint.copy(alpha = 0.55f), radius = w * 0.03f, center = Offset(w * 0.68f, h * 0.32f))
                    drawCircle(color = tint.copy(alpha = 0.40f), radius = w * 0.022f, center = Offset(w * 0.74f, h * 0.46f))
                }
            },
    )
}

@Composable
fun ErrorSignalIllustration(
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.illustrationSize,
    tint: Color = MaterialTheme.colorScheme.error,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val stroke = w * 0.055f
                onDrawBehind {
                    drawCircle(color = container, radius = w * 0.44f, center = Offset(w / 2f, h / 2f))
                    drawCircle(
                        color = tint.copy(alpha = 0.25f),
                        radius = w * 0.26f,
                        center = Offset(w / 2f, h / 2f),
                        style = Stroke(width = stroke * 0.75f),
                    )
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.50f, h * 0.34f),
                        end = Offset(w * 0.50f, h * 0.53f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = tint,
                        radius = stroke * 0.65f,
                        center = Offset(w * 0.50f, h * 0.64f),
                    )
                }
            },
    )
}

@Composable
fun SearchEmptyIllustration(
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.illustrationSize,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val stroke = w * 0.055f
                val center = Offset(w * 0.44f, h * 0.44f)
                val radius = w * 0.16f
                onDrawBehind {
                    drawCircle(color = container, radius = w * 0.44f, center = Offset(w / 2f, h / 2f))
                    drawCircle(
                        color = tint,
                        radius = radius,
                        center = center,
                        style = Stroke(width = stroke),
                    )
                    drawLine(
                        color = tint,
                        start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
                        end = Offset(w * 0.70f, h * 0.70f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            },
    )
}
