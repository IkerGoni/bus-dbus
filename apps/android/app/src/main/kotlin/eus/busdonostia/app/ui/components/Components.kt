package eus.busdonostia.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eus.busdonostia.app.R
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.ui.theme.CountdownRow
import eus.busdonostia.app.ui.theme.LocalStatusColors
import eus.busdonostia.app.ui.theme.MonoFamily

/** Fondo del badge: `linea.color` visto en vivo o neutro `#334155` si nunca se vio (DESIGN.md §3). */
@Composable
fun lineColor(colorArgb: Int?): Color = colorArgb?.let { Color(it).copy(alpha = 1f) } ?: LocalStatusColors.current.lineFallback

/** Texto blanco u oscuro según contraste con el fondo (≥ 4.5:1). */
private fun onLineColor(background: Color): Color {
    val white = (1.05) / (background.luminance() + 0.05)
    val dark = (background.luminance() + 0.05) / (Color(0xFF0F172A).luminance() + 0.05)
    return if (white >= dark) Color.White else Color(0xFF0F172A)
}

/** Cuadrado de línea Dbus: 28 dp en listas, 36 dp en cabeceras. */
@Composable
fun LineBadge(lineId: String, colorArgb: Int?, size: Dp = 28.dp) {
    val bg = lineColor(colorArgb)
    val description = stringResource(R.string.a11y_line, lineId)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 30.dp) 12.dp else 8.dp))
            .background(bg)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = lineId,
            color = onLineColor(bg),
            fontFamily = MonoFamily,
            fontSize = if (lineId.length > 2) 10.sp else 12.sp,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Código de parada en píldora (`340`). */
@Composable
fun StopCodePill(code: String) {
    Text(
        text = code,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontFamily = MonoFamily,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
        softWrap = false,
    )
}

/** Estrella 48×48 dp: rellena ámbar si está guardada, contorno gris si no. Persiste al pulsar. */
@Composable
fun FavoriteStar(checked: Boolean, onToggle: () -> Unit, description: String) {
    IconToggleButton(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = if (checked) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = description,
            tint = if (checked) LocalStatusColors.current.favorite else LocalStatusColors.current.scheduled,
        )
    }
}

/** Buscador pill único de 48 dp. */
@Composable
fun SearchPill(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(1.dp, LocalStatusColors.current.border, CircleShape),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Cabecera de 56 dp: título (+ subtítulo) a la izquierda y acciones de 48 dp a la derecha. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = if (navigation == null) 16.dp else 4.dp, end = 4.dp),
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
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        actions()
    }
}

/** Tarjeta con borde de 1 dp y radio 16 dp. */
@Composable
fun BorderedCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = androidx.compose.foundation.BorderStroke(1.dp, LocalStatusColors.current.border)
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            content()
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { content() }
    }
}

/** `● 3 min` verde en vivo, reloj gris en horario; `Ahora` si quedan 0 min. Nunca segundos. */
@Composable
fun MinutesLabel(minutes: Int, source: ArrivalSource) {
    val colors = LocalStatusColors.current
    val live = source == ArrivalSource.LIVE
    val tint = if (live) colors.live else colors.scheduled
    val sourceText = stringResource(if (live) R.string.source_live else R.string.source_scheduled)
    val text = if (minutes <= 0) stringResource(R.string.minutes_now) else stringResource(R.string.minutes_short, minutes)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$sourceText, $text" },
    ) {
        if (live) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        } else {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = CountdownRow, color = tint, maxLines = 1, softWrap = false)
    }
}

/** Esqueleto de carga: [rows] bloques grises del alto de una tarjeta. */
@Composable
fun SkeletonList(rows: Int = 4, rowHeight: Dp = 72.dp) {
    val description = stringResource(R.string.loading)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** Etiqueta de sección en mayúsculas (`CERCA DE TI`), sin saltos de línea. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Banner no bloqueante (avisos de Dbus, datos viejos, sin ubicación). */
@Composable
fun InfoBanner(text: String, warning: Boolean, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val colors = LocalStatusColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (warning) colors.warningContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (warning) colors.onWarningContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Box(Modifier.width(8.dp))
            action()
        }
    }
}
