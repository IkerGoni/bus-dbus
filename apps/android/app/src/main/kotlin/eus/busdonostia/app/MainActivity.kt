package eus.busdonostia.app

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
        super.onCreate(savedInstanceState)
        val settings = (application as BusDonostiaApp).container.settings
        setContent {
            BusDonostiaTheme {
                val onboardingDone by settings.onboardingDone.collectAsStateWithLifecycle()
                // A08 solo en la primera apertura; después, directamente Inicio.
                if (onboardingDone) AppShell() else OnboardingScreen(onFinished = settings::setOnboardingDone)
            }
        }
    }
}
