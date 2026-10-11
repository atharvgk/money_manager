package app.moneymanager.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.moneymanager.core.format.Inr
import app.moneymanager.ui.theme.LocalMoneyColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModel
import com.patrykandpatrick.vico.compose.pie.rememberPieChart

private val rupeeAxis = CartesianValueFormatter { _, value, _ -> Inr.compact((value * 100).toLong()) }

private val MonthLabelsKey = ExtraStore.Key<List<String>>()

/** Month names stored with the chart data. Vico crashes on blank labels, so there's always a fallback. */
private val monthAxis = CartesianValueFormatter { context, x, _ ->
    context.model.extraStore.getOrNull(MonthLabelsKey)?.getOrNull(x.toInt())?.takeIf { it.isNotBlank() } ?: "·"
}

/** Tap-and-hold tooltip that shows the (stacked) total in rupees. */
@Composable
private fun rememberRupeeMarker(): DefaultCartesianMarker {
    val label = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp),
        padding = Insets(8.dp, 4.dp, 8.dp, 4.dp),
        background = rememberShapeComponent(Fill(MaterialTheme.colorScheme.inverseSurface), RoundedCornerShape(6.dp)),
    )
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = remember {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                val total = targets.filterIsInstance<ColumnCartesianLayerMarkerTarget>()
                    .sumOf { t -> t.columns.sumOf { it.entry.y } }
                Inr.formatRounded((total * 100).toLong())
            }
        },
    )
}

@Composable
private fun axisLabel() = rememberTextComponent(
    style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp),
    padding = Insets(2.dp, 2.dp, 2.dp, 2.dp),
)

/** Daily spend for one month: one series, so one colour and no legend. */
@Composable
fun DailySpendChart(dailyPaise: LongArray, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    val key = dailyPaise.contentHashCode()
    LaunchedEffect(key) {
        producer.runTransaction { columnModel { series(dailyPaise.map { it / 100.0 }) } }
    }
    val colors = LocalMoneyColors.current
    val column = rememberLineComponent(
        fill = Fill(colors.chartSeries),
        thickness = 6.dp,
        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
    )
    val grid = rememberLineComponent(fill = Fill(colors.gridline), thickness = 1.dp)
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(columnProvider = ColumnCartesianLayer.ColumnProvider.series(column)),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel(),
                valueFormatter = rupeeAxis,
                line = null,
                tick = null,
                guideline = grid,
                itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 4 }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel(),
                valueFormatter = remember { CartesianValueFormatter { _, x, _ -> (x.toInt() + 1).toString() } },
                guideline = null,
                tick = null,
                line = grid,
                itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 5 }) },
            ),
            marker = rememberRupeeMarker(),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth(),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = remember { Zoom.Content }),
        chartAreaHeight = 170.dp,
    )
}

/**
 * Months on the x axis, one stacked segment per category. [series] and [colors] must line up;
 * callers fold the tail into "Other" so there are never more than 7 coloured series.
 */
@Composable
fun StackedMonthsChart(labels: List<String>, series: List<List<Double>>, colors: List<Color>, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    val key = series.hashCode() + labels.hashCode()
    LaunchedEffect(key) {
        if (series.isNotEmpty()) {
            producer.runTransaction {
                columnModel { series.forEach { series(it) } }
                // Labels travel with the data, so the axis never pairs 6 labels with the old 12-month model
                // while switching ranges (that mismatch used to produce a blank label and crash).
                extras { it[MonthLabelsKey] = labels }
            }
        }
    }
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val columns = remember(colors, surface) {
        // A thin surface-coloured stroke gives the 2px gap between stacked segments.
        colors.map { LineComponent(Fill(it), 18.dp, RoundedCornerShape(2.dp), strokeFill = Fill(surface), strokeThickness = 1.dp) }
    }
    val grid = rememberLineComponent(fill = Fill(LocalMoneyColors.current.gridline), thickness = 1.dp)
    if (series.isEmpty()) return
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(columns),
                mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel(),
                valueFormatter = rupeeAxis,
                line = null,
                tick = null,
                guideline = grid,
                itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 4 }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel(),
                valueFormatter = monthAxis,
                guideline = null,
                tick = null,
                line = grid,
            ),
            marker = rememberRupeeMarker(),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth(),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = remember { Zoom.Content }),
        chartAreaHeight = 220.dp,
    )
}

/** Part-to-whole donut. Callers keep it to ≤ 6 segments (top 5 + "Other"). */
@Composable
fun CategoryDonut(values: List<Double>, colors: List<Color>, modifier: Modifier = Modifier) {
    if (values.isEmpty() || values.sum() <= 0.0) return
    val slices = remember(colors) { PieChart.SliceProvider.series(colors.map { PieChart.Slice(fill = Fill(it)) }) }
    val model = remember(values) { PieChartModel.build(*values.toTypedArray()) }
    PieChartHost(
        chart = rememberPieChart(
            sliceProvider = slices,
            spacing = 2.dp,
            innerSize = PieSize.Inner.fixed(104.dp),
        ),
        model = model,
        modifier = modifier.size(150.dp),
        chartAreaHeight = 150.dp,
    )
}
