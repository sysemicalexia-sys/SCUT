package com.scut.engine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.scut.ui.screens.MainScreen
import com.scut.ui.theme.ScutTheme

class MainActivity : ComponentActivity() {

    private val engine = NativeEngine()
    private lateinit var scripts: ScriptManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        engine.create()
        scripts = ScriptManager(this).apply {
            ensureExamplesCopied(listOf("fft_degrade.lua", "hotkey_slide.lua", "space_warp.lua"))
        }

        setContent {
            ScutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(engine = engine, scripts = scripts)
                }
            }
        }
    }

    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }
}
