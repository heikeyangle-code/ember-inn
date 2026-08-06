package com.emberinn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.emberinn.app.ui.MainScreen
import com.emberinn.app.ui.theme.EmberInnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmberInnTheme {
                MainScreen()
            }
        }
    }
}
