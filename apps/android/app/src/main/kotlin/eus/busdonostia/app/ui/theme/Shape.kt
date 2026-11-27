package eus.busdonostia.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(DesignTokens.cornerRadiusChip),
    small = RoundedCornerShape(DesignTokens.cornerRadiusBanner),
    medium = RoundedCornerShape(DesignTokens.cornerRadiusCard),
    large = RoundedCornerShape(DesignTokens.cornerRadiusHero),
    extraLarge = RoundedCornerShape(DesignTokens.cornerRadiusDock),
)
