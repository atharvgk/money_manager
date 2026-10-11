package app.moneymanager.core.analysis

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

enum class BillStatus {
    /** A matching payment was seen. */
    PAID,

    /** Expected today or later. */
    DUE,

    /** Expected earlier but no matching payment was seen (maybe paid from an account the app doesn't see). */
    NOT_SEEN,
}

data class BillEntry(val date: LocalDate, val payment: RecurringPayment, val status: BillStatus, val amountPaise: Long)

/** Places recurring payments on a calendar: paid ones on the day they were charged, expected ones on their due day. */
object BillCalendar {

    fun month(month: YearMonth, recurring: List<RecurringPayment>, charges: List<Charge>, today: LocalDate, zone: ZoneId): List<BillEntry> {
        val out = ArrayList<BillEntry>()
        val byMerchant = charges.groupBy { it.merchantKey }
        for (r in recurring) {
            val paid = byMerchant[r.merchantKey].orEmpty().filter { matches(r, it) }.map { it.timestamp.date(zone) }
            paid.filter { YearMonth.from(it) == month }.forEach { d ->
                val amount = byMerchant.getValue(r.merchantKey).first { matches(r, it) && it.timestamp.date(zone) == d }.amountPaise
                out += BillEntry(d, r, BillStatus.PAID, amount)
            }
            if (!r.active) continue
            for (due in expectedDates(r, zone, month.atDay(1), month.atEndOfMonth())) {
                out += BillEntry(due, r, if (due.isBefore(today)) BillStatus.NOT_SEEN else BillStatus.DUE, r.typicalAmountPaise)
            }
        }
        return out.sortedWith(compareBy<BillEntry> { it.date }.thenBy { it.payment.merchant })
    }

    /** Payments expected from today to [days] days ahead, soonest first. */
    fun upcoming(recurring: List<RecurringPayment>, today: LocalDate, zone: ZoneId, days: Long = 14): List<BillEntry> =
        recurring.filter { it.active }
            .flatMap { r -> expectedDates(r, zone, today, today.plusDays(days)).map { BillEntry(it, r, BillStatus.DUE, r.typicalAmountPaise) } }
            .sortedBy { it.date }

    /** The first due date from [today] on (the same date the calendar shows). */
    fun nextDue(r: RecurringPayment, zone: ZoneId, today: LocalDate): LocalDate? =
        expectedDates(r, zone, today, today.plusYears(1).plusDays(1)).firstOrNull()

    /** Due dates after the last payment that fall within [from]..[to]. */
    fun expectedDates(r: RecurringPayment, zone: ZoneId, from: LocalDate, to: LocalDate): List<LocalDate> {
        val last = r.lastTimestamp.date(zone)
        val out = ArrayList<LocalDate>()
        var k = 1L
        while (true) {
            // Always step from the last payment (not from the previous step) so 31 Jan → 28 Feb → 31 Mar.
            val d = when (r.cadence) {
                Cadence.WEEKLY -> last.plusWeeks(k)
                Cadence.MONTHLY -> last.plusMonths(k)
                Cadence.QUARTERLY -> last.plusMonths(3 * k)
                Cadence.YEARLY -> last.plusYears(k)
            }
            if (d.isAfter(to)) break
            if (!d.isBefore(from)) out += d
            k++
        }
        return out
    }

    private fun matches(r: RecurringPayment, c: Charge) =
        abs(c.amountPaise - r.typicalAmountPaise) <= maxOf(r.typicalAmountPaise * 15 / 100, 3000L)

    private fun Long.date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
