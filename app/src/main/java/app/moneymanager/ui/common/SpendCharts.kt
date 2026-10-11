package app.moneymanager.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.moneymanager.core.format.Inr
import app.moneymanager.ui.theme.LocalMoneyColors
import kotlin.math.abs

/** Part-to-whole as one slim bar, segments separated by a small gap. Pair it with a list: colour isn't the only cue. */
@Composable
fun ShareBar(parts: List<Pair<Color, Long>>, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val total = parts.sumOf { it.second }
    if (total <= 0) return
    Row(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.filter { it.second > 0 }.forEach { (color, value) ->
            Box(Modifier.weight(value.toFloat() / total).fillMaxHeight().background(color))
        }
    }
}

/** Last month's line in the pace chart (and its legend): clearly secondary, still readable. */
@Composable
fun paceComparisonColor(): Color = MaterialTheme.colorScheme.outline

/** Thin progress track (budget). */
@Composable
fun ProgressTrack(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/**
 * Running total through the month: this month (solid, up to today) against last month (muted, whole month).
 * Shows at a glance whether spending is ahead of or behind last month's pace.
 */
@Composable
fun PaceChart(
    current: LongArray,
    previous: LongArray,
    daysInMonth: Int,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    val colors = LocalMoneyColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = colors.card
    val previousColor = paceComparisonColor()
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = muted)
    val valueStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.chartSeries)
    val description = "So far ${Inr.formatRounded(current.lastOrNull() ?: 0)}; last month ${Inr.formatRounded(previous.getOrNull(current.size - 1) ?: 0)} by the same day"

    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val labelsH = 22.dp.toPx()
        val top = 22.dp.toPx()
        val baseY = size.height - labelsH
        val plotH = baseY - top
        val maxV = maxOf(current.maxOrNull() ?: 0, previous.maxOrNull() ?: 0, 1L).toFloat()
        val days = maxOf(daysInMonth, previous.size, 2)
        fun x(i: Int) = size.width * i / (days - 1)
        fun y(v: Long) = baseY - plotH * v / maxV

        gridlines(colors.gridline, top, baseY, 3)
        drawLine(colors.gridline, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx())

        fun path(values: LongArray) = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        if (previous.size > 1) {
            drawPath(path(previous), previousColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (current.isNotEmpty()) {
            if (current.size > 1) {
                // Soft fill under this month's line.
                val fill = path(current).apply {
                    lineTo(x(current.lastIndex), baseY)
                    lineTo(x(0), baseY)
                    close()
                }
                drawPath(fill, colors.chartSeries.copy(alpha = 0.10f))
                drawPath(path(current), colors.chartSeries, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val end = Offset(x(current.lastIndex), y(current.last()))
            drawCircle(surface, 6.dp.toPx(), end)
            drawCircle(colors.chartSeries, 4.dp.toPx(), end)
            val text = measurer.measure(Inr.compact(current.last()), valueStyle)
            val tx = (end.x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(tx, (end.y - text.size.height - 8.dp.toPx()).coerceAtLeast(0f)))
        }

        // Day labels: 1, middle, last
        listOf(1, (days + 1) / 2, days).forEach { d ->
            val t = measurer.measure("$d", labelStyle)
            val cx = (x(d - 1) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(cx, baseY + 6.dp.toPx()))
        }
    }
}

/**
 * Plain bar chart. Tap a bar to select it; the selected bar is drawn in the accent colour with its value above.
 * An optional dashed [average] line is labelled at the right edge.
 */
@Composable
fun BarChart(
    values: LongArray,
    labels: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    average: Long? = null,
    labelEvery: Int = 1,
    height: Dp = 190.dp,
) {
    if (values.isEmpty()) return
    val colors = LocalMoneyColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = muted)
    val selectedLabelStyle = labelStyle.copy(color = ink, fontWeight = FontWeight.SemiBold)
    val valueStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink)
    val description = labels.zip(values.toList()).joinToString { (l, v) -> "$l: ${Inr.formatRounded(v)}" }

    Canvas(
        modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                detectTapGestures { o -> onSelect((o.x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.lastIndex)) }
            },
    ) {
        val n = values.size
        val top = 26.dp.toPx()
        val labelsH = 22.dp.toPx()
        val baseY = size.height - labelsH
        val plotH = baseY - top
        val slot = size.width / n
        val barW = if (n <= 12) minOf(slot * 0.56f, 32.dp.toPx()) else (slot * 0.64f).coerceIn(2.dp.toPx(), 9.dp.toPx())
        val maxV = maxOf(values.max(), average ?: 0L, 1L).toFloat()
        fun cx(i: Int) = slot * i + slot / 2

        gridlines(colors.gridline, top, baseY, 3)
        drawLine(colors.gridline, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx())

        val r = CornerRadius(minOf(barW / 2, 6.dp.toPx()))
        for (i in 0 until n) {
            val v = values[i]
            if (v <= 0) continue
            val h = maxOf(plotH * v / maxV, 3.dp.toPx())
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        cx(i) - barW / 2, baseY - h, cx(i) + barW / 2, baseY, r, r, CornerRadius.Zero, CornerRadius.Zero,
                    ),
                )
            }
            drawPath(path, if (i == selected) colors.chartSeries else colors.chartMuted)
        }

        if (average != null && average > 0) {
            val y = baseY - plotH * average / maxV
            drawLine(muted.copy(alpha = 0.8f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }

        if (selected != null && selected in values.indices) {
            val v = values[selected]
            val h = if (v > 0) maxOf(plotH * v / maxV, 3.dp.toPx()) else 0f
            val t = measurer.measure(Inr.formatRounded(v), valueStyle)
            val tx = (cx(selected) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(tx, (baseY - h - t.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
        }

        for (i in 0 until n) {
            val show = i == selected || i % labelEvery == 0 || i == n - 1
            if (!show) continue
            // Keep neighbours of the selected label from overlapping it on dense charts.
            if (i != selected && selected != null && labelEvery > 1 && abs(i - selected) < labelEvery) continue
            val t = measurer.measure(labels[i], if (i == selected) selectedLabelStyle else labelStyle)
            val x = (cx(i) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(x, baseY + 6.dp.toPx()))
        }
    }
}

private fun DrawScope.gridlines(color: Color, top: Float, baseY: Float, count: Int) {
    for (k in 1..count) {
        val y = baseY - (baseY - top) * k / count
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
    }
}
