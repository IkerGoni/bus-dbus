package eus.busdonostia.app.data.settings

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Apariencia elegida en A07. */
enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
}

/**
 * Preferencias locales (A07, A08). Nada de ubicación ni datos personales.
 * El idioma no se guarda aquí: lo guarda AppCompat (`autoStoreLocales`).
 *
 * Cero I/O en Main Thread (A5-13): [prefsProvider] solo se evalúa en
 * [Dispatchers.IO] dentro de [init]; hasta entonces los valores son los neutros
 * y [ready] es false. La UI espera a [ready] antes de decidir qué pintar.
 */
class SettingsStore(
    prefsProvider: () -> SharedPreferences,
    scope: CoroutineScope,
) {
    private val prefs: SharedPreferences by lazy(prefsProvider)

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _lastLiveFetchMs = MutableStateFlow<Long?>(null)

    /** Hora de la última respuesta real de Dbus (no `API_ERROR`); null si nunca hubo. */
    val lastLiveFetchMs: StateFlow<Long?> = _lastLiveFetchMs.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** true cuando las preferencias ya se leyeron de disco (en IO). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val p = prefs
            _theme.value = readTheme(p)
            _onboardingDone.value = p.getBoolean(KEY_ONBOARDING, false)
            _lastLiveFetchMs.value = p.getLong(KEY_LAST_FETCH, 0L).takeIf { it > 0 }
            _ready.value = true
        }
    }

    private fun readTheme(p: SharedPreferences): ThemeMode =
        p.getString(KEY_THEME, null)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM

    /** Persiste y aplica la apariencia (AppCompat recrea la actividad). */
    fun setTheme(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _theme.value = mode
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun setOnboardingDone() {
        prefs.edit { putBoolean(KEY_ONBOARDING, true) }
        _onboardingDone.value = true
    }

    fun recordLiveFetch(atMs: Long) {
        if (atMs <= (_lastLiveFetchMs.value ?: 0L)) return
        prefs.edit { putLong(KEY_LAST_FETCH, atMs) }
        _lastLiveFetchMs.value = atMs
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_LAST_FETCH = "last_live_fetch_ms"
    }
}

/** Idiomas de la app (ADR 009): es por defecto, eu. */
enum class AppLanguage(val tag: String) { ES("es"), EU("eu") }

/** Idioma efectivo: el elegido en A07 o, si no hay, el que resolvió el sistema (es si no es eu). */
fun currentAppLanguage(resolvedLanguage: String): AppLanguage {
    val chosen = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: resolvedLanguage
    return if (chosen == AppLanguage.EU.tag) AppLanguage.EU else AppLanguage.ES
}

/** Cambia el idioma de la app: `LocaleManager` en Android 13+, AppCompat lo guarda y aplica antes. */
fun setAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(language.tag))
}
