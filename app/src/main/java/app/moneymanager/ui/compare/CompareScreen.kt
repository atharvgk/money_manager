package app.moneymanager.ui.compare

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.moneymanager.AppContainer
import app.moneymanager.core.analysis.Summaries
import app.moneymanager.core.format.Inr
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.ui.common.CategoryBadge
import app.moneymanager.ui.common.ColorDot
import app.moneymanager.ui.common.EmptyState
import app.moneymanager.ui.common.SectionCard
import app.moneymanager.ui.common.StackedMonthsChart
import app.moneymanager.ui.common.appViewModel
import app.moneymanager.ui.common.color
import app.moneymanager.ui.common.monthEnd
import app.moneymanager.ui.common.monthShort
import app.moneymanager.ui.common.monthStart
import app.moneymanager.ui.common.toRecord
import app.moneymanager.ui.common.zone
import app.moneymanager.ui.theme.LocalMoneyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class CompareRow(val key: String, val values: LongArray, val total: Long)

data class CompareState(
    val span: Int = 6,
    val months: List<YearMonth> = emptyList(),
    val rows: List<CompareRow> = emptyList(),
    val totals: LongArray = LongArray(0),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val span = MutableStateFlow(6)

    val state = combine(
        span.flatMapLatest { n ->
            val now = YearMonth.now(zone)
            val months = (n - 1 downTo 0).map { now.minusMonths(it.toLong()) }
            repo.observeRange(monthStart(months.first()), monthEnd(months.last())).map { Triple(n, months, it) }
        },
        repo.categories,
    ) { (n, months, txns), cats ->
        val byCat = Summaries.categoryByMonth(txns.map { it.toRecord() }, months, zone)
        val rows = byCat.map { (k, v) -> CompareRow(k, v, v.sum()) }.filter { it.total > 0 }.sortedByDescending { it.total }
        val totals = LongArray(months.size) { i -> rows.sumOf { it.values[i] } }
        CompareState(n, months, rows, totals, cats.associateBy { it.key }, loading = false)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen() {
    val vm = appViewModel { CompareViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Compare", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 4.dp))
                SingleChoiceSegmentedButtonRow {
                    listOf(6, 12).forEachIndexed { i, n ->
                        SegmentedButton(
                            selected = s.span == n,
                            onClick = { vm.span.value = n },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text("${n}M") }
                    }
                }
            }
        }
        if (s.loading) return@LazyColumn
        if (s.rows.isEmpty()) {
            item { EmptyState("📊", "Nothing to compare yet", "Once a few months of transactions are recorded, they'll line up here.") }
            return@LazyColumn
        }
        item { ChartCard(s) }
        item { TableCard(s) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(s: CompareState) {
    // At most 6 named categories + one neutral "Other" so colours stay distinguishable.
    val top = s.rows.take(6)
    val rest = s.rows.drop(6)
    val fold = MaterialTheme.colorScheme.outlineVariant
    val colors = top.map { s.categories[it.key].color() } + if (rest.isNotEmpty()) listOf(fold) else emptyList()
    val series = top.map { r -> r.values.map { it / 100.0 } } +
        if (rest.isNotEmpty()) listOf(s.months.indices.map { i -> rest.sumOf { it.values[i] } / 100.0 }) else emptyList()
    SectionCard(title = "Spending by month") {
        StackedMonthsChart(labels = s.months.map(::monthShort), series = series, colors = colors)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            top.forEachIndexed { i, r ->
                val c = s.categories[r.key]
                LegendItem(colors[i], c, c?.name ?: r.key)
            }
            if (rest.isNotEmpty()) LegendItem(fold, null, "Other (${rest.size})")
        }
    }
}

/** The category's own icon doubles as the colour key; the folded "Other" gets a plain dot. */
@Composable
private fun LegendItem(color: Color, category: CategoryEntity?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (category != null) CategoryBadge(category, 22.dp) else ColorDot(color, 10.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private val NAME_COL = 144.dp
private val CELL = 76.dp

@Composable
private fun TableCard(s: CompareState) {
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    // The month in progress would always look like "▼ less" than a whole month, so it isn't marked.
    val now = YearMonth.now(zone)
    fun previous(values: LongArray, i: Int): Long? = if (i > 0 && s.months[i] != now) values[i - 1] else null
    SectionCard(title = "Category × month") {
        Text(
            "▲ more than the month before (by 10%+), ▼ less. ${monthShort(now)} isn't marked until it's over. Scroll sideways for older months.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TableRow(scroll, name = { Text("Category", style = MaterialTheme.typography.labelMedium) }) {
            s.months.forEach { m ->
                Text(monthShort(m) + if (m.monthValue == 1) " '${m.year % 100}" else "", style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End, modifier = Modifier.width(CELL).padding(end = 6.dp))
            }
        }
        HorizontalDivider()
        s.rows.forEach { r ->
            val c = s.categories[r.key]
            TableRow(scroll, name = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(c, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(c?.name ?: r.key, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }) {
                r.values.forEachIndexed { i, v -> Cell(v, previous(r.values, i)) }
            }
        }
        HorizontalDivider()
        TableRow(scroll, name = { Text("Total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }) {
            s.totals.forEachIndexed { i, v -> Cell(v, previous(s.totals, i), bold = true) }
        }
    }
}

@Composable
private fun TableRow(scroll: ScrollState, name: @Composable () -> Unit, cells: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(NAME_COL).padding(end = 6.dp)) { name() }
        Row(Modifier.weight(1f).horizontalScroll(scroll)) { cells() }
    }
}

@Composable
private fun Cell(value: Long, previous: Long?, bold: Boolean = false) {
    val colors = LocalMoneyColors.current
    val up = previous != null && previous > 0 && value > previous * 11 / 10
    val down = previous != null && previous > 0 && value < previous * 9 / 10
    val newSpend = previous == 0L && value > 0
    val bg = when {
        up -> colors.worseContainer
        down -> colors.betterContainer
        else -> Color.Transparent
    }
    val mark = when {
        up -> "▲ "
        down -> "▼ "
        newSpend -> "• "
        else -> ""
    }
    Box(Modifier.width(CELL).padding(horizontal = 2.dp).clip(RoundedCornerShape(6.dp)).background(bg).padding(vertical = 4.dp, horizontal = 4.dp), contentAlignment = Alignment.CenterEnd) {
        Text(
            if (value == 0L) "–" else mark + Inr.compact(value),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = when {
                up -> colors.worse
                down -> colors.better
                value == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
