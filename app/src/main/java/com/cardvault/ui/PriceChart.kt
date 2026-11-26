package com.cardvault.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.cardvault.data.PricePoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Line chart of a card's value over time, with the low, high and date range labeled. */
@Composable
fun PriceChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val color = if (points.last().value >= points.first().value) Up else Down
    val minV = points.minOf { it.value }
    val maxV = points.maxOf { it.value }
    val firstDay = points.first().day
    val lastDay = points.last().day
    val fmt = DateTimeFormatter.ofPattern("MMM d")

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val leftPad = 8f
        val rightPad = 8f
        val topPad = 36f
        val bottomPad = 40f
        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad
        val span = (maxV - minV).takeIf { it > 0 } ?: (maxV * 0.1).coerceAtLeast(1.0)
        val daySpan = (lastDay - firstDay).coerceAtLeast(1)

        fun x(day: Long) = leftPad + w * ((day - firstDay).toFloat() / daySpan)
        fun y(v: Double) = topPad + h * (1f - ((v - minV) / span).toFloat())

        // grid lines
        for (i in 0..2) {
            val gy = topPad + h * i / 2f
            drawLine(Muted.copy(alpha = 0.2f), Offset(leftPad, gy), Offset(leftPad + w, gy), strokeWidth = 1f)
        }

        val line = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) line.moveTo(x(p.day), y(p.value)) else line.lineTo(x(p.day), y(p.value))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(lastDay), topPad + h)
            lineTo(x(firstDay), topPad + h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)), startY = topPad, endY = topPad + h))
        drawPath(line, color, style = Stroke(width = 5f))
        drawCircle(color, radius = 8f, center = Offset(x(lastDay), y(points.last().value)))

        // labels
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 30f
            this.color = Muted.toArgb()
        }
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawText("High ${formatMoney(maxV)}", leftPad, 26f, paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas.drawText("Low ${formatMoney(minV)}", leftPad + w, 26f, paint)
        paint.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText(LocalDate.ofEpochDay(firstDay).format(fmt), leftPad, size.height - 6f, paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas.drawText(LocalDate.ofEpochDay(lastDay).format(fmt), leftPad + w, size.height - 6f, paint)
    }
}
