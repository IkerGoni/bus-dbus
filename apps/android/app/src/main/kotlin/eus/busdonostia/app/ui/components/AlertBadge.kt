package eus.busdonostia.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.Badge
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eus.busdonostia.app.R

/**
 * Badge de avisos de la barra inferior. `AnimatedVisibility` cubre `0 ↔ >0`; `AnimatedContent`
 * anima además los cambios de número (`1 → 2 → … → 9+`). En reduced motion el texto cambia al
 * instante y el spring de entrada se resuelve en `snap()`.
 */
@Composable
internal fun AlertBadge(count: Int, text: String?) {
    val motion = rememberEffectiveMotionTokens()
    val reduced = LocalReducedMotion.current
    val springSpec = rememberMotionSpring<Float>(bouncy = true)
    val description = if (text != null) pluralStringResource(R.plurals.a11y_alerts_badge, count, count) else ""

    AnimatedVisibility(
        visible = text != null,
        enter = scaleIn(animationSpec = springSpec) + fadeIn(tween(motion.bottomTabFadeInMs)),
        exit = scaleOut(tween(motion.bottomTabFadeOutMs)) + fadeOut(tween(motion.bottomTabFadeOutMs)),
    ) {
        Badge(Modifier.semantics { contentDescription = description }) {
            val label = text.orEmpty()
            if (reduced) {
                Text(label, maxLines = 1)
            } else {
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        (scaleIn(animationSpec = springSpec) + fadeIn(tween(motion.bottomTabFadeInMs)))
                            .togetherWith(scaleOut(tween(motion.bottomTabFadeOutMs)) + fadeOut(tween(motion.bottomTabFadeOutMs)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "alertBadgeCount",
                ) { value ->
                    Text(
                        text = value,
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}
