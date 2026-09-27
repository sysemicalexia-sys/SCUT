package com.scut.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scut.ui.components.WarpGridOverlay

@Composable
fun HomeContent(modifier: Modifier = Modifier, status: String, scriptsPath: String) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            WarpGridOverlay(onWarpChanged = {})
        }

        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Scripts: $scriptsPath",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
