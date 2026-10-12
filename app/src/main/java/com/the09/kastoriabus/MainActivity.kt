package com.the09.kastoriabus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.the09.kastoriabus.ui.KastoriaBusAppUi
import com.the09.kastoriabus.ui.theme.KastoriaBusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen has a dark coloured header behind the status bar,
        // so the clock and battery icons should always be white.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            KastoriaBusTheme {
                KastoriaBusAppUi()
            }
        }
    }
}
