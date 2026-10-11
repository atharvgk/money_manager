package app.moneymanager.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.moneymanager.AppContainer
import app.moneymanager.core.analysis.Cadence
import app.moneymanager.core.analysis.Charge
import app.moneymanager.core.analysis.RecurringDetector
import app.moneymanager.core.analysis.RecurringPayment
import app.moneymanager.core.format.Inr
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.ui.common.CategoryBadge
import app.moneymanager.ui.common.EmptyState
import app.moneymanager.ui.common.appViewModel
import app.moneymanager.ui.common.isCredit
import app.moneymanager.ui.common.shortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class SubsState(
    val active: List<RecurringPayment> = emptyList(),
    val inactive: List<RecurringPayment> = emptyList(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val loading: Boolean = true,
) {
    val monthlyTotal get() = active.sumOf { it.monthlyCostPaise }
}

class SubscriptionsViewModel(c: AppContainer) : ViewModel() {
    private val since = System.currentTimeMillis() - 400L * 24 * 3600 * 1000

    val state = combine(c.repository.observeRange(since, Long.MAX_VALUE), c.repository.categories) { txns, cats ->
        val charges = txns.filter { !it.isCredit && !it.excluded && it.merchantKey != null }
            .map { Charge(it.merchantKey!!, it.merchant ?: it.vpa ?: it.merchantKey, it.amountPaise, it.timestamp, it.categoryKey) }
        val found = RecurringDetector.detect(charges, System.currentTimeMillis())
        SubsState(found.filter { it.active }, found.filter { !it.active }, cats.associateBy { it.key }, loading = false)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubsState())
}

@Composable
fun SubscriptionsScreen() {
    val vm = appViewModel { SubscriptionsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Recurring payments", style = MaterialTheme.typography.titleLarge) }
        if (s.loading) return@LazyColumn
        if (s.active.isEmpty() && s.inactive.isEmpty()) {
            item {
                EmptyState(
                    "🔁",
                    "Nothing recurring yet",
                    "Subscriptions, rent, SIPs and EMIs show up here once the same merchant charges a similar amount on a regular schedule.",
                )
            }
            return@LazyColumn
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("About ${Inr.formatRounded(s.monthlyTotal)} a month", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "${s.active.size} active · ${Inr.formatRounded(s.monthlyTotal * 12)} a year",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        items(s.active, key = { "a" + it.merchantKey + it.typicalAmountPaise }) { RecurringRow(it, s.categories[it.categoryKey]) }
        if (s.inactive.isNotEmpty()) {
            item {
                Text(
                    "Not seen recently",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(s.inactive, key = { "i" + it.merchantKey + it.typicalAmountPaise }) { RecurringRow(it, s.categories[it.categoryKey]) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RecurringRow(r: RecurringPayment, category: CategoryEntity?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val next = if (r.active) " · next ~${shortDate(r.nextExpectedTimestamp)}" else ""
                val seen = if (r.confirmed) "${r.count} payments" else "Probably a subscription"
                Text(
                    "${r.cadence.label} · last ${shortDate(r.lastTimestamp)}$next",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(seen, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Inr.format(r.typicalAmountPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val per = when (r.cadence) {
                    Cadence.WEEKLY -> "per week"
                    Cadence.MONTHLY -> "per month"
                    Cadence.QUARTERLY -> "per quarter"
                    Cadence.YEARLY -> "per year"
                }
                Text(per, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
