package eus.busdonostia.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Valores de docs/07-android-mvp/design-system/design-tokens.json (color.light / color.dark).

@Immutable
data class StatusColors(
    val live: Color,
    val liveContainer: Color,
    val onLiveContainer: Color,
    val scheduled: Color,
    val scheduledContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val favorite: Color,
    val border: Color,
    val lineFallback: Color,
)

val LightStatusColors = StatusColors(
    live = Color(0xFF059669),
    liveContainer = Color(0xFFD1FAE5),
    onLiveContainer = Color(0xFF065F46),
    scheduled = Color(0xFF64748B),
    scheduledContainer = Color(0xFFF1F5F9),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF92400E),
    favorite = Color(0xFFF59E0B),
    border = Color(0xFFE2E8F0),
    lineFallback = Color(0xFF334155),
)

val DarkStatusColors = StatusColors(
    live = Color(0xFF34D399),
    liveContainer = Color(0xFF064E3B),
    onLiveContainer = Color(0xFFA7F3D0),
    scheduled = Color(0xFF94A3B8),
    scheduledContainer = Color(0xFF334155),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF451A03),
    onWarningContainer = Color(0xFFFDE68A),
    favorite = Color(0xFFFBBF24),
    border = Color(0xFF334155),
    lineFallback = Color(0xFF334155),
)

val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF005BB5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = Color(0xFF005BB5),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF273549),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF1E293B),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)
