package eus.busdonostia.app

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eus.busdonostia.app.ui.AppShell
import eus.busdonostia.app.ui.onboarding.OnboardingScreen
import eus.busdonostia.app.ui.theme.BusDonostiaTheme

/** AppCompat: idioma por app (es/eu) y apariencia de A07 también en Android < 13. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        val settings = (application as BusDonostiaApp).container.settings
        setContent {
            BusDonostiaTheme {
                // Los ajustes se leen de disco en IO (A5-13): hasta entonces, nada de decidir
                // onboarding vs Inicio con un valor neutro (evita el flash de A08 en returning users).
                val ready by settings.ready.collectAsStateWithLifecycle()
                if (ready) {
                    val onboardingDone by settings.onboardingDone.collectAsStateWithLifecycle()
                    // A08 solo en la primera apertura; después, directamente Inicio.
                    if (onboardingDone) AppShell() else OnboardingScreen(onFinished = settings::setOnboardingDone)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // App visible: arranque permitido. Retoma la vigilancia si se paró (sin servicio, reinicio, límite de Android).
        (application as BusDonostiaApp).container.monitoring.onAppOpened()
    }
}
