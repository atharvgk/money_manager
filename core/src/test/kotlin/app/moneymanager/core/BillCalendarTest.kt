package app.moneymanager.core

import app.moneymanager.core.analysis.BillCalendar
import app.moneymanager.core.analysis.BillStatus
import app.moneymanager.core.analysis.Cadence
import app.moneymanager.core.analysis.Charge
import app.moneymanager.core.analysis.RecurringPayment
import app.moneymanager.core.categorize.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class BillCalendarTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun recurring(key: String, amount: Long, last: Long, cadence: Cadence = Cadence.MONTHLY, active: Boolean = true) =
        RecurringPayment(key, key.replaceFirstChar { it.uppercase() }, Categories.SUBSCRIPTIONS, amount, cadence, 4, last, last, active, confirmed = true)

    @Test fun paid_due_and_not_seen_in_the_current_month() {
        val today = LocalDate.of(2026, 10, 11)
        val netflix = recurring("netflix", 64900, at(2026, 10, 3))   // paid on the 3rd → next on 3 Nov
        val rent = recurring("rent", 2500000, at(2026, 9, 5))         // expected 5 Oct, not seen
        val sip = recurring("sip", 500000, at(2026, 9, 20))           // due 20 Oct
        val charges = listOf(
            Charge("netflix", "Netflix", 64900, at(2026, 9, 3), Categories.SUBSCRIPTIONS),
            Charge("netflix", "Netflix", 64900, at(2026, 10, 3), Categories.SUBSCRIPTIONS),
            Charge("netflix", "Netflix", 19900, at(2026, 10, 7), Categories.SUBSCRIPTIONS), // different amount: not the bill
        )
        val entries = BillCalendar.month(YearMonth.of(2026, 10), listOf(netflix, rent, sip), charges, today, zone)
        assertEquals(
            listOf(
                Triple(3, "netflix", BillStatus.PAID),
                Triple(5, "rent", BillStatus.NOT_SEEN),
                Triple(20, "sip", BillStatus.DUE),
            ),
            entries.map { Triple(it.date.dayOfMonth, it.payment.merchantKey, it.status) },
        )
        assertEquals(64900, entries.first().amountPaise)
    }

    @Test fun past_month_shows_what_was_paid_and_inactive_ones_are_not_expected() {
        val old = recurring("gym", 150000, at(2026, 6, 10), active = false)
        val charges = listOf(Charge("gym", "Gym", 150000, at(2026, 6, 10), Categories.HEALTH))
        val june = BillCalendar.month(YearMonth.of(2026, 6), listOf(old), charges, LocalDate.of(2026, 10, 11), zone)
        assertEquals(listOf(BillStatus.PAID), june.map { it.status })
        assertTrue(BillCalendar.month(YearMonth.of(2026, 7), listOf(old), charges, LocalDate.of(2026, 10, 11), zone).isEmpty())
    }

    @Test fun due_dates_step_from_the_last_payment() {
        val r = recurring("emi", 100000, at(2026, 1, 31))
        val dates = BillCalendar.expectedDates(r, zone, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30))
        assertEquals(listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), dates)
        assertEquals(LocalDate.of(2026, 3, 31), BillCalendar.nextDue(r, zone, LocalDate.of(2026, 3, 1)))
        val weekly = recurring("milk", 30000, at(2026, 10, 6), Cadence.WEEKLY)
        assertEquals(4, BillCalendar.expectedDates(weekly, zone, LocalDate.of(2026, 10, 7), LocalDate.of(2026, 11, 3)).size)
    }

    @Test fun upcoming_lists_the_next_two_weeks_soonest_first() {
        val today = LocalDate.of(2026, 10, 11)
        val soon = recurring("airtel", 59900, at(2026, 9, 14))
        val later = recurring("netflix", 64900, at(2026, 9, 12))
        val far = recurring("rent", 2500000, at(2026, 9, 30))
        val up = BillCalendar.upcoming(listOf(soon, later, far), today, zone)
        assertEquals(listOf("netflix", "airtel"), up.map { it.payment.merchantKey })
    }
}
