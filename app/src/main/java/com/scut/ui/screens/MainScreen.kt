package com.scut.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scut.engine.NativeEngine
import com.scut.engine.ScriptManager

@Composable
fun MainScreen(engine: NativeEngine, scripts: ScriptManager) {
    var toolsOpen by remember { mutableStateOf(false) }
    var projectsOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Engine ready \u00b7 ${engine.nodeCount()} nodes") }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SCUT") },
                    navigationIcon = {
                        IconButton(onClick = { toolsOpen = true }) { Text("\u2630") }
                    },
                    actions = {
                        IconButton(onClick = { projectsOpen = true }) { Text("Projects") }
                    }
                )
            }
        ) { padding ->
            HomeContent(
                modifier = Modifier.padding(padding),
                status = status,
                scriptsPath = scripts.directoryPath()
            )
        }

        if (toolsOpen || projectsOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        toolsOpen = false
                        projectsOpen = false
                    }
            )
        }

        AnimatedVisibility(
            visible = toolsOpen,
            enter = slideInHorizontally(tween(220)) { -it },
            exit = slideOutHorizontally(tween(220)) { -it },
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight()
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 300.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                ToolsPanel(
                    scripts = scripts,
                    onRunScript = { source, name ->
                        val ok = engine.runScript(source)
                        status = "$name: ${if (ok) "ok" else "failed"} \u00b7 ${engine.nodeCount()} nodes"
                        toolsOpen = false
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = projectsOpen,
            enter = slideInHorizontally(tween(220)) { it },
            exit = slideOutHorizontally(tween(220)) { it },
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 300.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                ProjectsPanel()
            }
        }
    }
}
