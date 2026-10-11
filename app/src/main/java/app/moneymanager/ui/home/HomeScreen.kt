package app.moneymanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.moneymanager.AppContainer
import app.moneymanager.core.analysis.MonthSummary
import app.moneymanager.core.analysis.Summaries
import app.moneymanager.core.format.Inr
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.ui.common.CategoryDonut
import app.moneymanager.ui.common.ChangeIndicator
import app.moneymanager.ui.common.ColorDot
import app.moneymanager.ui.common.DailySpendChart
import app.moneymanager.ui.common.EmptyState
import app.moneymanager.ui.common.MonthSelector
import app.moneymanager.ui.common.SectionCard
import app.moneymanager.ui.common.appViewModel
import app.moneymanager.ui.common.color
import app.moneymanager.ui.common.monthEnd
import app.moneymanager.ui.common.monthLabel
import app.moneymanager.ui.common.monthShort
import app.moneymanager.ui.common.monthStart
import app.moneymanager.ui.common.toRecord
import app.moneymanager.ui.common.zone
import app.moneymanager.ui.theme.LocalMoneyColors
import app.moneymanager.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.YearMonth

data class HomeState(
    val month: YearMonth,
    val current: MonthSummary? = null,
    val previous: MonthSummary? = null,
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val earliest: YearMonth? = null,
    val totalTransactions: Int = 0,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val month = MutableStateFlow(YearMonth.now(zone))

    val state = combine(
        month.flatMapLatest { m -> repo.observeRange(monthStart(m.minusMonths(1)), monthEnd(m)).map { m to it } },
        repo.categories,
        repo.earliestTimestamp,
        repo.transactionCount,
    ) { (m, txns), cats, earliest, count ->
        val records = txns.map { it.toRecord() }
        HomeState(
            month = m,
            current = Summaries.month(records, m, zone),
            previous = Summaries.month(records, m.minusMonths(1), zone),
            categories = cats.associateBy { it.key },
            earliest = earliest?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) },
            totalTransactions = count,
            loading = false,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(YearMonth.now(zone)))
}

@Composable
fun HomeScreen(onOpenCategory: (String?, String) -> Unit, onOpenSettings: () -> Unit, onAdd: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasSms by remember { mutableStateOf(Permissions.hasSms(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasSms = Permissions.hasSms(context) }
    val thisMonth = YearMonth.now(zone)

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    MonthSelector(
                        month = s.month,
                        canGoBack = s.earliest == null || s.month > s.earliest!!,
                        canGoForward = s.month < thisMonth,
                        onChange = { vm.month.value = it },
                    )
                }
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add cash expense") }
            }
        }
        if (!hasSms) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings),
                ) {
                    Text(
                        "SMS access is off, so bank transactions aren't being recorded. Tap to fix it in Settings.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        val cur = s.current
        if (s.loading || cur == null) return@LazyColumn
        item { HeroCard(cur, s.previous, s.month) }
        if (cur.txnCount == 0) {
            item {
                EmptyState(
                    emoji = if (s.totalTransactions == 0) "🧾" else "🌿",
                    title = if (s.totalTransactions == 0) "No transactions yet" else "Nothing in ${monthLabel(s.month)}",
                    body = if (s.totalTransactions == 0) "Bank SMS will show up here automatically. You can also add a cash expense with +."
                    else "Use the arrows to look at another month.",
                    action = if (s.totalTransactions == 0) ({ Button(onClick = onAdd) { Text("Add a cash expense") } }) else null,
                )
            }
            return@LazyColumn
        }
        if (cur.byCategory.isNotEmpty()) {
            item { CategoryCard(cur, s.categories) { key -> onOpenCategory(key, s.month.toString()) } }
        }
        if (cur.topMerchants.isNotEmpty()) item { TopMerchantsCard(cur) }
        item { DailyCard(cur, s.month) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HeroCard(cur: MonthSummary, prev: MonthSummary?, month: YearMonth) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Spent in ${monthLabel(month)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                Inr.formatRounded(cur.spentPaise),
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (prev != null && prev.spentPaise > 0) {
                Spacer(Modifier.height(6.dp))
                val ch = Summaries.change(cur.spentPaise, prev.spentPaise)
                ChangeIndicator(ch.deltaPaise, ch.percent, monthShort(month.minusMonths(1)))
            } else if (prev != null) {
                Text(
                    "Nothing recorded in ${monthShort(month.minusMonths(1))} to compare with",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Stat("Income", Inr.formatRounded(cur.incomePaise), Modifier.weight(1f))
                Stat("Net", Inr.formatRounded(cur.incomePaise - cur.spentPaise), Modifier.weight(1f))
                Stat("Transactions", cur.txnCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

private data class Slice(val key: String?, val label: String, val emoji: String, val amount: Long, val color: Color)

@Composable
private fun CategoryCard(cur: MonthSummary, categories: Map<String, CategoryEntity>, onOpen: (String?) -> Unit) {
    // ≤ 6 segments: the top 5 categories keep their own colour, the rest fold into one neutral slice.
    val top = cur.byCategory.take(5)
    val rest = cur.byCategory.drop(5)
    val foldColor = MaterialTheme.colorScheme.outlineVariant
    val slices = top.map { t ->
        val c = categories[t.categoryKey]
        Slice(t.categoryKey, c?.name ?: t.categoryKey, c?.emoji ?: "📦", t.amountPaise, c.color())
    } + if (rest.isNotEmpty()) listOf(Slice(null, "${rest.size} more categories", "…", rest.sumOf { it.amountPaise }, foldColor)) else emptyList()
    val total = cur.spentPaise.coerceAtLeast(1)

    SectionCard(title = "Where it went") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CategoryDonut(values = slices.map { it.amount / 100.0 }, colors = slices.map { it.color })
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${cur.byCategory.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("categories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                slices.forEach { sl ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(sl.key) }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(sl.color)
                        Spacer(Modifier.width(8.dp))
                        Text("${sl.emoji} ${sl.label}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${sl.amount * 100 / total}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        cur.byCategory.forEach { t ->
            val c = categories[t.categoryKey]
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(t.categoryKey) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c?.emoji ?: "📦", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c?.name ?: t.categoryKey, style = MaterialTheme.typography.bodyLarge)
                    Text("${t.count} payment${if (t.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Inr.formatRounded(t.amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TopMerchantsCard(cur: MonthSummary) {
    val max = cur.topMerchants.maxOf { it.amountPaise }.coerceAtLeast(1)
    val barColor = LocalMoneyColors.current.chartSeries
    SectionCard(title = "Top merchants") {
        cur.topMerchants.forEach { m ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(Inr.formatRounded(m.amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(m.amountPaise.toFloat() / max).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${m.count}×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DailyCard(cur: MonthSummary, month: YearMonth) {
    val today = java.time.LocalDate.now(zone)
    val days = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
    val avg = cur.spentPaise / days.coerceAtLeast(1)
    val peakDay = cur.dailySpend.indices.maxByOrNull { cur.dailySpend[it] } ?: 0
    SectionCard(title = "Daily spending") {
        Text(
            "Average ${Inr.formatRounded(avg)} a day · highest ${Inr.formatRounded(cur.dailySpend[peakDay])} on ${peakDay + 1} ${monthShort(month)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        DailySpendChart(cur.dailySpend)
        Text(
            "Touch and hold a bar to see the amount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
