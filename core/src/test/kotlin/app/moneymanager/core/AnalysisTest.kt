package app.moneymanager.core

import app.moneymanager.core.analysis.Cadence
import app.moneymanager.core.analysis.Charge
import app.moneymanager.core.analysis.Dedupe
import app.moneymanager.core.analysis.RecurringDetector
import app.moneymanager.core.analysis.Summaries
import app.moneymanager.core.analysis.TxnRecord
import app.moneymanager.core.categorize.Categories
import app.moneymanager.core.format.Amounts
import app.moneymanager.core.format.Inr
import app.moneymanager.core.model.TxnType.CREDIT
import app.moneymanager.core.model.TxnType.DEBIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class AnalysisTest {
    private val min = 60_000L

    @Test fun dedupe_by_reference() {
        val a = Dedupe.Key(DEBIT, 45000, T.ts, "1234", "527812345678")
        val b = Dedupe.Key(DEBIT, 45000, T.ts + 9 * min, null, "527812345678")
        assertTrue(Dedupe.isSameTransaction(a, b))
        assertFalse(Dedupe.isSameTransaction(a, b.copy(reference = "527812345699")))
        assertFalse("direction matters", Dedupe.isSameTransaction(a, b.copy(type = CREDIT)))
    }

    @Test fun dedupe_by_time_window_and_account() {
        val bank = Dedupe.Key(DEBIT, 123450, T.ts, "5678", null)
        assertTrue(Dedupe.isSameTransaction(bank, bank.copy(timestamp = T.ts + 90_000, reference = "527812345678")))
        assertFalse("outside 2 minutes", Dedupe.isSameTransaction(bank, bank.copy(timestamp = T.ts + 3 * min)))
        assertFalse("different account", Dedupe.isSameTransaction(bank, bank.copy(account = "1111")))
        assertTrue("one side has no account", Dedupe.isSameTransaction(bank, bank.copy(account = null)))
        assertTrue("3 vs 4 digit masks", Dedupe.isSameTransaction(bank.copy(account = "123"), bank.copy(account = "0123")))
    }

    @Test fun same_payment_via_bank_and_upi_sms_dedupes() {
        val bankSms = T.txn("VM-HDFCBK", "Rs.450.00 debited from a/c **1234 on 05-10-25 to VPA swiggy@axisbank(UPI Ref No 527812345678).")
        val upiSms = T.txn("VM-HDFCBK", "Sent Rs.450.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 05/10/25\nRef 527812345678", T.ts + 20_000)
        fun key(t: app.moneymanager.core.model.ParsedTxn) = Dedupe.Key(t.type, t.amountPaise, t.timestamp, t.account, t.reference)
        assertTrue(Dedupe.isSameTransaction(key(bankSms), key(upiSms)))
    }

    @Test fun monthly_subscription_detected() {
        val day = 24 * 3600 * 1000L
        val start = T.at(2025, 5, 3)
        val charges = (0 until 5).map { Charge("netflix", "Netflix", 64900, start + it * 30 * day, Categories.SUBSCRIPTIONS) }
        val r = RecurringDetector.detect(charges, now = start + 4 * 30 * day + 5 * day)
        assertEquals(1, r.size)
        assertEquals(Cadence.MONTHLY, r[0].cadence)
        assertEquals(64900, r[0].typicalAmountPaise)
        assertTrue(r[0].active)
    }

    @Test fun random_food_orders_are_not_subscriptions() {
        val day = 24 * 3600 * 1000L
        val start = T.at(2025, 5, 1)
        val amounts = listOf(45000L, 23000, 61000, 19900, 38000, 52000, 27000)
        val charges = amounts.mapIndexed { i, a -> Charge("swiggy", "Swiggy", a, start + i * 4 * day, Categories.FOOD) }
        assertTrue(RecurringDetector.detect(charges, start + 40 * day).isEmpty())
    }

    @Test fun same_day_each_month_food_orders_are_not_subscriptions() {
        val day = 24 * 3600 * 1000L
        val s = T.at(2025, 7, 9)
        // A Swiggy order around the 9th every month (amounts vary), plus other Swiggy orders in between.
        val orders = listOf(0 to 49000L, 31 to 53800L, 61 to 58600L, 3 to 32700L, 34 to 37500L, 64 to 42300L)
            .map { (d, a) -> Charge("swiggy", "Swiggy", a, s + d * day, Categories.FOOD) }
        assertTrue(RecurringDetector.detect(orders, s + 70 * day).isEmpty())
        // A fixed-price monthly grocery subscription is still recurring.
        val milk = (0 until 4).map { Charge("country delight", "Country Delight", 120000, s + it * 30 * day, Categories.GROCERIES) }
        assertEquals(Cadence.MONTHLY, RecurringDetector.detect(milk, s + 100 * day).single().cadence)
    }

    @Test fun yearly_and_skipped_month_patterns() {
        val day = 24 * 3600 * 1000L
        val s = T.at(2024, 9, 10)
        val yearly = listOf(Charge("prime", "Amazon Prime", 149900, s, Categories.SUBSCRIPTIONS), Charge("prime", "Amazon Prime", 149900, s + 365 * day, Categories.SUBSCRIPTIONS))
        assertEquals(Cadence.YEARLY, RecurringDetector.detect(yearly, s + 370 * day).single().cadence)
        val rent = listOf(0, 31, 61, 122, 153).map { Charge("landlord", "Landlord", 2500000, s + it * day, Categories.RENT) }
        assertEquals(Cadence.MONTHLY, RecurringDetector.detect(rent, s + 160 * day).single().cadence)
    }

    @Test fun month_summary_and_change() {
        val oct = YearMonth.of(2025, 10)
        val txns = listOf(
            TxnRecord(DEBIT, 45000, T.at(2025, 10, 5), Categories.FOOD, "Swiggy", "swiggy", false),
            TxnRecord(DEBIT, 30000, T.at(2025, 10, 6), Categories.FOOD, "Swiggy", "swiggy", false),
            TxnRecord(DEBIT, 99900, T.at(2025, 10, 6), Categories.SHOPPING, "Amazon", "amazon", false),
            TxnRecord(DEBIT, 500000, T.at(2025, 10, 7), Categories.OTHER, null, null, true),
            TxnRecord(CREDIT, 8500000, T.at(2025, 10, 1), Categories.INCOME, "Acme", "acme", false),
            TxnRecord(DEBIT, 10000, T.at(2025, 9, 30), Categories.FOOD, "Zomato", "zomato", false),
        )
        val s = Summaries.month(txns, oct, T.zone)
        assertEquals(174900, s.spentPaise)
        assertEquals(8500000, s.incomePaise)
        assertEquals(Categories.SHOPPING, s.byCategory.first().categoryKey)
        assertEquals(75000, s.byCategory.first { it.categoryKey == Categories.FOOD }.amountPaise)
        assertEquals(129900, s.dailySpend[5])
        assertEquals("swiggy", s.topMerchants[1].merchantKey)
        val ch = Summaries.change(150000, 100000)
        assertEquals(50000, ch.deltaPaise)
        assertEquals(50.0, ch.percent!!, 0.001)
        assertNull(Summaries.change(100, 0).percent)
    }

    @Test fun indian_number_formatting() {
        assertEquals("₹0", Inr.format(0))
        assertEquals("₹450", Inr.format(45000))
        assertEquals("₹450.50", Inr.format(45050))
        assertEquals("₹1,000", Inr.format(100000))
        assertEquals("₹1,23,456", Inr.format(12345600))
        assertEquals("₹12,34,56,789.05", Inr.format(123456789_05))
        assertEquals("−₹2,500", Inr.format(-250000))
        assertEquals("+₹1,000", Inr.formatSigned(100000))
        assertEquals("₹950", Inr.compact(95000))
        assertEquals("₹1.2K", Inr.compact(123400))
        assertEquals("₹45K", Inr.compact(4512300))
        assertEquals("₹1.2L", Inr.compact(12345600))
        assertEquals("₹3.4Cr", Inr.compact(3400000000))
        assertEquals("₹34Cr", Inr.compact(34000000000))
    }

    @Test fun amount_parsing() {
        assertEquals(12345678L, Amounts.parseToPaise("1,23,456.78"))
        assertEquals(45000L, Amounts.parseToPaise("450.0"))
        assertEquals(45000L, Amounts.parseToPaise("450"))
        assertEquals(45000L, Amounts.parseToPaise("450.00/-"))
        assertNull(Amounts.parseToPaise("4.5.0"))
    }
}
