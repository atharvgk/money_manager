package app.moneymanager.ui.common

import app.moneymanager.core.categorize.Categories
import app.moneymanager.data.db.Source
import app.moneymanager.data.db.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Which money to look at: everything, bank accounts (incl. debit cards and cash), or credit cards. */
enum class AccountScope(val label: String) { ALL("All"), BANK("Bank"), CARDS("Cards") }

/**
 * Knows which card numbers are credit cards. Some SMS just say "Card 5678"; if any other SMS called
 * 5678 a credit card, those count as credit card spends too.
 */
class CardBook(creditCardAccounts: Collection<String>) {
    private val credit = creditCardAccounts.toHashSet()
    val hasCards get() = credit.isNotEmpty()

    fun isCreditCard(t: TransactionEntity) =
        t.accountKind == "CREDIT_CARD" || (t.accountKind == "CARD" && t.account != null && t.account in credit)

    fun inScope(t: TransactionEntity, scope: AccountScope) = when (scope) {
        AccountScope.ALL -> true
        AccountScope.BANK -> !isCreditCard(t)
        AccountScope.CARDS -> isCreditCard(t)
    }

    companion object {
        val Empty = CardBook(emptyList())
    }
}

/** One account chip: "HDFC ••1234", "Card ••5678", "Cash". [key] is what transactions are matched on. */
data class AccountChip(val key: String, val label: String, val count: Int)

const val CASH_KEY = "cash"

fun TransactionEntity.accountKey(): String? = when {
    source == Source.MANUAL -> CASH_KEY
    else -> account
}

fun TransactionEntity.matchesAccount(key: String?) = key == null || accountKey() == key

private val BANK_SUFFIX = Regex("""\s+(?:Bank|FIRST)$""")

/** "HDFC Bank" → "HDFC", so chips stay short. */
fun shortBank(bank: String?): String? = bank?.replace(BANK_SUFFIX, "")?.takeIf { it.isNotBlank() }

fun accountChips(txns: List<TransactionEntity>, cards: CardBook): List<AccountChip> =
    txns.filter { !it.deleted && it.accountKey() != null }
        .groupBy { it.accountKey()!! }
        .map { (key, list) ->
            val label = if (key == CASH_KEY) "Cash" else {
                val name = shortBank(list.firstNotNullOfOrNull { it.bank }) ?: if (list.any(cards::isCreditCard)) "Card" else "A/c"
                "$name ••$key"
            }
            AccountChip(key, label, list.size)
        }
        .sortedByDescending { it.count }

/**
 * A span of days. For the month in progress it runs from the 1st to today, so it can be compared with
 * the same days of last month ("1–11 Oct" vs "1–11 Sep"); for a past month it's the whole month.
 */
data class Period(val month: YearMonth, val lastDay: Int, val partial: Boolean, val from: Long, val to: Long) {
    /** "1–11 Oct" or "October". */
    val label: String get() = if (partial) "1–$lastDay ${short()}" else full()

    /** "1–11 Sep" or "Sep". */
    val shortLabel: String get() = if (partial) "1–$lastDay ${short()}" else short()

    fun contains(ts: Long) = ts in from until to

    private fun short() = month.month.getDisplayName(TextStyle.SHORT, LOCALE)
    private fun full() = month.month.getDisplayName(TextStyle.FULL, LOCALE) + if (month.year != YearMonth.now().year) " ${month.year}" else ""

    private companion object {
        val LOCALE: Locale = Locale.forLanguageTag("en-IN")
    }
}

data class CategoryAmount(val key: String, val amount: Long, val count: Int)

data class Snapshot(val spent: Long, val received: Long, val byCategory: List<CategoryAmount>, val count: Int) {
    companion object {
        val Empty = Snapshot(0, 0, emptyList(), 0)
    }
}

data class CategoryChange(val key: String, val current: Long, val previous: Long) {
    val delta: Long get() = current - previous
}

data class MerchantAmount(val name: String, val amount: Long, val count: Int)

/** Where a month stands against the budget. [perDay] is what's left per remaining day, including today. */
data class BudgetStatus(
    val limit: Long,
    val spent: Long,
    val daysLeft: Int,
    val projected: Long?,
) {
    val left: Long get() = limit - spent
    val over: Boolean get() = spent > limit
    val fraction: Float get() = if (limit <= 0) 0f else (spent.toFloat() / limit).coerceIn(0f, 1f)
    val perDay: Long get() = if (daysLeft > 0 && left > 0) left / daysLeft else 0
}

object SpendMath {
    fun period(month: YearMonth, today: LocalDate, zone: ZoneId): Period {
        val partial = YearMonth.from(today) == month
        return span(month, if (partial) today.dayOfMonth else month.lengthOfMonth(), partial, zone)
    }

    /** The month before [p], over the same days when [p] is still in progress (clamped for short months). */
    fun comparison(p: Period, zone: ZoneId): Period {
        val prev = p.month.minusMonths(1)
        val lastDay = if (p.partial) minOf(p.lastDay, prev.lengthOfMonth()) else prev.lengthOfMonth()
        return span(prev, lastDay, p.partial, zone)
    }

    private fun span(month: YearMonth, lastDay: Int, partial: Boolean, zone: ZoneId) = Period(
        month = month,
        lastDay = lastDay,
        partial = partial,
        from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        to = month.atDay(lastDay).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    private fun TransactionEntity.counts() = !excluded && !deleted

    /** Excluded and deleted transactions never count. Spent = debits, received = credits. */
    fun snapshot(txns: List<TransactionEntity>, p: Period, category: String? = null): Snapshot {
        val inPeriod = txns.filter { it.counts() && p.contains(it.timestamp) && (category == null || it.categoryKey == category) }
        val debits = inPeriod.filter { it.type == "DEBIT" }
        return Snapshot(
            spent = debits.sumOf { it.amountPaise },
            received = inPeriod.filter { it.type == "CREDIT" }.sumOf { it.amountPaise },
            byCategory = debits.groupBy { it.categoryKey }
                .map { (k, l) -> CategoryAmount(k, l.sumOf { it.amountPaise }, l.size) }
                .sortedByDescending { it.amount },
            count = inPeriod.size,
        )
    }

    /** Categories whose spending changed, biggest change first. */
    fun changes(current: Snapshot, previous: Snapshot): List<CategoryChange> {
        val cur = current.byCategory.associate { it.key to it.amount }
        val prev = previous.byCategory.associate { it.key to it.amount }
        return (cur.keys + prev.keys)
            .map { CategoryChange(it, cur[it] ?: 0, prev[it] ?: 0) }
            .filter { it.delta != 0L }
            .sortedByDescending { abs(it.delta) }
    }

    fun topMerchants(txns: List<TransactionEntity>, p: Period, category: String?, limit: Int = 5): List<MerchantAmount> =
        txns.filter { it.counts() && it.type == "DEBIT" && p.contains(it.timestamp) && (category == null || it.categoryKey == category) }
            .groupBy { it.merchantKey ?: it.merchant ?: it.vpa ?: "?" }
            .map { (_, l) -> MerchantAmount(l.first().merchant ?: l.first().vpa ?: "Unknown", l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.amount }
            .take(limit)

    /** Spend per day of [month] (index 0 = the 1st). */
    fun daily(txns: List<TransactionEntity>, month: YearMonth, zone: ZoneId, category: String? = null): LongArray {
        val out = LongArray(month.lengthOfMonth())
        for (t in txns) {
            if (!t.counts() || t.type != "DEBIT" || (category != null && t.categoryKey != category)) continue
            val d = Instant.ofEpochMilli(t.timestamp).atZone(zone)
            if (YearMonth.from(d) == month) out[d.dayOfMonth - 1] += t.amountPaise
        }
        return out
    }

    /** Running total: element i = everything spent up to and including day i + 1. */
    fun cumulative(daily: LongArray, days: Int = daily.size): LongArray {
        val out = LongArray(days.coerceIn(0, daily.size))
        var sum = 0L
        for (i in out.indices) {
            sum += daily[i]
            out[i] = sum
        }
        return out
    }

    /** Total spend for each month in [months]. */
    fun monthly(txns: List<TransactionEntity>, months: List<YearMonth>, zone: ZoneId, category: String? = null): LongArray {
        val index = months.withIndex().associate { (i, m) -> m to i }
        val out = LongArray(months.size)
        for (t in txns) {
            if (!t.counts() || t.type != "DEBIT" || (category != null && t.categoryKey != category)) continue
            val i = index[YearMonth.from(Instant.ofEpochMilli(t.timestamp).atZone(zone))] ?: continue
            out[i] += t.amountPaise
        }
        return out
    }

    /** The 6 months ending with [month] (or with this month, when [month] is one of the last 6). */
    fun sixMonths(month: YearMonth, now: YearMonth): List<YearMonth> {
        val end = if (month.isAfter(now.minusMonths(6))) now else month
        return (5 downTo 0).map { end.minusMonths(it.toLong()) }
    }

    /** Average of the months that have any spending (so a brand-new install isn't dragged down by empty months). */
    fun averageOfNonZero(values: LongArray): Long {
        val nz = values.filter { it > 0 }
        return if (nz.isEmpty()) 0 else nz.sum() / nz.size
    }

    /** Paid once or twice a month (rent, SIPs, bills…), so they don't continue at a daily rate. */
    private val MONTHLY_LUMPS = setOf(
        Categories.RENT, Categories.INVESTMENTS, Categories.TRANSFERS, Categories.BILLS, Categories.SUBSCRIPTIONS, Categories.EDUCATION,
    )

    /** Everyday spending (food, groceries, travel…): what keeps going at roughly the same rate. */
    fun dayToDay(s: Snapshot): Long = s.byCategory.filter { it.key !in MONTHLY_LUMPS }.sumOf { it.amount }

    /**
     * [projected] = what's spent so far plus the everyday rate for the rest of the month. Big monthly
     * payments aren't extrapolated: a ₹20,000 rent on the 2nd would otherwise "project" to ₹3 lakh.
     */
    fun budget(limit: Long, spent: Long, dayToDay: Long, month: YearMonth, today: LocalDate): BudgetStatus {
        val current = YearMonth.from(today) == month
        val daysLeft = if (current) month.lengthOfMonth() - today.dayOfMonth + 1 else 0
        // Projecting from the first few days is mostly noise.
        val projected = if (current && today.dayOfMonth >= 5) spent + dayToDay * (month.lengthOfMonth() - today.dayOfMonth) / today.dayOfMonth else null
        return BudgetStatus(limit, spent, daysLeft, projected)
    }
}
