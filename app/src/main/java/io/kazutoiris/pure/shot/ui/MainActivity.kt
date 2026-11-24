package io.kazutoiris.pure.shot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.kazutoiris.pure.shot.ui.settings.SettingsScreen
import io.kazutoiris.pure.shot.ui.theme.PureShotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PureShotTheme {
                SettingsScreen()
            }
        }
    }
}
