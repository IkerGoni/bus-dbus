package eus.busdonostia.app.ui.theme

/**
 * MotionTokens — Valores de animación y microinteracción.
 * Fuente de verdad: packages/contracts/design-tokens.json (Sprint 6 build-time).
 * En Sprint 5.1 se mantiene manualmente idéntico a contracts-templates/design-tokens.v1.template.json.
 * R8 inlina todas las constantes (0 ms runtime). No crear parsers JSON en runtime.
 */
object MotionTokens {
    // Límite máximo bloqueante (ninguna transición de navegación/carga puede superarlo).
    const val MAX_BLOCKING_DURATION_MS = 350

    // Navegación: Bottom Bar vs Detalle
    object Navigation {
        // Pestañas principales (HOME, STOPS, LINES, ALERTS): solo fade, sin slide.
        const val BOTTOM_TAB_FADE_IN_MS = 150
        const val BOTTOM_TAB_FADE_OUT_MS = 100
        // Rutas de detalle (stop, line, alert/new, alert/edit, settings): slide corto + fade.
        const val DETAIL_SLIDE_IN_MS = 260
        const val DETAIL_FADE_IN_MS = 220
        const val DETAIL_SLIDE_OUT_MS = 220
        const val DETAIL_FADE_OUT_MS = 180
        // Fracción de desplazamiento (1/6 ≈ 0.166) para compatibilidad con Predictive Back (targetSdk 36).
        const val DETAIL_SLIDE_FRACTION = 0.166f
    }

    // Transiciones de estado de datos y UI
    object Transitions {
        // ArrivalsView (Loading, Live, Empty, Degraded, Error)
        const val ARRIVALS_STATE_FADE_IN_MS = 220
        const val ARRIVALS_STATE_FADE_OUT_MS = 150
        // Cambio de estado de ArrivalsView: fracción de slide vertical (1/16 = 0.0625)
        const val ARRIVALS_STATE_SLIDE_FRACTION = 0.0625f
        // Contador de minutos
        const val MINUTES_SLIDE_IN_MS = 200
        const val MINUTES_SLIDE_OUT_MS = 160
        // Botón limpiar búsqueda
        const val SEARCH_CLEAR_BUTTON_MS = 160
        // InfoBanner (estado degradado, permiso notificaciones, notice config)
        const val INFO_BANNER_MS = 200
        // Cambio de sentido en LineDetailScreen
        const val LINE_DIRECTION_CROSSFADE_MS = 200
        // Wizard de 4 pasos en AlertEditScreen
        const val WIZARD_STEP_SLIDE_MS = 240
    }

    // Microinteracciones táctiles
    object MicroInteractions {
        // Factor de escala al pulsar (pressScale)
        const val PRESS_SCALE_FACTOR = 0.97f
        // FavoriteStar: escala mínima (inactivo), pico (overshoot), final
        const val FAVORITE_STAR_MIN_SCALE = 0.8f
        const val FAVORITE_STAR_PEAK_SCALE = 1.15f
        // FavoriteStar: rotación 72° (simetría de 5 puntas, evita desenfoque de 360°)
        const val FAVORITE_STAR_ROTATION_DEG = 72.0f
        // Springs
        const val SPRING_NO_BOUNCE_DAMPING = 1.0f
        const val SPRING_MEDIUM_BOUNCE_DAMPING = 0.5f
        const val SPRING_MEDIUM_STIFFNESS = 1500.0f
        // Botón limpiar búsqueda: escala inicial/final al aparecer/desaparecer
        const val SEARCH_CLEAR_SCALE_FACTOR = 0.8f
    }

    // Pulso de GPS LIVE (LiveSignalDot)
    object LivePulse {
        const val DURATION_MS = 1100
        const val MIN_ALPHA = 0.3f
        const val MAX_ALPHA = 1.0f
    }

    // Stagger para listas (tope de 8 ítems para líneas de 35+ paradas)
    object Stagger {
        const val ITEM_DELAY_MS = 35
        const val MAX_ANIMATED_INDEX = 8
    }

    // Celebración (partículas en eventos in-app: 1ª estrella / 1er aviso)
    object Celebration {
        const val PARTICLES_DURATION_MS = 800
    }

    /**
     * Tokens resueltos según accesibilidad (reducedMotion).
     * Cuando reducedMotion == true, devuelve duraciones en 0 y desactiva pulsos/rotaciones.
     */
    data class ResolvedMotionTokens(
        // Navegación
        val bottomTabFadeInMs: Int,
        val bottomTabFadeOutMs: Int,
        val detailSlideInMs: Int,
        val detailFadeInMs: Int,
        val detailSlideOutMs: Int,
        val detailFadeOutMs: Int,
        val detailSlideFraction: Float,

        // Transiciones
        val arrivalsStateFadeInMs: Int,
        val arrivalsStateFadeOutMs: Int,
        val arrivalsStateSlideFraction: Float,
        val minutesSlideInMs: Int,
        val minutesSlideOutMs: Int,
        val searchClearButtonMs: Int,
        val infoBannerMs: Int,
        val lineDirectionCrossfadeMs: Int,
        val wizardStepSlideMs: Int,

        // Microinteracciones
        val pressScaleFactor: Float,
        val favoriteStarMinScale: Float,
        val favoriteStarPeakScale: Float,
        val favoriteStarRotationDeg: Float,
        val springNoBounceDamping: Float,
        val springMediumBounceDamping: Float,
        val springMediumStiffness: Float,
        val searchClearScaleFactor: Float,

        // LivePulse
        val livePulseDurationMs: Int,
        val livePulseMinAlpha: Float,
        val livePulseMaxAlpha: Float,

        // Stagger
        val staggerItemDelayMs: Int,
        val staggerMaxAnimatedIndex: Int,

        // Celebration
        val particlesDurationMs: Int,
    ) {
        companion object {
            val FULL = ResolvedMotionTokens(
                bottomTabFadeInMs = Navigation.BOTTOM_TAB_FADE_IN_MS,
                bottomTabFadeOutMs = Navigation.BOTTOM_TAB_FADE_OUT_MS,
                detailSlideInMs = Navigation.DETAIL_SLIDE_IN_MS,
                detailFadeInMs = Navigation.DETAIL_FADE_IN_MS,
                detailSlideOutMs = Navigation.DETAIL_SLIDE_OUT_MS,
                detailFadeOutMs = Navigation.DETAIL_FADE_OUT_MS,
                detailSlideFraction = Navigation.DETAIL_SLIDE_FRACTION,

                arrivalsStateFadeInMs = Transitions.ARRIVALS_STATE_FADE_IN_MS,
                arrivalsStateFadeOutMs = Transitions.ARRIVALS_STATE_FADE_OUT_MS,
                arrivalsStateSlideFraction = Transitions.ARRIVALS_STATE_SLIDE_FRACTION,
                minutesSlideInMs = Transitions.MINUTES_SLIDE_IN_MS,
                minutesSlideOutMs = Transitions.MINUTES_SLIDE_OUT_MS,
                searchClearButtonMs = Transitions.SEARCH_CLEAR_BUTTON_MS,
                infoBannerMs = Transitions.INFO_BANNER_MS,
                lineDirectionCrossfadeMs = Transitions.LINE_DIRECTION_CROSSFADE_MS,
                wizardStepSlideMs = Transitions.WIZARD_STEP_SLIDE_MS,

                pressScaleFactor = MicroInteractions.PRESS_SCALE_FACTOR,
                favoriteStarMinScale = MicroInteractions.FAVORITE_STAR_MIN_SCALE,
                favoriteStarPeakScale = MicroInteractions.FAVORITE_STAR_PEAK_SCALE,
                favoriteStarRotationDeg = MicroInteractions.FAVORITE_STAR_ROTATION_DEG,
                springNoBounceDamping = MicroInteractions.SPRING_NO_BOUNCE_DAMPING,
                springMediumBounceDamping = MicroInteractions.SPRING_MEDIUM_BOUNCE_DAMPING,
                springMediumStiffness = MicroInteractions.SPRING_MEDIUM_STIFFNESS,
                searchClearScaleFactor = MicroInteractions.SEARCH_CLEAR_SCALE_FACTOR,

                livePulseDurationMs = LivePulse.DURATION_MS,
                livePulseMinAlpha = LivePulse.MIN_ALPHA,
                livePulseMaxAlpha = LivePulse.MAX_ALPHA,

                staggerItemDelayMs = Stagger.ITEM_DELAY_MS,
                staggerMaxAnimatedIndex = Stagger.MAX_ANIMATED_INDEX,

                particlesDurationMs = Celebration.PARTICLES_DURATION_MS,
            )

            val ZERO = ResolvedMotionTokens(
                bottomTabFadeInMs = 0,
                bottomTabFadeOutMs = 0,
                detailSlideInMs = 0,
                detailFadeInMs = 0,
                detailSlideOutMs = 0,
                detailFadeOutMs = 0,
                detailSlideFraction = 0f,

                arrivalsStateFadeInMs = 0,
                arrivalsStateFadeOutMs = 0,
                arrivalsStateSlideFraction = 0f,
                minutesSlideInMs = 0,
                minutesSlideOutMs = 0,
                searchClearButtonMs = 0,
                infoBannerMs = 0,
                lineDirectionCrossfadeMs = 0,
                wizardStepSlideMs = 0,

                pressScaleFactor = 1f,
                favoriteStarMinScale = 1f,
                favoriteStarPeakScale = 1f,
                favoriteStarRotationDeg = 0f,
                springNoBounceDamping = 1f,
                springMediumBounceDamping = 1f,
                springMediumStiffness = 1f,
                searchClearScaleFactor = 1f,

                livePulseDurationMs = 0,
                livePulseMinAlpha = 1f,
                livePulseMaxAlpha = 1f,

                staggerItemDelayMs = 0,
                staggerMaxAnimatedIndex = 0,

                particlesDurationMs = 0,
            )
        }
    }

    /**
     * Devuelve los tokens resueltos según accesibilidad.
     * Usar desde `rememberEffectiveMotionTokens()` en Motion.kt.
     */
    fun resolve(reducedMotion: Boolean): ResolvedMotionTokens =
        if (reducedMotion) ResolvedMotionTokens.ZERO else ResolvedMotionTokens.FULL
}