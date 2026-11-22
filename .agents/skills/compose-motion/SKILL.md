---
name: compose-motion
description: Implementa microanimaciones fluidas a 60/120 fps, feedback háptico y transiciones de navegación en Jetpack Compose (Material 3) para Bus Donostia usando MotionTokens compilados, accesibilidad real (ANIMATOR_DURATION_SCALE) y compatibilidad estricta con ADR 009, Lint y Robolectric.
---

# Microanimaciones y Motion Design — Bus Donostia (Jetpack Compose)

## 1. Principios Innegociables
1. **Cero Lottie en UI estándar:** Usar exclusivamente APIs nativas de Compose (`animate*AsState`, `AnimatedContent`, `AnimatedVisibility`, `Crossfade`, `graphicsLayer`).
2. **Cero valores mágicos inline:** Toda duración, escala, ángulo, *spring* y *stagger* se lee de `rememberEffectiveMotionTokens()` (respaldado por `MotionTokens`, generado desde `packages/contracts/design-tokens.json` a partir de v1.1).
3. **Límite máximo bloqueante:** Ninguna transición de pantalla o estado puede superar `MotionTokens.MAX_BLOCKING_DURATION_MS` (`350 ms`).
4. **Cero datos simulados (ADR 009):** Nunca interpolar segundos falsos entre polls de 30 s ni animar barras de progreso estimadas.

---

## 2. Reglas de Rendimiento (120 fps) y Accesibilidad
- **Fase de dibujo (`graphicsLayer`):** En listas (`LazyColumn`), leer siempre `scale`, `alpha` y `rotationZ` dentro de `Modifier.graphicsLayer { ... }` para saltar Composición y Layout.
- **Dígitos tabulares (`tnum`):** Todos los minutos restantes y horas `HH:mm` deben aplicar `fontFeatureSettings = "tnum"`.
- **Accesibilidad real:** Leer `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` en `LocalReducedMotion`. Cuando es `true`, `rememberEffectiveMotionTokens()` colapsa duraciones a `0` y desactiva bucles infinitos.
- **Seguridad en Robolectric:** Todo `rememberInfiniteTransition` debe condicionarse a `isActive && !reducedMotion && !LocalInspectionMode.current` para evitar *timeouts* en `composeTestRule.waitForIdle()`.
- **Lint `HardcodedText = ERROR`:** Todo `contentDescription` debe usar `stringResource(R.string.xxx)` (con paridad ES/EU) o `null` si es decorativo.

---

## 3. Patrones Aprobados por Pantalla

| Pantalla / Componente | Patrón obligatorio | Detalle clave |
| :--- | :--- | :--- |
| **`AppShell.kt` (BottomBar)** | `fadeIn` / `fadeOut` puro (`150/100 ms`) | Prohibido deslizamiento lateral entre `HOME`, `STOPS`, `LINES`, `ALERTS`. |
| **`AppShell.kt` (Detalle)** | `slideIntoContainer(it * 0.166) + fadeIn` | Compatible con *Predictive Back* (`targetSdk 36`). |
| **`ArrivalsView` (5 estados)** | `AnimatedContent(contentKey = { it::class })` | Evita doble altura en Skeleton → Live y evita re-animar cada 30 s con el `Ticker`. |
| **`FavoriteStar`** | `scale 0.8→1.15→1` (spring) + rotación **`72°`** + color + háptica | Nunca `360°`; háptica solo ante interacción táctil del usuario. |
| **GPS `ArrivalSource.LIVE`** | Pulso `1f ↔ 0.3f` (`1100 ms`) en **`LiveSignalDot` (`8dp`)** | El texto de minutos permanece al **100% de opacidad** con `tnum`. |
| **`sharedElement()` (v1.1)** | Claves compuestas `"line-${stopId}-${lineId}"` | Desactivado si `reducedMotion == true`. |
| **`LineDetailScreen`** | `Crossfade` al cambiar sentido + stagger `minOf(index, 8) * 35ms` | Tope de 8 ítems para no retrasar líneas largas de 35+ paradas. |
| **`AlertEditScreen`** | `AnimatedContent` direccional (izq/der) + stepper con háptica | Refuerza dirección del wizard de 4 pasos. |
| **`Mascot.kt` (Canvas)** | Solo en Onboarding y estados vacíos | Prohibido en `HomeScreen` con contenido activo. |

---

## 4. Primitivas de Referencia (`ui/components/Motion.kt`)

```kotlin
package eus.dbus.donostia.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import eus.dbus.donostia.ui.theme.DesignTokens
import eus.dbus.donostia.ui.theme.MotionTokens

val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberSystemReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

fun Modifier.pressScale(
    interactionSource: MutableInteractionSource
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) MotionTokens.PRESS_SCALE_FACTOR else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = MotionTokens.SPRING_MEDIUM_STIFFNESS
        ),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun LiveSignalDot(
    isLive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.LiveSignalDotSize,
    liveColor: Color = MaterialTheme.colorScheme.primary,
    scheduledColor: Color = MaterialTheme.colorScheme.outline
) {
    val reducedMotion = LocalReducedMotion.current
    val animatePulse = isLive && !reducedMotion && !LocalInspectionMode.current
    val alpha = if (animatePulse) {
        val transition = rememberInfiniteTransition(label = "liveGpsPulse")
        val animatedAlpha by transition.animateFloat(
            initialValue = MotionTokens.LIVE_PULSE_MAX_ALPHA,
            targetValue = MotionTokens.LIVE_PULSE_MIN_ALPHA,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = MotionTokens.LIVE_PULSE_DURATION_MS,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "liveDotAlpha"
        )
        animatedAlpha
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .background(
                color = if (isLive) liveColor else scheduledColor,
                shape = CircleShape
            )
    )
}
```
