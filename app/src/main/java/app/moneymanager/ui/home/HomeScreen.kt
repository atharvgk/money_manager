package app.moneymanager.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.moneymanager.AppContainer
import app.moneymanager.core.analysis.BillCalendar
import app.moneymanager.core.analysis.BillEntry
import app.moneymanager.core.analysis.Summaries
import app.moneymanager.core.format.Inr
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.data.db.TransactionEntity
import app.moneymanager.ui.bills.observeRecurring
import app.moneymanager.ui.common.AccountChip
import app.moneymanager.ui.common.AccountChipsRow
import app.moneymanager.ui.common.AccountScope
import app.moneymanager.ui.common.AmountColumn
import app.moneymanager.ui.common.AppCard
import app.moneymanager.ui.common.BudgetDialog
import app.moneymanager.ui.common.BudgetStatus
import app.moneymanager.ui.common.CardBook
import app.moneymanager.ui.common.CardLink
import app.moneymanager.ui.common.CategoryBadge
import app.moneymanager.ui.common.CategoryChange
import app.moneymanager.ui.common.ChangeIndicator
import app.moneymanager.ui.common.DeltaText
import app.moneymanager.ui.common.EmptyState
import app.moneymanager.ui.common.ItemRow
import app.moneymanager.ui.common.MonthMenu
import app.moneymanager.ui.common.Period
import app.moneymanager.ui.common.ProgressTrack
import app.moneymanager.ui.common.ScopeTabs
import app.moneymanager.ui.common.SectionCard
import app.moneymanager.ui.common.ShareBar
import app.moneymanager.ui.common.Snapshot
import app.moneymanager.ui.common.SpendMath
import app.moneymanager.ui.common.accountChips
import app.moneymanager.ui.common.appViewModel
import app.moneymanager.ui.common.color
import app.moneymanager.ui.common.matchesAccount
import app.moneymanager.ui.common.monthEnd
import app.moneymanager.ui.common.monthStart
import app.moneymanager.ui.common.percentOf
import app.moneymanager.ui.common.zone
import app.moneymanager.ui.theme.LocalMoneyColors
import app.moneymanager.ui.transactions.TransactionRow
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class HomeState(
    val loading: Boolean = true,
    val month: YearMonth,
    val scope: AccountScope = AccountScope.ALL,
    val earliest: YearMonth? = null,
    val hasCards: Boolean = false,
    val period: Period? = null,
    val comparison: Period? = null,
    val current: Snapshot = Snapshot.Empty,
    val previous: Snapshot = Snapshot.Empty,
    val changes: List<CategoryChange> = emptyList(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val budgetLimit: Long = 0,
    val budget: BudgetStatus? = null,
    val chips: List<AccountChip> = emptyList(),
    val recentAccount: String? = null,
    val recent: List<TransactionEntity> = emptyList(),
    val upcoming: List<BillEntry> = emptyList(),
    val totalTransactions: Int = 0,
)

private data class HomeInputs(val month: YearMonth, val scope: AccountScope, val account: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val month = MutableStateFlow(YearMonth.now(zone))
    val scope = MutableStateFlow(AccountScope.ALL)
    val recentAccount = MutableStateFlow<String?>(null)

    private val inputs = combine(month, scope, recentAccount) { m, s, a -> HomeInputs(m, s, a) }

    private val meta = combine(repo.categories, repo.creditCardAccounts, repo.earliestTimestamp, repo.transactionCount, c.settings.prefs.map { it.monthlyBudget }) { cats, cards, earliest, count, budget ->
        Meta(cats.associateBy { it.key }, CardBook(cards), earliest?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) }, count, budget)
    }

    private val upcoming = observeRecurring(repo).map { BillCalendar.upcoming(it.recurring, LocalDate.now(zone), zone).take(4) }

    val state = combine(
        month.flatMapLatest { m -> repo.observeRange(monthStart(m.minusMonths(1)), monthEnd(m)).map { m to it } },
        inputs,
        meta,
        upcoming,
    ) { (m, txns), inp, meta, upcoming ->
        if (inp.month != m) return@combine null // the month just changed; its data is on the way
        val today = LocalDate.now(zone)
        val period = SpendMath.period(m, today, zone)
        val comparison = SpendMath.comparison(period, zone)
        val scoped = txns.filter { meta.cards.inScope(it, inp.scope) }
        val current = SpendMath.snapshot(scoped, period)
        val previous = SpendMath.snapshot(scoped, comparison)
        val inMonth = scoped.filter { period.contains(it.timestamp) }
        val chips = accountChips(inMonth, meta.cards)
        val account = inp.account?.takeIf { key -> chips.any { it.key == key } }
        HomeState(
            loading = false,
            month = m,
            scope = inp.scope,
            earliest = meta.earliest,
            hasCards = meta.cards.hasCards,
            period = period,
            comparison = comparison,
            current = current,
            previous = previous,
            changes = SpendMath.changes(current, previous),
            categories = meta.categories,
            budgetLimit = meta.budget,
            budget = if (meta.budget > 0) {
                val all = SpendMath.snapshot(txns, period)
                SpendMath.budget(meta.budget, all.spent, SpendMath.dayToDay(all), m, today)
            } else null,
            chips = chips,
            recentAccount = account,
            recent = inMonth.filter { it.matchesAccount(account) }.take(5),
            upcoming = if (m == YearMonth.from(today)) upcoming else emptyList(),
            totalTransactions = meta.count,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class Meta(
        val categories: Map<String, CategoryEntity>,
        val cards: CardBook,
        val earliest: YearMonth?,
        val count: Int,
        val budget: Long,
    )
}

@Composable
fun HomeScreen(
    onOpenSpend: (category: String?, month: YearMonth, scope: AccountScope) -> Unit,
    onOpenCategories: (month: YearMonth, scope: AccountScope) -> Unit,
    onOpenTransactions: (account: String?, month: YearMonth) -> Unit,
    onOpenBills: () -> Unit,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val latest by vm.state.collectAsStateWithLifecycle()
    // Keep showing the last good state while a new month loads, so the screen doesn't flash.
    var shown by remember { mutableStateOf<HomeState?>(null) }
    latest?.let { shown = it }
    val s = shown
    val context = LocalContext.current
    var hasSms by remember { mutableStateOf(Permissions.hasSms(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasSms = Permissions.hasSms(context) }
    var editBudget by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthMenu(
                    month = s?.month ?: vm.month.value,
                    earliest = s?.earliest,
                    onChange = { vm.month.value = it },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = "Add cash expense") }
            }
        }
        if (!hasSms) item { SmsOffCard(onOpenSettings) }
        if (s == null) return@LazyColumn
        if (s.hasCards) item { ScopeTabs(s.scope, onChange = { vm.scope.value = it }) }

        if (s.totalTransactions == 0) {
            item {
                EmptyState(
                    emoji = "🧾",
                    title = "No transactions yet",
                    body = "Bank SMS show up here automatically. You can also add a cash expense.",
                    action = { Button(onClick = onAdd) { Text("Add a cash expense") } },
                )
            }
            return@LazyColumn
        }

        item { SummaryCard(s, onClick = { onOpenSpend(null, s.month, s.scope) }) }
        if (s.scope == AccountScope.ALL) {
            item { BudgetCard(s, onEdit = { editBudget = true }) }
        }
        if (s.current.byCategory.isNotEmpty()) {
            item {
                CategoriesCard(
                    s,
                    onOpen = { onOpenSpend(it, s.month, s.scope) },
                    onAll = { onOpenCategories(s.month, s.scope) },
                )
            }
        }
        val changes = s.changes.take(4)
        if (changes.isNotEmpty() && s.previous.spent > 0) {
            item { ChangesCard(changes, s.categories, s.comparison!!) { onOpenSpend(it, s.month, s.scope) } }
        }
        if (s.upcoming.isNotEmpty()) item { ComingUpCard(s.upcoming, s.categories, onOpenBills) }
        item {
            RecentCard(
                s,
                onSelectAccount = { vm.recentAccount.value = it },
                onSeeAll = { onOpenTransactions(s.recentAccount, s.month) },
                onEdit = onEdit,
            )
        }
    }

    if (editBudget) BudgetDialog(currentPaise = s?.budgetLimit ?: 0, onDismiss = { editBudget = false })
}

@Composable
private fun SmsOffCard(onOpenSettings: () -> Unit) {
    AppCard(onClick = onOpenSettings) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Sms, contentDescription = null, tint = LocalMoneyColors.current.warning)
            Spacer(Modifier.width(12.dp))
            Text(
                "SMS access is off, so bank transactions aren't being recorded. Tap to fix it.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryCard(s: HomeState, onClick: () -> Unit) {
    val period = s.period ?: return
    AppCard(onClick = onClick, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (period.partial) "Spent so far · " else "Spent in ") + period.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Spending details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Inr.formatRounded(s.current.spent), fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(8.dp))
        val versus = s.comparison!!.shortLabel
        if (s.previous.spent > 0) {
            val ch = Summaries.change(s.current.spent, s.previous.spent)
            ChangeIndicator(ch.deltaPaise, ch.percent, versus)
        } else {
            Text("Nothing spent in $versus to compare with", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(14.dp))
        Row {
            Stat("Money in", Inr.formatRounded(s.current.received), LocalMoneyColors.current.income, Modifier.weight(1f))
            Stat("Money out", Inr.formatRounded(s.current.spent), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            val net = s.current.received - s.current.spent
            Stat("Net", (if (net > 0) "+" else if (net < 0) "−" else "") + Inr.formatRounded(abs(net)), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun BudgetCard(s: HomeState, onEdit: () -> Unit) {
    val b = s.budget
    val current = s.month == YearMonth.now(zone)
    if (b == null) {
        if (!current) return
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Monthly budget", style = MaterialTheme.typography.titleMedium)
                    Text("Set a limit to see what's left and a daily allowance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onEdit) { Text("Set") }
            }
        }
        return
    }
    val colors = LocalMoneyColors.current
    val trackColor = when {
        b.over -> colors.worse
        b.fraction > 0.85f -> colors.warning
        else -> colors.chartSeries
    }
    SectionCard(title = "Budget", action = { CardLink("Edit", onEdit) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                when {
                    b.over -> "${Inr.formatRounded(-b.left)} over"
                    current -> "${Inr.formatRounded(b.left)} left"
                    else -> "${Inr.formatRounded(b.left)} under"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (b.over) colors.worse else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text("of ${Inr.formatRounded(b.limit)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        ProgressTrack(b.fraction, trackColor)
        Spacer(Modifier.height(10.dp))
        val lines = buildList {
            if (current && !b.over && b.daysLeft > 0) add("You can spend about ${Inr.formatRounded(b.perDay)} a day for the next ${b.daysLeft} day${if (b.daysLeft == 1) "" else "s"}.")
            if (!current) add(if (b.over) "Went over the budget this month." else "Stayed within the budget this month.")
            b.projected?.let { p ->
                if (!b.over) add("At your everyday pace, the month ends around ${Inr.formatRounded(p)}" + if (p > b.limit) ", over budget." else ".")
            }
        }
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun CategoriesCard(s: HomeState, onOpen: (String) -> Unit, onAll: () -> Unit) {
    val all = s.current.byCategory
    val top = all.take(4)
    val rest = all.drop(4)
    val other = MaterialTheme.colorScheme.outline
    val total = s.current.spent.coerceAtLeast(1)
    SectionCard(title = "Where it went", action = { CardLink("All categories", onAll) }) {
        ShareBar(top.map { s.categories[it.key].color() to it.amount } + if (rest.isNotEmpty()) listOf(other to rest.sumOf { it.amount }) else emptyList())
        Spacer(Modifier.height(8.dp))
        top.forEach { c ->
            val cat = s.categories[c.key]
            ItemRow(
                title = cat?.name ?: c.key,
                subtitle = "${percentOf(c.amount, total)} · ${c.count} payment${if (c.count == 1) "" else "s"}",
                leading = { CategoryBadge(cat) },
                trailing = { AmountColumn(Inr.formatRounded(c.amount)) },
                onClick = { onOpen(c.key) },
            )
        }
        if (rest.isNotEmpty()) {
            ItemRow(
                title = "${rest.size} more categor${if (rest.size == 1) "y" else "ies"}",
                subtitle = "${percentOf(rest.sumOf { it.amount }, total)} · " + rest.joinToString { s.categories[it.key]?.name ?: it.key },
                leading = {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Text("+${rest.size}", style = MaterialTheme.typography.labelLarge)
                    }
                },
                trailing = { AmountColumn(Inr.formatRounded(rest.sumOf { it.amount })) },
                onClick = onAll,
            )
        }
    }
}

@Composable
private fun ChangesCard(changes: List<CategoryChange>, categories: Map<String, CategoryEntity>, comparison: Period, onOpen: (String) -> Unit) {
    SectionCard(title = "Biggest changes", subtitle = "Compared with ${comparison.shortLabel}") {
        changes.forEach { ch ->
            val cat = categories[ch.key]
            ItemRow(
                title = cat?.name ?: ch.key,
                subtitle = "${Inr.formatRounded(ch.previous)} → ${Inr.formatRounded(ch.current)}",
                leading = { CategoryBadge(cat) },
                trailing = { DeltaText(ch.delta) },
                onClick = { onOpen(ch.key) },
            )
        }
    }
}

private val DAY_NUM = DateTimeFormatter.ofPattern("d", Locale.forLanguageTag("en-IN"))
private val MONTH_ABBR = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("en-IN"))

@Composable
fun DateTile(date: LocalDate) {
    Column(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(DAY_NUM.format(date), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, lineHeight = 14.sp)
        Text(MONTH_ABBR.format(date).uppercase(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, lineHeight = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ComingUpCard(entries: List<BillEntry>, categories: Map<String, CategoryEntity>, onOpenBills: () -> Unit) {
    val today = LocalDate.now(zone)
    SectionCard(title = "Coming up", subtitle = "Recurring payments in the next 2 weeks", action = { CardLink("Calendar", onOpenBills) }) {
        entries.forEach { e ->
            val days = e.date.toEpochDay() - today.toEpochDay()
            ItemRow(
                title = e.payment.merchant,
                subtitle = when (days) {
                    0L -> "Due today"
                    1L -> "Due tomorrow"
                    else -> "Due in $days days"
                } + " · " + (categories[e.payment.categoryKey]?.name ?: ""),
                leading = { DateTile(e.date) },
                trailing = { AmountColumn("~" + Inr.formatRounded(e.amountPaise)) },
                onClick = onOpenBills,
            )
        }
    }
}

@Composable
private fun RecentCard(s: HomeState, onSelectAccount: (String?) -> Unit, onSeeAll: () -> Unit, onEdit: (Long) -> Unit) {
    SectionCard(title = "Recent transactions", action = { CardLink("See all", onSeeAll) }) {
        AccountChipsRow(s.chips, s.recentAccount, onSelectAccount)
        if (s.chips.size >= 2 || s.recentAccount != null) Spacer(Modifier.height(4.dp))
        if (s.recent.isEmpty()) {
            Text(
                "Nothing in ${s.period?.label ?: "this month"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        s.recent.forEach { t ->
            TransactionRow(t, s.categories[t.categoryKey], onClick = { onEdit(t.id) }, padding = PaddingValues(vertical = 9.dp), showDate = true)
        }
    }
}
