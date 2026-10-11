package app.moneymanager.core.analysis

import app.moneymanager.core.model.TxnType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Minimal view of a stored transaction, enough for every chart and total. */
data class TxnRecord(
    val type: TxnType,
    val amountPaise: Long,
    val timestamp: Long,
    val categoryKey: String,
    val merchant: String?,
    val merchantKey: String?,
    val excluded: Boolean,
)

data class CategoryTotal(val categoryKey: String, val amountPaise: Long, val count: Int)
data class MerchantTotal(val merchantKey: String, val merchant: String, val amountPaise: Long, val count: Int)

data class MonthSummary(
    val month: YearMonth,
    val spentPaise: Long,
    val incomePaise: Long,
    val byCategory: List<CategoryTotal>,
    val topMerchants: List<MerchantTotal>,
    /** Spend per day of month; index 0 = day 1. */
    val dailySpend: LongArray,
    val txnCount: Int,
)

data class MonthChange(val deltaPaise: Long, val percent: Double?)

object Summaries {
    /** Excluded transactions never count. Spending = debits, income = credits. */
    fun month(txns: List<TxnRecord>, month: YearMonth, zone: ZoneId): MonthSummary {
        val inMonth = txns.filter { !it.excluded && YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month }
        val debits = inMonth.filter { it.type == TxnType.DEBIT }
        val daily = LongArray(month.lengthOfMonth())
        for (d in debits) {
            val day = Instant.ofEpochMilli(d.timestamp).atZone(zone).dayOfMonth
            daily[day - 1] += d.amountPaise
        }
        val byCategory = debits.groupBy { it.categoryKey }
            .map { (k, l) -> CategoryTotal(k, l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.amountPaise }
        val topMerchants = debits.filter { it.merchantKey != null }
            .groupBy { it.merchantKey!! }
            .map { (k, l) -> MerchantTotal(k, l.last().merchant ?: k, l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.amountPaise }
            .take(5)
        return MonthSummary(
            month = month,
            spentPaise = debits.sumOf { it.amountPaise },
            incomePaise = inMonth.filter { it.type == TxnType.CREDIT }.sumOf { it.amountPaise },
            byCategory = byCategory,
            topMerchants = topMerchants,
            dailySpend = daily,
            txnCount = inMonth.size,
        )
    }

    fun change(current: Long, previous: Long): MonthChange =
        MonthChange(current - previous, if (previous > 0) (current - previous) * 100.0 / previous else null)

    /** category -> spend per month, aligned with [months]. */
    fun categoryByMonth(txns: List<TxnRecord>, months: List<YearMonth>, zone: ZoneId): Map<String, LongArray> {
        val index = months.withIndex().associate { (i, m) -> m to i }
        val out = HashMap<String, LongArray>()
        for (t in txns) {
            if (t.excluded || t.type != TxnType.DEBIT) continue
            val i = index[YearMonth.from(Instant.ofEpochMilli(t.timestamp).atZone(zone))] ?: continue
            out.getOrPut(t.categoryKey) { LongArray(months.size) }[i] += t.amountPaise
        }
        return out
    }
}
