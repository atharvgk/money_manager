package app.moneymanager.ui.spend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.moneymanager.AppContainer
import app.moneymanager.core.analysis.Summaries
import app.moneymanager.core.format.Inr
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.data.db.TransactionEntity
import app.moneymanager.ui.common.AccountChip
import app.moneymanager.ui.common.AccountChipsRow
import app.moneymanager.ui.common.AccountScope
import app.moneymanager.ui.common.AmountColumn
import app.moneymanager.ui.common.AppCard
import app.moneymanager.ui.common.BarChart
import app.moneymanager.ui.common.CardBook
import app.moneymanager.ui.common.CategoryBadge
import app.moneymanager.ui.common.ChangeIndicator
import app.moneymanager.ui.common.DeltaText
import app.moneymanager.ui.common.EmptyState
import app.moneymanager.ui.common.ItemRow
import app.moneymanager.ui.common.MerchantAmount
import app.moneymanager.ui.common.MonthSelector
import app.moneymanager.ui.common.PaceChart
import app.moneymanager.ui.common.paceComparisonColor
import app.moneymanager.ui.common.ProgressTrack
import app.moneymanager.ui.common.Period
import app.moneymanager.ui.common.ScopeTabs
import app.moneymanager.ui.common.ScreenHeader
import app.moneymanager.ui.common.SectionCard
import app.moneymanager.ui.common.ShareBar
import app.moneymanager.ui.common.Snapshot
import app.moneymanager.ui.common.SpendMath
import app.moneymanager.ui.common.accountChips
import app.moneymanager.ui.common.appViewModel
import app.moneymanager.ui.common.color
import app.moneymanager.ui.common.matchesAccount
import app.moneymanager.ui.common.monthEnd
import app.moneymanager.ui.common.monthShort
import app.moneymanager.ui.common.monthStart
import app.moneymanager.ui.common.percentOf
import app.moneymanager.ui.common.zone
import app.moneymanager.ui.theme.LocalMoneyColors
import app.moneymanager.ui.transactions.TransactionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

enum class Trend(val label: String) { PACE("Pace"), MONTHS("6 months"), DAILY("Daily") }

data class SpendState(
    val month: YearMonth,
    val scope: AccountScope,
    val account: String?,
    val earliest: YearMonth?,
    val hasCards: Boolean,
    val period: Period,
    val comparison: Period,
    val current: Snapshot,
    val previous: Snapshot,
    val daily: LongArray,
    val previousDaily: LongArray,
    val months: List<YearMonth>,
    val monthly: LongArray,
    val merchants: List<MerchantAmount>,
    val transactions: List<TransactionEntity>,
    val chips: List<AccountChip>,
    val categories: Map<String, CategoryEntity>,
)

private data class SpendInputs(val month: YearMonth, val scope: AccountScope, val account: String?)

/** Data for the spending detail page (all spending or one category) and the all-categories page. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpendViewModel(c: AppContainer, private val category: String?, startMonth: YearMonth, startScope: AccountScope) : ViewModel() {
    private val repo = c.repository
    val month = MutableStateFlow(startMonth)
    val scope = MutableStateFlow(startScope)
    val account = MutableStateFlow<String?>(null)

    val state = combine(
        month.flatMapLatest { m ->
            val window = SpendMath.sixMonths(m, YearMonth.now(zone))
            repo.observeRange(monthStart(minOf(window.first(), m.minusMonths(1))), monthEnd(maxOf(window.last(), m))).map { m to it }
        },
        combine(month, scope, account) { m, s, a -> SpendInputs(m, s, a) },
        repo.categories,
        repo.creditCardAccounts,
        repo.earliestTimestamp,
    ) { (m, txns), inp, cats, cards, earliest ->
        if (inp.month != m) return@combine null
        val book = CardBook(cards)
        val today = LocalDate.now(zone)
        val period = SpendMath.period(m, today, zone)
        val comparison = SpendMath.comparison(period, zone)
        val scoped = txns.filter { book.inScope(it, inp.scope) }
        val chips = accountChips(scoped.filter { period.contains(it.timestamp) }, book)
        val acct = inp.account?.takeIf { key -> chips.any { it.key == key } }
        val mine = scoped.filter { it.matchesAccount(acct) }
        val window = SpendMath.sixMonths(m, YearMonth.from(today))
        SpendState(
            month = m,
            scope = inp.scope,
            account = acct,
            earliest = earliest?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) },
            hasCards = book.hasCards,
            period = period,
            comparison = comparison,
            current = SpendMath.snapshot(mine, period, category),
            previous = SpendMath.snapshot(mine, comparison, category),
            daily = SpendMath.daily(mine, m, zone, category),
            previousDaily = SpendMath.daily(mine, m.minusMonths(1), zone, category),
            months = window,
            monthly = SpendMath.monthly(mine, window, zone, category),
            merchants = SpendMath.topMerchants(mine, period, category, limit = 6),
            transactions = mine.filter { period.contains(it.timestamp) && it.type == "DEBIT" && (category == null || it.categoryKey == category) },
            chips = chips,
            categories = cats.associateBy { it.key },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun rememberSpendState(vm: SpendViewModel): SpendState? {
    val latest by vm.state.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<SpendState?>(null) }
    latest?.let { shown = it }
    return shown
}

/** Common header rows: month stepper, Bank/Cards switch and account chips. */
private fun LazyListScope.filters(s: SpendState, vm: SpendViewModel) {
    item {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val now = YearMonth.now(zone)
            MonthSelector(
                month = s.month,
                canGoBack = s.earliest == null || s.month > s.earliest,
                canGoForward = s.month < now,
                onChange = { vm.month.value = it },
            )
        }
    }
    if (s.hasCards) item { ScopeTabs(s.scope, onChange = { vm.scope.value = it }) }
    if (s.chips.size >= 2 || s.account != null) item { AccountChipsRow(s.chips, s.account, onSelect = { vm.account.value = it }) }
}

// ============================================================================================== spending detail

@Composable
fun SpendScreen(
    category: String?,
    startMonth: YearMonth,
    startScope: AccountScope,
    onBack: () -> Unit,
    onOpenCategory: (String, YearMonth, AccountScope) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val vm = appViewModel(key = "spend:$category:$startMonth:$startScope") { SpendViewModel(it, category, startMonth, startScope) }
    val s = rememberSpendState(vm)
    var trend by rememberSaveable { mutableStateOf(Trend.PACE) }
    val cat = s?.categories?.get(category)
    val title = if (category == null) "Spending" else cat?.name ?: "Category"

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title, onBack = onBack) }
        if (s == null) return@LazyColumn
        filters(s, vm)
        item { TotalCard(s, cat, category != null) }
        item { TrendCard(s, trend, onTrend = { trend = it }) }
        if (category == null) {
            if (s.current.byCategory.isNotEmpty()) {
                item { CategoryListCard(s, title = "By category", onOpen = { onOpenCategory(it, s.month, s.scope) }) }
            }
        } else {
            if (s.merchants.isNotEmpty()) item { MerchantsCard(s.merchants, s.current.spent) }
            if (s.transactions.isNotEmpty()) {
                item {
                    SectionCard(title = "Transactions", subtitle = "${s.transactions.size} in ${s.period.label}") {
                        s.transactions.forEach { t ->
                            TransactionRow(
                                t, s.categories[t.categoryKey], onClick = { onEdit(t.id) },
                                padding = PaddingValues(vertical = 9.dp), showDate = true, showCategory = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalCard(s: SpendState, cat: CategoryEntity?, isCategory: Boolean) {
    AppCard(padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCategory) {
                CategoryBadge(cat, 44.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    (if (s.period.partial) "Spent so far · " else "Spent in ") + s.period.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Inr.formatRounded(s.current.spent), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (s.previous.spent > 0) {
            val ch = Summaries.change(s.current.spent, s.previous.spent)
            ChangeIndicator(ch.deltaPaise, ch.percent, s.comparison.shortLabel)
        } else {
            Text("Nothing spent in ${s.comparison.shortLabel} to compare with", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val payments = s.current.byCategory.sumOf { it.count }
        Spacer(Modifier.height(12.dp))
        Text(
            "$payments payment${if (payments == 1) "" else "s"} · about ${Inr.formatRounded(s.current.spent / s.period.lastDay.coerceAtLeast(1))} a day",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendCard(s: SpendState, trend: Trend, onTrend: (Trend) -> Unit) {
    val colors = LocalMoneyColors.current
    SectionCard(title = "Trend") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Trend.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = trend == t,
                    onClick = { onTrend(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, Trend.entries.size),
                    icon = {},
                ) { Text(t.label, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(16.dp))
        when (trend) {
            Trend.PACE -> {
                val days = s.period.lastDay
                val current = SpendMath.cumulative(s.daily, days)
                val previous = SpendMath.cumulative(s.previousDaily)
                PaceChart(current, previous, s.month.lengthOfMonth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendLine(colors.chartSeries, monthShort(s.month) + if (s.period.partial) " so far" else "")
                    LegendLine(paceComparisonColor(), monthShort(s.month.minusMonths(1)))
                }
                Spacer(Modifier.height(8.dp))
                val prevSameDay = previous.getOrNull(minOf(days, previous.size) - 1) ?: 0L
                val now = current.lastOrNull() ?: 0L
                Caption(
                    when {
                        prevSameDay == 0L && now == 0L -> "No spending in either month yet."
                        prevSameDay == 0L -> "Nothing was spent by day $days last month."
                        now > prevSameDay -> "${Inr.formatRounded(now - prevSameDay)} ahead of last month's pace by day $days."
                        now < prevSameDay -> "${Inr.formatRounded(prevSameDay - now)} behind last month's pace by day $days."
                        else -> "Exactly on last month's pace."
                    } + if (s.period.partial && previous.isNotEmpty()) " Last month ended at ${Inr.formatRounded(previous.last())}." else "",
                )
            }
            Trend.MONTHS -> {
                var sel by remember(s.months) { mutableStateOf(s.months.indexOf(s.month).takeIf { it >= 0 } ?: s.months.lastIndex) }
                val thisMonth = YearMonth.now(zone)
                // The month in progress would drag the average down, so it only counts finished months.
                val avg = SpendMath.averageOfNonZero(s.monthly.filterIndexed { i, _ -> s.months[i] != thisMonth }.toLongArray())
                BarChart(s.monthly, s.months.map(::monthShort), sel, onSelect = { sel = it }, average = avg)
                Spacer(Modifier.height(8.dp))
                val m = s.months[sel]
                Caption(
                    "${monthShort(m)} ${m.year}: ${Inr.formatRounded(s.monthly[sel])}" + (if (m == thisMonth) " so far" else "") +
                        (if (avg > 0) "\nDashed line: average of past months, ${Inr.formatRounded(avg)}" else ""),
                )
            }
            Trend.DAILY -> {
                val shownDays = if (s.period.partial) s.period.lastDay else s.daily.size
                val values = s.daily.copyOf(shownDays)
                val peak = values.indices.maxByOrNull { values[it] } ?: 0
                var sel by remember(s.month) { mutableStateOf<Int?>(if (values.any { it > 0 }) peak else null) }
                val avg = values.sum() / shownDays.coerceAtLeast(1)
                BarChart(values, (1..shownDays).map { it.toString() }, sel, onSelect = { sel = it }, average = avg, labelEvery = 5)
                Spacer(Modifier.height(8.dp))
                val day = sel
                Caption(
                    if (day != null) {
                        "${day + 1} ${monthShort(s.month)}: ${Inr.formatRounded(values[day])} · highest ${Inr.formatRounded(values[peak])} on ${peak + 1} ${monthShort(s.month)}" +
                            "\nDashed line: daily average, ${Inr.formatRounded(avg)}"
                    } else "Nothing spent yet this month.",
                )
            }
        }
    }
}

@Composable
private fun LegendLine(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(16.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MerchantsCard(merchants: List<MerchantAmount>, total: Long) {
    SectionCard(title = "Top merchants") {
        val max = merchants.maxOf { it.amount }.coerceAtLeast(1)
        merchants.forEach { m ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(Inr.formatRounded(m.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ProgressTrack(m.amount.toFloat() / max, LocalMoneyColors.current.chartSeries, height = 4.dp) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${m.count}× · ${percentOf(m.amount, total)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Every category with its share, amount and change vs the comparison period. */
@Composable
private fun CategoryListCard(s: SpendState, title: String, onOpen: (String) -> Unit) {
    val total = s.current.spent.coerceAtLeast(1)
    val previous = s.previous.byCategory.associate { it.key to it.amount }
    SectionCard(title = title, subtitle = "Change vs ${s.comparison.shortLabel}") {
        ShareBar(s.current.byCategory.map { s.categories[it.key].color() to it.amount })
        Spacer(Modifier.height(8.dp))
        s.current.byCategory.forEach { c ->
            val cat = s.categories[c.key]
            val delta = c.amount - (previous[c.key] ?: 0)
            ItemRow(
                title = cat?.name ?: c.key,
                subtitle = "${percentOf(c.amount, total)} · ${c.count} payment${if (c.count == 1) "" else "s"}",
                leading = { CategoryBadge(cat) },
                trailing = {
                    AmountColumn(Inr.formatRounded(c.amount)) {
                        if (previous[c.key] == null) {
                            Text("new", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (abs(delta) >= 100) {
                            DeltaText(delta)
                        }
                    }
                },
                onClick = { onOpen(c.key) },
            )
        }
        // Categories you spent on last time but not (yet) this time.
        val gone = s.previous.byCategory.filter { p -> s.current.byCategory.none { it.key == p.key } }
        if (gone.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Nothing yet in: " + gone.joinToString { "${s.categories[it.key]?.name ?: it.key} (${Inr.formatRounded(it.amount)} in ${s.comparison.shortLabel})" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================================================================================== all categories

@Composable
fun CategoriesScreen(
    startMonth: YearMonth,
    startScope: AccountScope,
    onBack: () -> Unit,
    onOpenCategory: (String, YearMonth, AccountScope) -> Unit,
) {
    val vm = appViewModel(key = "categories:$startMonth:$startScope") { SpendViewModel(it, null, startMonth, startScope) }
    val s = rememberSpendState(vm)
    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Categories", onBack = onBack) }
        if (s == null) return@LazyColumn
        filters(s, vm)
        if (s.current.byCategory.isEmpty()) {
            item { EmptyState("🗂️", "No spending", "Nothing was spent in ${s.period.label} with these filters.") }
            return@LazyColumn
        }
        item {
            AppCard(padding = PaddingValues(20.dp)) {
                Text(
                    (if (s.period.partial) "Spent so far · " else "Spent in ") + s.period.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Inr.formatRounded(s.current.spent), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "across ${s.current.byCategory.size} categor${if (s.current.byCategory.size == 1) "y" else "ies"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { CategoryListCard(s, title = "All categories", onOpen = { onOpenCategory(it, s.month, s.scope) }) }
    }
}
