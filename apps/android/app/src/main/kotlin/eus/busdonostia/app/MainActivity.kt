package eus.busdonostia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eus.busdonostia.app.ui.AppShell
import eus.busdonostia.app.ui.theme.BusDonostiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BusDonostiaTheme {
                AppShell()
            }
        }
    }
}
