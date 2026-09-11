package com.scut.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

data class WarpPoint(
    val base: Offset,
    val offset: Offset
)

@Composable
fun WarpGridOverlay(
    cols: Int = 8,
    rows: Int = 8,
    onWarpChanged: (List<Offset>) -> Unit = {}
) {
    var points by remember(cols, rows) {
        mutableStateOf(
            List(cols * rows) { idx ->
                val x = if (cols <= 1) 0.5f else (idx % cols).toFloat() / (cols - 1)
                val y = if (rows <= 1) 0.5f else (idx / cols).toFloat() / (rows - 1)
                WarpPoint(Offset(x, y), Offset.Zero)
            }
        )
    }

    var active by remember { mutableStateOf(-1) }
    val currentPoints by rememberUpdatedState(points)

    // Read here, in composable scope, same reason as VelocityGraph.kt -
    // the Canvas draw lambda below can't call MaterialTheme.colorScheme.
    val backgroundColor = MaterialTheme.colorScheme.background
    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        active = nearest(currentPoints, pos, size.width, size.height)
                    },
                    onDragEnd = {
                        active = -1
                    },
                    onDragCancel = {
                        active = -1
                    },
                    onDrag = { change, drag ->
                        if (active >= 0) {
                            val delta = Offset(drag.x / size.width, drag.y / size.height)

                            val updated = currentPoints.mapIndexed { i, p ->
                                if (i == active) {
                                    p.copy(offset = p.offset + delta)
                                } else {
                                    p
                                }
                            }

                            points = updated
                            onWarpChanged(updated.map { it.offset })
                            change.consume()
                        }
                    }
                )
            }
    ) {
        val grid = points.map {
            Offset(
                (it.base.x + it.offset.x) * size.width,
                (it.base.y + it.offset.y) * size.height
            )
        }

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val i = y * cols + x

                if (x + 1 < cols) {
                    drawLine(lineColor, grid[i], grid[i + 1], strokeWidth = 2f)
                }

                if (y + 1 < rows) {
                    drawLine(lineColor, grid[i], grid[i + cols], strokeWidth = 2f)
                }
            }
        }

        grid.forEach {
            drawCircle(pointColor, radius = 8f, center = it)
        }
    }
}

private fun nearest(points: List<WarpPoint>, pos: Offset, w: Float, h: Float): Int {
    var best = -1
    var bestDist = Float.MAX_VALUE

    points.forEachIndexed { i, p ->
        val px = (p.base.x + p.offset.x) * w
        val py = (p.base.y + p.offset.y) * h
        val dx = px - pos.x
        val dy = py - pos.y
        val d = dx * dx + dy * dy

        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }

    return best
}
