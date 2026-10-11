package app.moneymanager

import app.moneymanager.data.db.TransactionEntity
import app.moneymanager.ui.common.AccountScope
import app.moneymanager.ui.common.CASH_KEY
import app.moneymanager.ui.common.CardBook
import app.moneymanager.ui.common.SpendMath
import app.moneymanager.ui.common.accountChips
import app.moneymanager.ui.common.matchesAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SpendMathTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun txn(
        ts: Long,
        paise: Long,
        cat: String,
        type: String = "DEBIT",
        kind: String? = "BANK",
        account: String? = "1234",
        excluded: Boolean = false,
        source: String = "SMS",
        bank: String? = "HDFC Bank",
    ) = TransactionEntity(
        type = type, amountPaise = paise, timestamp = ts, accountKind = kind, account = account, bank = bank,
        categoryKey = cat, source = source, excluded = excluded,
    )

    @Test fun month_in_progress_is_compared_with_the_same_days_of_last_month() {
        val p = SpendMath.period(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 11), zone)
        val c = SpendMath.comparison(p, zone)
        assertTrue(p.partial)
        assertEquals("1–11 Oct", p.label)
        assertEquals("1–11 Sep", c.shortLabel)
        val txns = listOf(
            txn(at(2026, 10, 3), 50000, "food"),
            txn(at(2026, 9, 5), 20000, "food"),
            txn(at(2026, 9, 25), 99900, "shopping"), // after 11 Sep: not part of the comparison
            txn(at(2026, 10, 4), 10000, "food", excluded = true),
            txn(at(2026, 10, 6), 8500000, "income", type = "CREDIT"),
        )
        val cur = SpendMath.snapshot(txns, p)
        val prev = SpendMath.snapshot(txns, c)
        assertEquals(50000, cur.spent)
        assertEquals(8500000, cur.received)
        assertEquals(20000, prev.spent)
        val change = SpendMath.changes(cur, prev).single()
        assertEquals("food", change.key)
        assertEquals(30000, change.delta)
    }

    @Test fun past_months_are_whole_and_short_months_clamp() {
        val p = SpendMath.period(YearMonth.of(2026, 3), LocalDate.of(2026, 10, 11), zone)
        assertFalse(p.partial)
        assertEquals(31, p.lastDay)
        // 31 March 2028 compares with 1–29 Feb (leap year).
        assertEquals(29, SpendMath.comparison(SpendMath.period(YearMonth.of(2028, 3), LocalDate.of(2028, 3, 31), zone), zone).lastDay)
    }

    @Test fun bank_and_card_scopes_use_every_sms_for_a_card_number() {
        val credit = txn(0, 100, "food", kind = "CREDIT_CARD", account = "5678")
        val sameCardUnlabelled = txn(0, 100, "food", kind = "CARD", account = "5678")
        val debitCard = txn(0, 100, "food", kind = "DEBIT_CARD", account = "9999")
        val otherCard = txn(0, 100, "food", kind = "CARD", account = "4321")
        val bank = txn(0, 100, "food", kind = "BANK")
        val cash = txn(0, 100, "food", kind = null, account = null, source = "MANUAL", bank = "Cash")
        val book = CardBook(listOf("5678"))
        assertTrue(book.inScope(credit, AccountScope.CARDS))
        assertTrue(book.inScope(sameCardUnlabelled, AccountScope.CARDS))
        assertTrue(book.inScope(debitCard, AccountScope.BANK))
        assertTrue(book.inScope(otherCard, AccountScope.BANK))
        assertTrue(book.inScope(bank, AccountScope.BANK))
        assertTrue(book.inScope(cash, AccountScope.BANK))
        assertFalse(book.inScope(credit, AccountScope.BANK))
        assertTrue(listOf(credit, bank, cash).all { book.inScope(it, AccountScope.ALL) })
        assertTrue(cash.matchesAccount(CASH_KEY))
        assertFalse(bank.matchesAccount("5678"))
    }

    @Test fun account_chips_are_short_and_busiest_first() {
        val book = CardBook(listOf("5678"))
        val chips = accountChips(
            listOf(
                txn(0, 1, "food"), txn(0, 1, "food"), txn(0, 1, "food"),
                txn(0, 1, "food", kind = "CREDIT_CARD", account = "5678", bank = null),
                txn(0, 1, "food", kind = null, account = null, source = "MANUAL", bank = "Cash"),
                txn(0, 1, "food", account = null), // no account number: counts in "All" only
            ),
            book,
        )
        assertEquals(listOf("HDFC ••1234", "Card ••5678", "Cash"), chips.map { it.label })
        assertEquals(3, chips.first().count)
    }

    @Test fun daily_cumulative_and_monthly_series() {
        val txns = listOf(txn(at(2026, 10, 2), 1000, "food"), txn(at(2026, 10, 2), 500, "food"), txn(at(2026, 10, 4), 200, "food"), txn(at(2026, 8, 20), 700, "food"))
        val daily = SpendMath.daily(txns, YearMonth.of(2026, 10), zone)
        assertEquals(1500, daily[1])
        assertEquals(listOf(0L, 1500L, 1500L, 1700L), SpendMath.cumulative(daily, 4).toList())
        val months = listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10))
        val monthly = SpendMath.monthly(txns, months, zone)
        assertEquals(listOf(700L, 0L, 1700L), monthly.toList())
        assertEquals(1200L, SpendMath.averageOfNonZero(monthly))
    }

    @Test fun six_month_window_ends_now_unless_looking_further_back() {
        val now = YearMonth.of(2026, 10)
        assertEquals(YearMonth.of(2026, 5), SpendMath.sixMonths(YearMonth.of(2026, 8), now).first())
        assertEquals(now, SpendMath.sixMonths(YearMonth.of(2026, 8), now).last())
        assertEquals(YearMonth.of(2026, 1), SpendMath.sixMonths(YearMonth.of(2026, 1), now).last())
    }

    @Test fun budget_left_per_day_and_projection() {
        // ₹11,000 spent by the 11th, of which ₹5,500 everyday spending (the rest is rent/SIP-type lumps).
        val b = SpendMath.budget(limit = 30_000_00, spent = 11_000_00, dayToDay = 5_500_00, month = YearMonth.of(2026, 10), today = LocalDate.of(2026, 10, 11))
        assertEquals(21, b.daysLeft) // 11th..31st
        assertEquals(19_000_00L, b.left)
        assertEquals(19_000_00L / 21, b.perDay)
        assertEquals(21_000_00L, b.projected) // 11,000 + 500 a day × 20 more days
        assertFalse(b.over)
        val early = SpendMath.budget(30_000_00, 1_000_00, 1_000_00, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 2))
        assertNull(early.projected)
        val past = SpendMath.budget(30_000_00, 31_000_00, 0, YearMonth.of(2026, 9), LocalDate.of(2026, 10, 11))
        assertTrue(past.over)
        assertEquals(0, past.perDay)
    }

    @Test fun day_to_day_spending_leaves_out_monthly_lumps() {
        val p = SpendMath.period(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 11), zone)
        val s = SpendMath.snapshot(
            listOf(txn(at(2026, 10, 2), 20_000_00, "rent"), txn(at(2026, 10, 3), 5_000_00, "investments"), txn(at(2026, 10, 4), 450_00, "food")),
            p,
        )
        assertEquals(450_00L, SpendMath.dayToDay(s))
    }
}
