package eus.busdonostia.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
    val alarm: Color,
    val alarmContainer: Color,
    val onAlarmContainer: Color,
    val favorite: Color,
    val border: Color,
    val lineFallback: Color,
)

val LightStatusColors = StatusColors(
    live = Color(0xFF047857),
    liveContainer = Color(0xFFD1FAE5),
    onLiveContainer = Color(0xFF065F46),
    scheduled = Color(0xFF475569),
    scheduledContainer = Color(0xFFEEF2F6),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F),
    alarm = Color(0xFFC81E1E),
    alarmContainer = Color(0xFFFEE2E2),
    onAlarmContainer = Color(0xFF991B1B),
    favorite = Color(0xFFD97706),
    border = Color(0xFFE2E8F0),
    lineFallback = Color(0xFF334155),
)

val DarkStatusColors = StatusColors(
    live = Color(0xFF34D399),
    liveContainer = Color(0xFF064E3B),
    onLiveContainer = Color(0xFFA7F3D0),
    scheduled = Color(0xFF9DB0C9),
    scheduledContainer = Color(0xFF1D2A40),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF451A03),
    onWarningContainer = Color(0xFFFDE68A),
    alarm = Color(0xFFF87171),
    alarmContainer = Color(0xFF4C1118),
    onAlarmContainer = Color(0xFFFECACA),
    favorite = Color(0xFFFBBF24),
    border = Color(0xFF263449),
    lineFallback = Color(0xFF334155),
)

val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF0052A8),
    inversePrimary = Color(0xFF6EB2FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F0FD),
    onPrimaryContainer = Color(0xFF00448E),
    secondary = Color(0xFF0E7490),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6F0FD),
    onSecondaryContainer = Color(0xFF00448E),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE9EEF5),
    surfaceContainerHighest = Color(0xFFDCE4EE),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFC81E1E),
    onError = Color(0xFFFFFFFF),
)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF6EB2FF),
    inversePrimary = Color(0xFF0052A8),
    onPrimary = Color(0xFF042347),
    primaryContainer = Color(0xFF17345A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF17345A),
    onSecondaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF070B12),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF151F30),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1D2A40),
    onSurfaceVariant = Color(0xFF9DB0C9),
    surfaceContainerLowest = Color(0xFF0B111C),
    surfaceContainerLow = Color(0xFF101827),
    surfaceContainer = Color(0xFF151F30),
    surfaceContainerHigh = Color(0xFF1D2A40),
    surfaceContainerHighest = Color(0xFF263651),
    outline = Color(0xFF7E93B0),
    outlineVariant = Color(0xFF263449),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)
