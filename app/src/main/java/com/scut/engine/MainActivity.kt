package com.scut.engine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scut.ui.theme.ScutTheme

class MainActivity : ComponentActivity() {

    private val engine = NativeEngine()
    private lateinit var scripts: ScriptManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engine.create()
        scripts = ScriptManager(this).apply {
            ensureExamplesCopied(listOf("fft_degrade.lua", "hotkey_slide.lua", "space_warp.lua"))
        }

        setContent {
            ScutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(engine = engine, scripts = scripts)
                }
            }
        }
    }

    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }
}

@Composable
private fun HomeScreen(engine: NativeEngine, scripts: ScriptManager) {
    var status by remember { mutableStateOf("Ready - scripts folder:\n${scripts.directoryPath()}") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("SCUT", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(status)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            val scriptFiles = scripts.listScripts()
            status = if (scriptFiles.isEmpty()) {
                "No .lua files found in scripts/ yet"
            } else {
                scriptFiles.joinToString("\n") { file ->
                    val ok = engine.runScript(file.readText())
                    "${file.name}: ${if (ok) "ok" else "failed - see Logcat tag SCUT"}"
                } + "\n\nnodes in graph: ${engine.nodeCount()}"
            }
        }) {
            Text("Run scripts from scripts/")
        }
    }
}
