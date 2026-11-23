package eus.busdonostia.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * DesignTokens — Espaciados, radios y dimensiones de componentes.
 * Fuente de verdad: packages/contracts/design-tokens.json (Sprint 6 build-time).
 * En Sprint 5.1 se mantiene manualmente idéntico a contracts-templates/design-tokens.v1.template.json.
 * R8 inlina todas las constantes. No crear parsers JSON en runtime.
 */
object DesignTokens {
    // Espaciados (8 pt base)
    object Spacing {
        val XS = 4.dp
        val SM = 8.dp
        val MD = 12.dp
        val LG = 16.dp
        val XL = 24.dp
    }

    // Radios de esquina estándar
    object CornerRadius {
        val CHIP = 8.dp
        val CARD = 16.dp
        val PILL = 999.dp
    }

    // Dimensiones de componentes específicas
    object Components {
        // Timeline (LineDetailScreen)
        val TIMELINE_TERMINAL_NODE = 8.dp
        val TIMELINE_INTERMEDIATE_NODE = 6.dp
        // LiveSignalDot (punto GPS en MinutesLabel)
        val LIVE_SIGNAL_DOT_SIZE = 8.dp
        // LineBadge ancho mínimo (evita colapso en códigos de 1-2 chars)
        val LINE_BADGE_MIN_WIDTH = 36.dp
    }

    // Accesos directos para compatibilidad con código existente
    val spacingXs: Dp = Spacing.XS
    val spacingSm: Dp = Spacing.SM
    val spacingMd: Dp = Spacing.MD
    val spacingLg: Dp = Spacing.LG
    val spacingXl: Dp = Spacing.XL

    val cornerRadiusChip: Dp = CornerRadius.CHIP
    val cornerRadiusCard: Dp = CornerRadius.CARD
    val cornerRadiusPill: Dp = CornerRadius.PILL

    val timelineTerminalNode: Dp = Components.TIMELINE_TERMINAL_NODE
    val timelineIntermediateNode: Dp = Components.TIMELINE_INTERMEDIATE_NODE
    val liveSignalDotSize: Dp = Components.LIVE_SIGNAL_DOT_SIZE
    val lineBadgeMinWidth: Dp = Components.LINE_BADGE_MIN_WIDTH
}