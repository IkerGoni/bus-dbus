package eus.busdonostia.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eus.busdonostia.app.BusDonostiaApp
import eus.busdonostia.app.BuildConfig
import eus.busdonostia.app.R
import eus.busdonostia.app.data.settings.AppLanguage
import eus.busdonostia.app.data.settings.ThemeMode
import eus.busdonostia.app.data.settings.currentAppLanguage
import eus.busdonostia.app.data.settings.setAppLanguage
import eus.busdonostia.app.domain.Catalog
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.ScreenHeader
import eus.busdonostia.app.ui.components.SectionLabel
import eus.busdonostia.app.ui.components.SkeletonList
import eus.busdonostia.app.ui.system.batterySettingsIntent
import eus.busdonostia.app.ui.system.notificationSettingsIntent
import eus.busdonostia.app.ui.system.openExternalUrl
import eus.busdonostia.app.ui.system.openSettings
import eus.busdonostia.app.ui.system.privacyPolicyIntent
import eus.busdonostia.app.ui.system.readSystemStatus
import eus.busdonostia.app.ui.theme.DesignTokens
import eus.busdonostia.app.ui.theme.MonoFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A07 · Ajustes. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as BusDonostiaApp).container }
    // Idioma de la app (es/eu), no el del sistema: el móvil puede estar en inglés y la app en español.
    val appLanguage = currentAppLanguage(LocalConfiguration.current.locales[0].language)
    val locale = Locale.forLanguageTag(appLanguage.tag)
    val theme by container.settings.theme.collectAsStateWithLifecycle()
    val lastFetchMs by container.settings.lastLiveFetchMs.collectAsStateWithLifecycle()
    val catalog by container.catalog.collectAsStateWithLifecycle()

    // Estado real del sistema: se relee al volver de Ajustes.
    var status by remember { mutableStateOf(readSystemStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readSystemStatus(context)
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
        )

        SectionLabel(stringResource(R.string.settings_section_appearance))
        BorderedCard(Modifier.padding(horizontal = DesignTokens.spacingLg)) {
            CardColumn {
                val modes = listOf(
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark,
                )
                Segmented(modes.map { stringResource(it.second) }, modes.indexOfFirst { it.first == theme }) {
                    container.settings.setTheme(modes[it].first)
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_section_language))
        BorderedCard(Modifier.padding(horizontal = DesignTokens.spacingLg)) {
            CardColumn {
                val langs = listOf(
                    AppLanguage.ES to R.string.settings_language_es,
                    AppLanguage.EU to R.string.settings_language_eu,
                    AppLanguage.EN to R.string.settings_language_en,
                )
                val current = appLanguage
                Segmented(langs.map { stringResource(it.second) }, langs.indexOfFirst { it.first == current }) {
                    setAppLanguage(langs[it].first)
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_section_notifications))
        BorderedCard(Modifier.padding(horizontal = DesignTokens.spacingLg)) {
            CardColumn {
                val hasBlockedChannels = status.notificationsEnabled && (!status.arrivalsChannelEnabled || !status.alarmChannelEnabled)
                val statusTextRes = when {
                    !status.notificationsEnabled -> R.string.settings_notifications_off
                    hasBlockedChannels -> R.string.settings_notifications_channels_blocked
                    else -> R.string.settings_notifications_on
                }
                Text(
                    stringResource(statusTextRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!status.notificationsEnabled) {
                    Hint(stringResource(R.string.settings_notifications_off_hint))
                } else if (hasBlockedChannels) {
                    Hint(stringResource(R.string.settings_notifications_channels_blocked_hint))
                }
                ActionButton(stringResource(R.string.settings_open_system)) {
                    context.openSettings(notificationSettingsIntent(context))
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_section_background))
        BorderedCard(Modifier.padding(horizontal = DesignTokens.spacingLg)) {
            CardColumn {
                Text(stringResource(R.string.settings_battery), style = MaterialTheme.typography.titleMedium)
                if (status.batteryUnrestricted) {
                    Hint(stringResource(R.string.settings_battery_ok))
                } else {
                    Hint(stringResource(R.string.settings_battery_restricted))
                    ActionButton(stringResource(R.string.settings_battery_change)) {
                        context.openSettings(batterySettingsIntent())
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_section_data))
        val loaded = catalog
        when {
            loaded == null -> SkeletonList(rows = 1)
            loaded.isFailure -> BorderedCard(Modifier.padding(horizontal = DesignTokens.spacingLg)) {
                CardColumn { Text(stringResource(R.string.catalog_error_title), style = MaterialTheme.typography.bodyMedium) }
            }
            else -> {
                val cat = loaded.getOrThrow()
                BorderedCard(Modifier.padding(horizontal = DesignTokens.spacingLg)) {
                    CardColumn {
                        Text(
                            stringResource(
                                R.string.settings_counts_join,
                                pluralStringResource(R.plurals.stops_count, cat.stops.size, cat.stops.size),
                                pluralStringResource(R.plurals.lines_count, cat.lines.size, cat.lines.size),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        catalogDate(cat, locale)?.let { ValueRow(stringResource(R.string.settings_catalog_date), it) }
                        val last = lastFetchMs
                        if (last != null) {
                            ValueRow(stringResource(R.string.settings_live_last), liveDate(last, locale))
                        } else {
                            Hint(stringResource(R.string.settings_live_never))
                        }
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_section_about))
        BorderedCard(Modifier.padding(horizontal = DesignTokens.spacingLg)) {
            CardColumn {
                ValueRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                Hint(stringResource(R.string.settings_disclaimer))
                Hint(stringResource(R.string.settings_credit_times))
                Hint(stringResource(R.string.settings_credit_stops))
                Hint(
                    loaded?.getOrNull()?.let { stringResource(R.string.osm_attribution, it.coordinates.attribution) }
                        ?: stringResource(R.string.settings_credit_osm),
                )
                Hint(stringResource(R.string.settings_credit_fonts))
                ActionButton(stringResource(R.string.settings_privacy_policy)) {
                    context.openExternalUrl(privacyPolicyIntent(appLanguage))
                }
            }
        }
        Spacer(Modifier.height(DesignTokens.spacingXl))
    }
}

private fun catalogDate(catalog: Catalog, locale: Locale): String? = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        .format(Instant.parse(catalog.generatedAt).atZone(ZoneId.systemDefault()))
}.getOrNull()

private fun liveDate(ms: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(DesignTokens.spacingLg), verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Etiqueta en prosa y valor numérico en mono con `tnum`. */
@Composable
private fun ValueRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = DesignTokens.touchTargetMin)) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                modifier = Modifier.heightIn(min = DesignTokens.touchTargetMin),
            ) {
                Text(label, maxLines = 1, softWrap = false)
            }
        }
    }
}
