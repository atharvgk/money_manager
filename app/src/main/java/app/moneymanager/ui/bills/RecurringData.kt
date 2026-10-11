package app.moneymanager.ui.bills

import app.moneymanager.core.analysis.Charge
import app.moneymanager.core.analysis.RecurringDetector
import app.moneymanager.core.analysis.RecurringPayment
import app.moneymanager.data.Repository
import app.moneymanager.ui.common.isCredit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Recurring payments found in the last ~13 months, plus the charges they were found in. */
data class RecurringData(val recurring: List<RecurringPayment>, val charges: List<Charge>) {
    val active get() = recurring.filter { it.active }
    val inactive get() = recurring.filter { !it.active }
    val monthlyTotal get() = active.sumOf { it.monthlyCostPaise }
}

private const val DAY = 24 * 3600 * 1000L

fun observeRecurring(repo: Repository): Flow<RecurringData> =
    repo.observeRange(System.currentTimeMillis() - 400 * DAY, Long.MAX_VALUE).map { txns ->
        val charges = txns.filter { !it.isCredit && !it.excluded && it.merchantKey != null }
            .map { Charge(it.merchantKey!!, it.merchant ?: it.vpa ?: it.merchantKey, it.amountPaise, it.timestamp, it.categoryKey) }
        RecurringData(RecurringDetector.detect(charges, System.currentTimeMillis()), charges)
    }
