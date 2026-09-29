package com.scut.engine

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.scut.ui.screens.MainScreen
import com.scut.ui.theme.ScutTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // the theme is light-only, so keep dark system-bar icons even in system dark mode
        val lightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)
        super.onCreate(savedInstanceState)

        val viewModel = ViewModelProvider(this).get(EngineViewModel::class.java)
        val scripts = ScriptManager(applicationContext).apply {
            ensureExamplesCopied(listOf("fft_degrade.lua", "hotkey_slide.lua", "space_warp.lua"))
        }

        setContent {
            ScutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel, scripts = scripts)
                }
            }
        }
    }
}
