package app.moneymanager.core.analysis

import app.moneymanager.core.categorize.Categories
import kotlin.math.abs
import kotlin.math.roundToLong

enum class Cadence(val days: Int, val label: String) {
    WEEKLY(7, "Weekly"),
    MONTHLY(30, "Monthly"),
    QUARTERLY(91, "Quarterly"),
    YEARLY(365, "Yearly"),
}

data class Charge(
    val merchantKey: String,
    val merchant: String,
    val amountPaise: Long,
    val timestamp: Long,
    val categoryKey: String,
)

data class RecurringPayment(
    val merchantKey: String,
    val merchant: String,
    val categoryKey: String,
    val typicalAmountPaise: Long,
    val cadence: Cadence,
    val count: Int,
    val lastTimestamp: Long,
    val nextExpectedTimestamp: Long,
    val active: Boolean,
    /** False for a single charge we only suspect is a subscription (from its category). */
    val confirmed: Boolean,
) {
    val monthlyCostPaise: Long
        get() = when (cadence) {
            Cadence.WEEKLY -> (typicalAmountPaise * 52 / 12.0).roundToLong()
            Cadence.MONTHLY -> typicalAmountPaise
            Cadence.QUARTERLY -> typicalAmountPaise / 3
            Cadence.YEARLY -> typicalAmountPaise / 12
        }
}

/** Finds subscriptions/EMIs/SIPs: the same merchant charging a similar amount at a steady interval. */
object RecurringDetector {
    private const val DAY = 24 * 3600 * 1000L
    private val RECURRING_CATEGORIES = setOf(
        Categories.SUBSCRIPTIONS, Categories.BILLS, Categories.RENT, Categories.EDUCATION, Categories.INVESTMENTS,
    )
    private val EVERYDAY_CATEGORIES = setOf(
        Categories.FOOD, Categories.GROCERIES, Categories.SHOPPING, Categories.TRANSPORT, Categories.FUEL,
        Categories.ENTERTAINMENT, Categories.TRAVEL, Categories.HEALTH, Categories.ATM_CASH,
    )

    fun detect(charges: List<Charge>, now: Long): List<RecurringPayment> {
        val out = ArrayList<RecurringPayment>()
        val usedKeys = HashSet<String>()
        for ((key, list) in charges.groupBy { it.merchantKey }) {
            for (cluster in clusterByAmount(list.sortedBy { it.timestamp })) {
                detectCluster(cluster, list.size, now)?.let {
                    out += it
                    usedKeys += key
                }
            }
        }
        // A single recent charge in "Subscriptions" is probably a subscription we haven't seen twice yet.
        charges.filter { it.categoryKey == Categories.SUBSCRIPTIONS && it.merchantKey !in usedKeys && now - it.timestamp <= 40 * DAY }
            .groupBy { it.merchantKey }
            .forEach { (_, list) ->
                val last = list.maxBy { it.timestamp }
                out += RecurringPayment(
                    last.merchantKey, last.merchant, last.categoryKey, last.amountPaise, Cadence.MONTHLY, list.size,
                    last.timestamp, last.timestamp + 30 * DAY, active = true, confirmed = false,
                )
            }
        return out.sortedWith(compareByDescending<RecurringPayment> { it.active }.thenByDescending { it.monthlyCostPaise })
    }

    /** Groups charges whose amounts are within ~15% (or ₹30) of each other. */
    private fun clusterByAmount(sorted: List<Charge>): List<List<Charge>> {
        val clusters = ArrayList<MutableList<Charge>>()
        for (c in sorted) {
            val target = clusters.firstOrNull { cl ->
                val ref = median(cl.map { it.amountPaise })
                abs(c.amountPaise - ref) <= maxOf(ref * 15 / 100, 3000L)
            }
            if (target != null) target += c else clusters += mutableListOf(c)
        }
        return clusters
    }

    private fun detectCluster(cluster: List<Charge>, merchantTotal: Int, now: Long): RecurringPayment? {
        if (cluster.size < 2) return null
        // Collapse same-day repeats (e.g. a retried payment).
        val charges = cluster.fold(ArrayList<Charge>()) { acc, c ->
            if (acc.isEmpty() || c.timestamp - acc.last().timestamp >= DAY) acc += c
            acc
        }
        if (charges.size < 2) return null
        val gaps = charges.zipWithNext { a, b -> (b.timestamp - a.timestamp).toDouble() / DAY }
        val medianGap = gaps.sorted()[gaps.size / 2]
        val cadence = when (medianGap) {
            in 6.0..8.5 -> Cadence.WEEKLY
            in 25.0..36.0 -> Cadence.MONTHLY
            in 84.0..98.0 -> Cadence.QUARTERLY
            in 350.0..380.0 -> Cadence.YEARLY
            else -> return null
        }
        val tolerance = when (cadence) {
            Cadence.WEEKLY -> 5.0..10.0
            Cadence.MONTHLY -> 22.0..40.0
            Cadence.QUARTERLY -> 80.0..100.0
            Cadence.YEARLY -> 340.0..390.0
        }
        // Allow one skipped period (e.g. 60 days for monthly) without breaking the pattern.
        val regular = gaps.count { g -> g in tolerance || (g / 2) in tolerance }
        if (regular < gaps.size * 0.7) return null
        val category = charges.groupingBy { it.categoryKey }.eachCount().maxBy { it.value }.key
        val minCount = if (category in RECURRING_CATEGORIES || cadence == Cadence.YEARLY || cadence == Cadence.QUARTERLY) 2 else 3
        if (charges.size < minCount) return null
        // Everyday spending (or a merchant you also pay at other times) can look monthly by coincidence, e.g. a
        // Swiggy order around the 9th each month. Real subscriptions charge (almost) the same amount every time.
        val busyMerchant = merchantTotal > charges.size * 1.5
        if (category in EVERYDAY_CATEGORIES || busyMerchant) {
            val amounts = charges.map { it.amountPaise }
            val spread = (amounts.max() - amounts.min()).toDouble() / median(amounts)
            if (charges.size < 3 || spread > 0.03) return null
        }
        val last = charges.last()
        val next = last.timestamp + cadence.days * DAY
        val active = now - last.timestamp <= (cadence.days * 1.5 + 5).toLong() * DAY
        return RecurringPayment(
            merchantKey = last.merchantKey,
            merchant = last.merchant,
            categoryKey = category,
            typicalAmountPaise = median(charges.takeLast(3).map { it.amountPaise }),
            cadence = cadence,
            count = charges.size,
            lastTimestamp = last.timestamp,
            nextExpectedTimestamp = next,
            active = active,
            confirmed = true,
        )
    }

    private fun median(values: List<Long>): Long {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
