package eus.busdonostia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import eus.busdonostia.app.ui.components.LocalReducedMotion
import eus.busdonostia.app.ui.components.rememberSystemReducedMotion

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

@Composable
fun BusDonostiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        // Accesibilidad real: conecta "Quitar animaciones" (ANIMATOR_DURATION_SCALE == 0) con todo el pipeline de motion.
        LocalReducedMotion provides rememberSystemReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
