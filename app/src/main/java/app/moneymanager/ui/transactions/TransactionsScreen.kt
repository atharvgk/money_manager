package app.moneymanager.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.moneymanager.AppContainer
import app.moneymanager.core.format.Inr
import app.moneymanager.data.TxnFilter
import app.moneymanager.data.db.AccountRow
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.data.db.TransactionEntity
import app.moneymanager.ui.common.AmountText
import app.moneymanager.ui.common.CategoryBadge
import app.moneymanager.ui.common.EmptyState
import app.moneymanager.ui.common.appViewModel
import app.moneymanager.ui.common.dayHeader
import app.moneymanager.ui.common.isCredit
import app.moneymanager.ui.common.monthEnd
import app.moneymanager.ui.common.monthStart
import app.moneymanager.ui.common.shortDate
import app.moneymanager.ui.common.timeOfDay
import app.moneymanager.ui.common.toLocalDate
import app.moneymanager.ui.common.zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

sealed interface TxRow {
    data class Header(val date: LocalDate, val spent: Long, val received: Long) : TxRow
    data class Item(val t: TransactionEntity) : TxRow
}

data class TxState(
    val filter: TxnFilter = TxnFilter(),
    val rows: List<TxRow> = emptyList(),
    val count: Int = 0,
    val spent: Long = 0,
    val received: Long = 0,
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountRow> = emptyList(),
    val loading: Boolean = true,
) {
    val categoryMap = categories.associateBy { it.key }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TransactionsViewModel(c: AppContainer, initialCategory: String, initialMonth: String) : ViewModel() {
    private val repo = c.repository
    val filter = MutableStateFlow(
        TxnFilter(
            categories = if (initialCategory.isNotEmpty()) setOf(initialCategory) else emptySet(),
            from = runCatching { monthStart(YearMonth.parse(initialMonth)) }.getOrDefault(0L),
            to = runCatching { monthEnd(YearMonth.parse(initialMonth)) }.getOrDefault(Long.MAX_VALUE),
        ),
    )

    val state = combine(
        filter.debounce(150).flatMapLatest { f -> repo.observeFiltered(f).map { f to it } },
        repo.categories,
        repo.accounts,
    ) { (f, list), cats, accounts ->
        val rows = ArrayList<TxRow>(list.size + 64)
        list.groupBy { it.timestamp.toLocalDate() }.forEach { (date, items) ->
            val counted = items.filter { !it.excluded }
            rows += TxRow.Header(date, counted.filter { !it.isCredit }.sumOf { it.amountPaise }, counted.filter { it.isCredit }.sumOf { it.amountPaise })
            items.forEach { rows += TxRow.Item(it) }
        }
        val counted = list.filter { !it.excluded }
        TxState(
            filter = f,
            rows = rows,
            count = list.size,
            spent = counted.filter { !it.isCredit }.sumOf { it.amountPaise },
            received = counted.filter { it.isCredit }.sumOf { it.amountPaise },
            categories = cats,
            accounts = accounts,
            loading = false,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxState(filter = filter.value))

    fun update(block: (TxnFilter) -> TxnFilter) {
        filter.value = block(filter.value)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(initialCategory: String, initialMonth: String, onEdit: (Long) -> Unit, onAdd: () -> Unit) {
    val vm = appViewModel(key = "tx:$initialCategory:$initialMonth") { TransactionsViewModel(it, initialCategory, initialMonth) }
    val s by vm.state.collectAsStateWithLifecycle()
    val f by vm.filter.collectAsStateWithLifecycle()
    var showCategories by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var showDates by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            OutlinedTextField(
                value = f.query,
                onValueChange = { q -> vm.update { it.copy(query = q) } },
                placeholder = { Text("Search transactions", maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (f.query.isNotEmpty()) IconButton(onClick = { vm.update { it.copy(query = "") } }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val catLabel = when (f.categories.size) {
                    0 -> "Category"
                    1 -> s.categoryMap[f.categories.first()]?.let { "${it.emoji} ${it.name}" } ?: "1 category"
                    else -> "${f.categories.size} categories"
                }
                FilterChip(
                    selected = f.categories.isNotEmpty(),
                    onClick = { showCategories = true },
                    label = { Text(catLabel) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                )
                Box {
                    val acct = s.accounts.firstOrNull { it.account == f.account }
                    FilterChip(
                        selected = f.account != null,
                        onClick = { showAccounts = true },
                        label = { Text(acct?.let(::accountLabel) ?: f.account?.let { "••$it" } ?: "Account") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                    )
                    DropdownMenu(expanded = showAccounts, onDismissRequest = { showAccounts = false }) {
                        DropdownMenuItem(text = { Text("All accounts") }, onClick = { vm.update { it.copy(account = null) }; showAccounts = false })
                        s.accounts.forEach { a ->
                            DropdownMenuItem(
                                text = { Text("${accountLabel(a)}  (${a.n})") },
                                onClick = { vm.update { it.copy(account = a.account) }; showAccounts = false },
                            )
                        }
                    }
                }
                val hasDates = f.from > 0L || f.to < Long.MAX_VALUE
                FilterChip(
                    selected = hasDates,
                    onClick = { showDates = true },
                    label = { Text(if (hasDates) "${shortDate(f.from)} – ${shortDate(f.to - 1)}" else "Any date") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                )
                if (f.categories.isNotEmpty() || f.account != null || hasDates) {
                    TextButton(onClick = { vm.update { TxnFilter(query = it.query) } }) { Text("Clear") }
                }
            }
            if (!s.loading) {
                Text(
                    "${s.count} transaction${if (s.count == 1) "" else "s"} · spent ${Inr.formatRounded(s.spent)} · received ${Inr.formatRounded(s.received)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (!s.loading && s.rows.isEmpty()) {
                EmptyState("🔍", "No transactions", "Try a different search or clear the filters.")
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                s.rows.forEach { row ->
                    when (row) {
                        is TxRow.Header -> stickyHeader(key = "h${row.date}") { DayHeader(row) }
                        is TxRow.Item -> item(key = row.t.id) {
                            TransactionRow(row.t, s.categoryMap[row.t.categoryKey], onClick = { onEdit(row.t.id) })
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add cash expense") }
    }

    if (showCategories) {
        CategoryFilterDialog(s.categories, f.categories, onDismiss = { showCategories = false }) { sel ->
            vm.update { it.copy(categories = sel) }
            showCategories = false
        }
    }
    if (showDates) {
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = f.from.takeIf { it > 0 }?.let { it.toLocalDate().toEpochDay() * DAY },
            initialSelectedEndDateMillis = f.to.takeIf { it < Long.MAX_VALUE }?.let { (it - 1).toLocalDate().toEpochDay() * DAY },
        )
        DatePickerDialog(
            onDismissRequest = { showDates = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = picker.selectedStartDateMillis
                    val end = picker.selectedEndDateMillis ?: start
                    if (start != null && end != null) {
                        val from = LocalDate.ofEpochDay(start / DAY).atStartOfDay(zone).toInstant().toEpochMilli()
                        val to = LocalDate.ofEpochDay(end / DAY).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        vm.update { it.copy(from = from, to = to) }
                    }
                    showDates = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { vm.update { it.copy(from = 0L, to = Long.MAX_VALUE) }; showDates = false }) { Text("Any date") }
            },
        ) {
            DateRangePicker(state = picker, modifier = Modifier.weight(1f), title = { Text("Date range", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

private const val DAY = 24 * 3600 * 1000L

fun accountLabel(a: AccountRow): String {
    val kind = when (a.accountKind) {
        "CREDIT_CARD" -> "Credit card"
        "DEBIT_CARD", "CARD" -> "Card"
        else -> "A/c"
    }
    return "${a.bank ?: kind} ••${a.account}"
}

@Composable
private fun DayHeader(h: TxRow.Header) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayHeader(h.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (h.spent > 0) Text("−${Inr.formatRounded(h.spent)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TransactionRow(t: TransactionEntity, category: CategoryEntity?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.merchant ?: t.vpa ?: if (t.isCredit) "Money received" else "Payment",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val bits = buildList {
                add(category?.name ?: t.categoryKey)
                t.account?.let { add("••$it") }
                if (t.source == "MANUAL") add("Cash")
                add(timeOfDay(t.timestamp))
                if (t.excluded) add("Excluded")
            }
            Text(
                bits.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            t.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        AmountText(t.amountPaise, credit = t.isCredit, excluded = t.excluded)
    }
}

@Composable
private fun CategoryFilterDialog(all: List<CategoryEntity>, selected: Set<String>, onDismiss: () -> Unit, onApply: (Set<String>) -> Unit) {
    var sel by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            LazyColumn {
                items(all, key = { it.key }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = if (c.key in sel) sel - c.key else sel + c.key }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.key in sel, onCheckedChange = { sel = if (it) sel + c.key else sel - c.key })
                        Text("${c.emoji}  ${c.name}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(sel) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = { onApply(emptySet()) }) { Text("All") } },
    )
}
