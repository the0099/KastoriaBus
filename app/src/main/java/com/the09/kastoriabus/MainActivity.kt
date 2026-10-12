package com.the09.kastoriabus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.the09.kastoriabus.ui.KastoriaBusAppUi
import com.the09.kastoriabus.ui.theme.KastoriaBusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KastoriaBusTheme {
                KastoriaBusAppUi()
            }
        }
    }
}
