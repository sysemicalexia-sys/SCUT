package com.scut.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun VelocityGraph() {
    var start by remember { mutableStateOf(Offset(0f, 1f)) }
    var end by remember { mutableStateOf(Offset(1f, 0f)) }
    var control by remember { mutableStateOf(Offset(0.25f, 0.15f)) }

    // Read here, in composable scope - the Canvas draw lambda below isn't
    // @Composable, so it can't call MaterialTheme.colorScheme itself; these
    // locals carry the actual Color values into it via closure instead.
    val backgroundColor = MaterialTheme.colorScheme.background
    val lineColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    control = Offset(
                        (control.x + drag.x / size.width).coerceIn(0f, 1f),
                        (control.y + drag.y / size.height).coerceIn(0f, 1f)
                    )
                    change.consume()
                }
            }
    ) {
        val s = Offset(start.x * size.width, start.y * size.height)
        val e = Offset(end.x * size.width, end.y * size.height)
        val c = Offset(control.x * size.width, control.y * size.height)

        val path = Path().apply {
            moveTo(s.x, s.y)
            quadraticBezierTo(c.x, c.y, e.x, e.y)
        }

        drawPath(path, lineColor, style = Stroke(width = 3f))
        drawCircle(lineColor, radius = 12f, center = c)
        drawCircle(backgroundColor, radius = 5f, center = c)
    }
}
