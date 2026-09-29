package com.scut.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scut.engine.ScriptManager
import java.io.File

@Composable
fun ToolsPanel(
    scripts: ScriptManager,
    onRunScript: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var files by remember { mutableStateOf(scripts.listScripts()) }

    Column(modifier = modifier.fillMaxSize().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Tools", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { files = scripts.listScripts() }) {
                Text("Refresh")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        LazyColumn {
            items(files) { file ->
                TextButton(
                    onClick = { onRunScript(file) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(file.name.removeSuffix(".lua"), modifier = Modifier.fillMaxWidth())
                }
            }

            if (files.isEmpty()) {
                item {
                    Text(
                        "No scripts yet",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
