package eus.busdonostia.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eus.busdonostia.app.R

// Fuentes variables empaquetadas (OFL, ver apps/android/licenses/).

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, weights: List<Int>): FontFamily =
    FontFamily(
        weights.map { w ->
            Font(
                resId = resId,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        },
    )

val SansFamily = variableFamily(R.font.plus_jakarta_sans, listOf(400, 500, 600, 700, 800))
val MonoFamily = variableFamily(R.font.jetbrains_mono, listOf(600, 700, 800))

/** Cifras monumentales en Hero Boards y Dial de antelación (`tnum`). */
val CountdownHero = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight(800),
    fontSize = 42.sp,
    lineHeight = 46.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum",
)

/** Cifra destacada de tarjeta secundaria (`tnum`). */
val CountdownCard = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight(800),
    fontSize = 24.sp,
    lineHeight = 28.sp,
    fontFeatureSettings = "tnum",
)

/** Cifra estándar en filas de llegadas (`tnum`). */
val CountdownRow = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight(700),
    fontSize = 17.sp,
    lineHeight = 22.sp,
    fontFeatureSettings = "tnum",
)

/** Horas `HH:mm`, índices de parada y metadatos numéricos (`tnum`). */
val MonoClock = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight(600),
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontFeatureSettings = "tnum",
)

private fun sans(size: Int, weight: Int, line: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = SansFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/** Escala completa de 15 roles Material 3 en Plus Jakarta Sans (sin fallback a Roboto). */
val AppTypography = Typography(
    displayLarge = sans(57, 700, 64, -0.25f),
    displayMedium = sans(45, 700, 52),
    displaySmall = sans(36, 700, 44),
    headlineLarge = sans(32, 700, 40, -0.3f),
    headlineMedium = sans(28, 700, 36, -0.25f),
    headlineSmall = sans(24, 700, 32, -0.2f),
    titleLarge = sans(20, 700, 28, -0.15f),
    titleMedium = sans(16, 700, 22),
    titleSmall = sans(14, 600, 20),
    bodyLarge = sans(16, 500, 22),
    bodyMedium = sans(14, 500, 20),
    bodySmall = sans(12, 500, 16),
    labelLarge = sans(14, 600, 20),
    labelMedium = sans(12, 600, 16, 0.25f),
    labelSmall = sans(11, 600, 14, 0.2f),
)
