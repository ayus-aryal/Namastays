package com.example.namastays.trek.presentataion.map.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.namastays.trek.util.ElevationPoint
import com.example.namastays.trek.util.GpxSaxHandler

@Composable
fun ElevationProfileChart(
    points: List<ElevationPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val minEle = points.minOf { it.elevationM }.toFloat()
    val maxEle = points.maxOf { it.elevationM }.toFloat()
    val maxDist = points.maxOf { it.distanceKm }
    val eleRange = (maxEle - minEle).coerceAtLeast(1f)

    val coralColor = Color(0xFFE8622A)
    val whiteColor = Color.White
    val darkColor = Color(0xFF1A1A1A)

    Column(modifier = modifier) {
        // Y axis labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${maxEle.toInt()} m",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val w = size.width
            val h = size.height
            val padding = 4f

            // Build path points
            val chartPoints = points.map { point ->
                val x = (point.distanceKm / maxDist) * (w - padding * 2) + padding
                val y = h - ((point.elevationM.toFloat() - minEle) / eleRange) * (h - padding * 2) - padding
                Offset(x, y)
            }

            if (chartPoints.size < 2) return@Canvas

            // Fill path
            val fillPath = Path().apply {
                moveTo(chartPoints.first().x, h)
                chartPoints.forEach { lineTo(it.x, it.y) }
                lineTo(chartPoints.last().x, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        coralColor.copy(alpha = 0.9f),
                        coralColor.copy(alpha = 0.4f)
                    )
                )
            )

            // White line on top
            val linePath = Path().apply {
                moveTo(chartPoints.first().x, chartPoints.first().y)
                chartPoints.drop(1).forEach { lineTo(it.x, it.y) }
            }

            drawPath(
                path = linePath,
                color = whiteColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // X axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0 km",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
            val midDist = "%.1f".format(maxDist / 2)
            Text(
                text = "$midDist km",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
            Text(
                text = "${"%.1f".format(maxDist)} km",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }

        // Bottom label
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${minEle.toInt()} m",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}