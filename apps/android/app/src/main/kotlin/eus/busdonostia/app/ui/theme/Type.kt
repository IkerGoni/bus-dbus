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

/** Cifras y códigos: JetBrains Mono con `tnum` (tokens `countdownHero` / `countdownRow`). */
val CountdownHero = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight(800),
    fontSize = 30.sp,
    lineHeight = 34.sp,
    fontFeatureSettings = "tnum",
)
val CountdownRow = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight(700),
    fontSize = 16.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "tnum",
)

private fun sans(size: Int, weight: Int, line: Int) = TextStyle(
    fontFamily = SansFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
)

val AppTypography = Typography(
    headlineSmall = sans(24, 700, 32), // screenTitle
    titleMedium = sans(16, 700, 22), // cardTitle
    bodyMedium = sans(14, 500, 20), // body
    bodyLarge = sans(16, 500, 22),
    labelLarge = sans(14, 600, 20),
    labelMedium = sans(12, 600, 16), // sectionLabel
    labelSmall = sans(11, 600, 14), // navLabel
)
