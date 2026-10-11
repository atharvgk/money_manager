package app.moneymanager.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.moneymanager.AppContainer
import app.moneymanager.core.analysis.BillCalendar
import app.moneymanager.core.analysis.BillEntry
import app.moneymanager.core.analysis.BillStatus
import app.moneymanager.core.analysis.Cadence
import app.moneymanager.core.analysis.RecurringPayment
import app.moneymanager.core.format.Inr
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.ui.common.AmountColumn
import app.moneymanager.ui.common.AppCard
import app.moneymanager.ui.common.CategoryBadge
import app.moneymanager.ui.common.EmptyState
import app.moneymanager.ui.common.ItemRow
import app.moneymanager.ui.common.MonthSelector
import app.moneymanager.ui.common.ScreenHeader
import app.moneymanager.ui.common.SectionCard
import app.moneymanager.ui.common.appViewModel
import app.moneymanager.ui.common.shortDate
import app.moneymanager.ui.common.zone
import app.moneymanager.ui.theme.LocalMoneyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class BillsState(
    val loading: Boolean = true,
    val month: YearMonth,
    val entries: List<BillEntry> = emptyList(),
    val data: RecurringData = RecurringData(emptyList(), emptyList()),
    val categories: Map<String, CategoryEntity> = emptyMap(),
)

class BillsViewModel(c: AppContainer) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now(zone))

    val state = combine(observeRecurring(c.repository), c.repository.categories, month) { data, cats, m ->
        BillsState(
            loading = false,
            month = m,
            entries = BillCalendar.month(m, data.recurring, data.charges, LocalDate.now(zone), zone),
            data = data,
            categories = cats.associateBy { it.key },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsState(month = YearMonth.now(zone)))
}

private val locale = Locale.forLanguageTag("en-IN")
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", locale)

@Composable
fun BillsScreen() {
    val vm = appViewModel { BillsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable(s.month) { mutableStateOf<Int?>(null) }
    val now = YearMonth.now(zone)

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Bills", subtitle = "Subscriptions, rent, EMIs, SIPs and other regular payments") }
        if (s.loading) return@LazyColumn
        if (s.data.recurring.isEmpty()) {
            item {
                EmptyState(
                    "🗓️",
                    "No regular payments yet",
                    "They appear here once the same payee is charged a similar amount on a regular schedule (usually after 2–3 payments).",
                )
            }
            return@LazyColumn
        }
        item {
            AppCard(padding = PaddingValues(20.dp)) {
                Text("Regular payments cost about", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Inr.formatRounded(s.data.monthlyTotal), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(" a month", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    "${s.data.active.size} active · ${Inr.formatRounded(s.data.monthlyTotal * 12)} a year",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AppCard(padding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MonthSelector(
                        month = s.month,
                        canGoBack = s.month > now.minusMonths(12),
                        canGoForward = s.month < now.plusMonths(3),
                        onChange = { vm.month.value = it },
                    )
                }
                CalendarGrid(s.month, s.entries, selected) { selected = if (selected == it) null else it }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(BillStatus.PAID, "Paid")
                    Legend(BillStatus.DUE, "Due")
                    Legend(BillStatus.NOT_SEEN, "Not seen")
                }
            }
        }
        item {
            val day = selected
            val list = if (day != null) s.entries.filter { it.date.dayOfMonth == day } else s.entries
            SectionCard(
                title = if (day != null) DAY_TITLE.format(s.month.atDay(day)) else "In ${s.month.month.getDisplayName(TextStyle.FULL, locale)}",
                subtitle = if (day == null && list.isNotEmpty()) {
                    val paid = list.filter { it.status == BillStatus.PAID }.sumOf { it.amountPaise }
                    val due = list.filter { it.status != BillStatus.PAID }.sumOf { it.amountPaise }
                    "Paid ${Inr.formatRounded(paid)}" + if (due > 0) " · still expected ${Inr.formatRounded(due)}" else ""
                } else null,
            ) {
                if (list.isEmpty()) {
                    Text("Nothing expected.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                list.forEach { e -> EntryRow(e, s.categories[e.payment.categoryKey]) }
            }
        }
        item {
            SectionCard(title = "All regular payments") {
                s.data.active.forEach { r -> RecurringRow(r, s.categories[r.categoryKey]) }
                if (s.data.inactive.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Not seen recently", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.data.inactive.forEach { r -> RecurringRow(r, s.categories[r.categoryKey]) }
                }
            }
        }
    }
}

/** Paid = filled green, due = hollow ring, not seen = filled amber: shape and colour both differ. */
@Composable
private fun StatusDot(status: BillStatus, size: Dp = 6.dp) {
    val colors = LocalMoneyColors.current
    val base = Modifier.size(size).clip(CircleShape)
    when (status) {
        BillStatus.PAID -> Box(base.background(colors.better))
        BillStatus.DUE -> Box(base.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape))
        BillStatus.NOT_SEEN -> Box(base.background(colors.warning))
    }
}

@Composable
private fun Legend(status: BillStatus, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(status, 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarGrid(month: YearMonth, entries: List<BillEntry>, selected: Int?, onPick: (Int) -> Unit) {
    val today = LocalDate.now(zone)
    val offset = month.atDay(1).dayOfWeek.value - 1 // weeks start on Monday
    val cells = offset + month.lengthOfMonth()
    val byDay = entries.groupBy { it.date.dayOfMonth }
    Column {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        for (week in 0 until (cells + 6) / 7) {
            Row {
                for (dow in 0 until 7) {
                    val day = week * 7 + dow - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val isToday = month == YearMonth.from(today) && day == today.dayOfMonth
                            val dayEntries = byDay[day].orEmpty()
                            val shape = RoundedCornerShape(10.dp)
                            Column(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                                    .background(if (selected == day) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                                    .clickable { onPick(day) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (dayEntries.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (dayEntries.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                    dayEntries.take(3).forEach { e -> StatusDot(e.status) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: BillEntry, category: CategoryEntity?) {
    val colors = LocalMoneyColors.current
    ItemRow(
        title = e.payment.merchant,
        subtitle = when (e.status) {
            BillStatus.PAID -> "Paid · ${DAY_TITLE.format(e.date)}"
            BillStatus.DUE -> "Due around ${DAY_TITLE.format(e.date)}"
            BillStatus.NOT_SEEN -> "Expected around ${DAY_TITLE.format(e.date)} · not seen"
        },
        leading = { CategoryBadge(category) },
        trailing = {
            AmountColumn((if (e.status == BillStatus.PAID) "" else "~") + Inr.format(e.amountPaise)) {
                Text(
                    when (e.status) {
                        BillStatus.PAID -> "✓ paid"
                        BillStatus.DUE -> "due"
                        BillStatus.NOT_SEEN -> "! not seen"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (e.status) {
                        BillStatus.PAID -> colors.better
                        BillStatus.DUE -> MaterialTheme.colorScheme.onSurfaceVariant
                        BillStatus.NOT_SEEN -> colors.warning
                    },
                )
            }
        },
    )
}

@Composable
private fun RecurringRow(r: RecurringPayment, category: CategoryEntity?) {
    val per = when (r.cadence) {
        Cadence.WEEKLY -> "a week"
        Cadence.MONTHLY -> "a month"
        Cadence.QUARTERLY -> "a quarter"
        Cadence.YEARLY -> "a year"
    }
    val next = if (r.active) BillCalendar.nextDue(r, zone, LocalDate.now(zone))?.let { " · next ~${SHORT_DAY.format(it)}" } ?: "" else ""
    ItemRow(
        title = r.merchant,
        subtitle = if (r.confirmed) "${r.cadence.label} · last ${shortDate(r.lastTimestamp)}$next" else "Probably a subscription · ${shortDate(r.lastTimestamp)}",
        leading = { CategoryBadge(category) },
        trailing = {
            AmountColumn(Inr.format(r.typicalAmountPaise)) {
                Text(per, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
