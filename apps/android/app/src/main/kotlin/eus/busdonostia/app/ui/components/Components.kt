package eus.busdonostia.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.CountdownRow
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.LocalStatusColors
import eus.busdonostia.app.ui.theme.MonoFamily

/** Fondo del badge: `linea.color` visto en vivo o neutro `#334155` si nunca se vio. */
@Composable
fun lineColor(colorArgb: Int?): Color = colorArgb?.let { Color(it).copy(alpha = 1f) } ?: LocalStatusColors.current.lineFallback

/** Texto blanco u oscuro según contraste con el fondo (≥ 4.5:1). */
private fun onLineColor(background: Color): Color {
    val white = (1.05) / (background.luminance() + 0.05)
    val dark = (background.luminance() + 0.05) / (Color(0xFF0F172A).luminance() + 0.05)
    return if (white >= dark) Color.White else Color(0xFF0F172A)
}

/** Cuadrado/píldora oficial de línea Dbus: 28 dp en listas, 36-40 dp en cabeceras/Hero. */
@Composable
fun LineBadge(
    lineId: String,
    colorArgb: Int?,
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.lineBadgeSize,
) {
    val bg = lineColor(colorArgb)
    val description = stringResource(R.string.a11y_line, lineId)
    val corner = when {
        size >= DesignTokens.lineBadgeLarge -> DesignTokens.cornerRadiusBanner
        size > DesignTokens.lineBadgeSize -> DesignTokens.cornerRadiusBanner
        else -> DesignTokens.cornerRadiusChip
    }
    val fontSize = when {
        size >= DesignTokens.lineBadgeLarge && lineId.length <= 2 -> 15.sp
        size >= DesignTokens.lineBadgeLarge -> 12.sp
        lineId.length > 2 -> 10.sp
        else -> 12.sp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = lineId,
            color = onLineColor(bg),
            fontFamily = MonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * `FilterChip` con indicador tonal animado por física spring en la fase de dibujo.
 * Conserva semántica, ripple, objetivo táctil de 48 dp y accesibilidad M3.
 */
@Composable
fun MotionFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = rememberMotionSpring(),
        label = "chipSelection",
    )
    val indicator = MaterialTheme.colorScheme.secondaryContainer
    val motion = rememberEffectiveMotionTokens()
    val radius = DesignTokens.cornerRadiusChip
    val visualHeight = DesignTokens.chipVisualHeight
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = Color.Transparent,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
        modifier = modifier
            .heightIn(min = DesignTokens.touchTargetMin)
            .drawBehind {
                if (progress <= 0f) return@drawBehind
                val start = motion.chipIndicatorStartScale
                val width = size.width * (start + (1f - start) * progress)
                val height = visualHeight.toPx()
                drawRoundRect(
                    color = indicator.copy(alpha = progress),
                    topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(radius.toPx()),
                )
            },
    )
}

/**
 * Estrella 48×48 dp: `interactionSource` conectado a `IconToggleButton` y `pressScale`,
 * escala normalizada `1.0f` en reposo con pulso elástico `1.0 → 1.15 → 1.0` al activar,
 * rotación `72°`, transición de color y háptica `LongPress`.
 */
@Composable
fun FavoriteStar(
    checked: Boolean,
    onToggle: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val motion = rememberEffectiveMotionTokens()
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    val springSpec = rememberMotionSpring<Float>(bouncy = true)
    val popScale = remember { Animatable(1f) }
    var previousChecked by remember { mutableStateOf(checked) }

    LaunchedEffect(checked) {
        if (checked && !previousChecked && !reducedMotion && motion.arrivalsStateFadeInMs > 0) {
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = motion.arrivalsStateFadeInMs
                    motion.favoriteStarMinScale at 0
                    motion.favoriteStarPeakScale at (motion.arrivalsStateFadeInMs / 2)
                    1f at motion.arrivalsStateFadeInMs
                },
            )
        } else {
            popScale.snapTo(1f)
        }
        previousChecked = checked
    }

    val rotation by animateFloatAsState(
        targetValue = if (checked) motion.favoriteStarRotationDeg else 0f,
        animationSpec = springSpec,
        label = "favoriteStarRotation",
    )

    val color by animateColorAsState(
        targetValue = if (checked) LocalStatusColors.current.favorite else LocalStatusColors.current.scheduled,
        animationSpec = tween(motion.lineDirectionCrossfadeMs),
        label = "favoriteStarColor",
    )

    IconToggleButton(
        checked = checked,
        onCheckedChange = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .size(DesignTokens.touchTargetMin)
            .graphicsLayer {
                val scale = popScale.value
                this.scaleX = scale
                this.scaleY = scale
                this.rotationZ = rotation
            }
            .pressScale(interactionSource),
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = description,
            tint = color,
        )
    }
}

/** Buscador pill tonal de 48 dp con anillo de foco animado y botón de borrado háptico. */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val motion = rememberEffectiveMotionTokens()
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(motion.searchClearButtonMs),
        label = "searchFocusBorder",
    )

    TextField(
        value = query,
        onValueChange = onQueryChange,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.touchTargetMin)
            .border(1.dp, borderColor, CircleShape),
        placeholder = {
            Text(
                placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(tween(motion.searchClearButtonMs)) +
                    scaleIn(initialScale = motion.searchClearScaleFactor, animationSpec = tween(motion.searchClearButtonMs)),
                exit = fadeOut(tween(motion.searchClearButtonMs)) +
                    scaleOut(targetScale = motion.searchClearScaleFactor, animationSpec = tween(motion.searchClearButtonMs)),
                label = "searchClearButton",
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onQueryChange("")
                    },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Cabecera de 56 dp+: título (+ subtítulo) a la izquierda y acciones de 48 dp a la derecha. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleModifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .heightIn(min = DesignTokens.headerMinHeight)
            .padding(
                start = if (navigation == null) DesignTokens.spacingLg else DesignTokens.spacingXs,
                end = DesignTokens.spacingXs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** Tarjeta tonal elevada con contorno sutil y `pressScale` en interacciones. */
@Composable
fun BorderedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shape: RoundedCornerShape = RoundedCornerShape(DesignTokens.cornerRadiusCard),
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier
                .fillMaxWidth()
                .pressScale(interactionSource),
            shape = shape,
            colors = colors,
            border = border,
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
        ) {
            content()
        }
    }
}

/**
 * Tarjeta Hero con raíl lateral de color oficial Dbus (`4.dp`) y jerarquía tonal elevada.
 * Base visual del Signature Moment 1 (`NextAlertCard` y `NextBusHeroBoard`).
 */
@Composable
fun AccentRailCard(
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(DesignTokens.cornerRadiusHero)
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) Modifier.pressScale(interactionSource) else Modifier
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val railWidth = DesignTokens.accentRailWidth

    val cardContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val railPx = railWidth.toPx()
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - railPx else 0f
                    drawRect(
                        color = accentColor,
                        topLeft = Offset(x, 0f),
                        size = Size(railPx, size.height),
                    )
                }
                .padding(start = railWidth),
        ) {
            content()
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier.fillMaxWidth().then(clickModifier),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = border,
        ) {
            cardContent()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = border,
        ) {
            cardContent()
        }
    }
}

/** `● 3 min` esmeralda en vivo, reloj en horario; píldora destacada si `≤ 2 min` en vivo. Nunca segundos. */
@Composable
fun MinutesLabel(
    minutes: Int,
    source: ArrivalSource,
    modifier: Modifier = Modifier,
    highlightImminent: Boolean = true,
) {
    val colors = LocalStatusColors.current
    val live = source == ArrivalSource.LIVE
    val imminent = highlightImminent && live && minutes <= 2
    val tint = if (live) colors.live else colors.scheduled
    val sourceText = stringResource(if (live) R.string.source_live else R.string.source_scheduled)
    val text = if (minutes <= 0) stringResource(R.string.minutes_now) else stringResource(R.string.minutes_short, minutes)
    val a11y = "$sourceText, $text"

    if (imminent) {
        Surface(
            color = colors.liveContainer,
            shape = RoundedCornerShape(DesignTokens.cornerRadiusPill),
            modifier = modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
                modifier = Modifier.padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
            ) {
                LiveSignalDot(isLive = true, liveColor = colors.onLiveContainer)
                AnimatedMinutesText(
                    minutes = minutes,
                    formattedText = text,
                    style = CountdownRow,
                    color = colors.onLiveContainer,
                )
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
            modifier = modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
        ) {
            if (live) {
                LiveSignalDot(isLive = true, liveColor = tint)
            } else {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(DesignTokens.iconSmall),
                )
            }
            AnimatedMinutesText(minutes = minutes, formattedText = text, style = CountdownRow, color = tint)
        }
    }
}

enum class SkeletonVariant { CARD, HERO, ARRIVAL_ROW }

/** Esqueleto de carga con shimmer tonal en fase de dibujo (desactivado en reduced motion y tests). */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    rowHeight: Dp = DesignTokens.skeletonRowHeight,
    variant: SkeletonVariant = SkeletonVariant.CARD,
) {
    val description = stringResource(R.string.loading)
    val reducedMotion = LocalReducedMotion.current
    val motion = rememberEffectiveMotionTokens()
    val animateShimmer = !reducedMotion && !LocalInspectionMode.current && motion.livePulseShimmerDurationMs > 0
    val shimmerOffset = if (animateShimmer) {
        val transition = rememberInfiniteTransition(label = "skeletonShimmer")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.livePulseShimmerDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmerProgress",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val effectiveHeight = when (variant) {
        SkeletonVariant.HERO -> DesignTokens.heroSkeletonHeight
        SkeletonVariant.ARRIVAL_ROW -> DesignTokens.arrivalRowMinHeight
        SkeletonVariant.CARD -> rowHeight
    }
    val shapeRadius = when (variant) {
        SkeletonVariant.HERO -> DesignTokens.cornerRadiusHero
        else -> DesignTokens.cornerRadiusCard
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DesignTokens.spacingLg)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        repeat(rows) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(effectiveHeight)
                    .clip(RoundedCornerShape(shapeRadius))
                    .drawWithCache {
                        val shimmerBrush = Brush.linearGradient(
                            colors = listOf(baseColor, highlightColor, baseColor),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.65f, size.height),
                        )
                        onDrawBehind {
                            drawRect(color = baseColor)
                            if (animateShimmer) {
                                val shift = size.width * (shimmerOffset.value * 2f - 0.65f)
                                withTransform({ translate(left = shift) }) {
                                    drawRect(brush = shimmerBrush)
                                }
                            }
                        }
                    },
            )
        }
    }
}

/** Etiqueta de sección en mayúsculas con seguimiento limpio, sin saltos de línea. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    horizontalPadding: Dp = DesignTokens.spacingLg,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = DesignTokens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.7.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(DesignTokens.spacingSm))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Banner tonal no bloqueante (avisos de Dbus, datos viejos, canales bloqueados, sin ubicación). */
@Composable
fun InfoBanner(
    text: String,
    warning: Boolean,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    val motion = rememberEffectiveMotionTokens()
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(motion.infoBannerMs)) + fadeIn(tween(motion.infoBannerMs)),
        exit = shrinkVertically(animationSpec = tween(motion.infoBannerMs)) + fadeOut(tween(motion.infoBannerMs)),
        label = "infoBanner",
    ) {
        val colors = LocalStatusColors.current
        val bg = if (warning) colors.warningContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        val fg = if (warning) colors.onWarningContainer else MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DesignTokens.cornerRadiusBanner))
                .background(bg)
                .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(DesignTokens.iconMedium),
            )
            Spacer(Modifier.width(DesignTokens.spacingSm))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                Spacer(Modifier.width(DesignTokens.spacingXs))
                action()
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CoreComponentsPreview() {
    BusDonostiaTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                LineBadge("28", -16753739, size = DesignTokens.lineBadgeLarge)
                LineBadge("B4", -12556862)
                MinutesLabel(1, ArrivalSource.LIVE)
                MinutesLabel(9, ArrivalSource.SCHEDULED)
                FavoriteStar(checked = true, onToggle = {}, description = "")
            }
            InfoBanner(visible = true, text = "Desvío temporal en Boulevard", warning = true)
        }
    }
}
