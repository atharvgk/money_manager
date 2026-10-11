package app.moneymanager.core

import app.moneymanager.core.categorize.Categories
import app.moneymanager.core.categorize.Categorizer
import app.moneymanager.core.categorize.KeywordRule
import app.moneymanager.core.categorize.SeedRules
import app.moneymanager.core.model.TxnHint
import app.moneymanager.core.model.TxnType.CREDIT
import app.moneymanager.core.model.TxnType.DEBIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizerTest {
    private val seeds = SeedRules.all.map { (k, c) -> KeywordRule(k, c, isUser = false) }
    private val c = Categorizer(seeds)

    private fun cat(merchant: String?, vpa: String? = null, hints: Set<TxnHint> = emptySet()) =
        c.categorize(DEBIT, merchant, vpa, hints).categoryKey

    @Test fun seeded_merchants() {
        assertEquals(Categories.FOOD, cat("Swiggy"))
        assertEquals(Categories.FOOD, cat(null, "zomato-order@ptybl"))
        assertEquals(Categories.GROCERIES, cat("Blinkit"))
        assertEquals(Categories.GROCERIES, cat("Swiggy Instamart"))
        assertEquals(Categories.GROCERIES, cat("Zepto Marketplace"))
        assertEquals(Categories.SHOPPING, cat("Amazon Pay India"))
        assertEquals(Categories.SUBSCRIPTIONS, cat("Amazon Prime"))
        assertEquals(Categories.SUBSCRIPTIONS, cat("Netflix"))
        assertEquals(Categories.TRANSPORT, cat("Uber India"))
        assertEquals(Categories.TRANSPORT, cat("OLA"))
        assertEquals(Categories.TRANSPORT, cat("Rapido"))
        assertEquals(Categories.TRAVEL, cat("IRCTC"))
        assertEquals(Categories.BILLS, cat("Reliance Jio"))
        assertEquals(Categories.BILLS, cat("BESCOM"))
        assertEquals(Categories.BILLS, cat("Airtel"))
        assertEquals(Categories.INVESTMENTS, cat("Zerodha Broking"))
        assertEquals(Categories.INVESTMENTS, cat("Groww"))
        assertEquals(Categories.FUEL, cat("Bharat Petroleum"))
        assertEquals(Categories.HEALTH, cat("Apollo Pharmacy"))
        assertEquals(Categories.ENTERTAINMENT, cat("BookMyShow"))
    }

    @Test fun short_keywords_need_whole_words() {
        assertEquals(Categories.OTHER, cat("Motorola Store India"))
        assertEquals(Categories.GROCERIES, cat("JioMart"))
        assertEquals(Categories.BILLS, cat("JIO Prepaid"))
    }

    @Test fun personal_vpa_is_transfer() {
        assertEquals(Categories.TRANSFERS, cat(null, "9876543210@ybl"))
        assertEquals(Categories.TRANSFERS, cat("John Doe", "john.doe@okaxis"))
        assertEquals(Categories.TRANSFERS, cat("Rahul Kumar Sharma"))
        assertEquals(Categories.TRANSFERS, cat("John Doe", hints = setOf(TxnHint.P2P)))
        assertEquals(Categories.GROCERIES, cat("Sharma General Stores"))
        assertEquals(Categories.OTHER, cat("Sharma Enterprises"))
        assertEquals(Categories.OTHER, cat(null, "paytmqr2810050501011ojbhgz8z@paytm"))
        assertEquals(Categories.OTHER, cat("Ramesh Kumar", hints = setOf(TxnHint.P2M)))
        assertEquals(Categories.GROCERIES, cat("Ramesh Kirana", hints = setOf(TxnHint.P2M)))
    }

    @Test fun atm_and_credits() {
        assertEquals(Categories.ATM_CASH, cat("ATM", hints = setOf(TxnHint.ATM)))
        assertEquals(Categories.INCOME, c.categorize(CREDIT, "Acme Corp", null, setOf(TxnHint.SALARY)).categoryKey)
        assertEquals(Categories.REFUNDS, c.categorize(CREDIT, "Amazon", null, setOf(TxnHint.REFUND)).categoryKey)
    }

    @Test fun credit_card_bill_payment_is_auto_excluded() {
        val r = c.categorize(CREDIT, null, null, setOf(TxnHint.CC_BILL_PAYMENT))
        assertTrue(r.autoExclude)
    }

    @Test fun user_rules_beat_seeds() {
        val withUser = Categorizer(seeds + KeywordRule("swiggy", Categories.GROCERIES, isUser = true))
        assertEquals(Categories.GROCERIES, withUser.categorize(DEBIT, "Swiggy", null, emptySet()).categoryKey)
        val person = Categorizer(seeds + KeywordRule("rahul kumar sharma", Categories.RENT, isUser = true))
        assertEquals(Categories.RENT, person.categorize(DEBIT, "Rahul Kumar Sharma", null, emptySet()).categoryKey)
    }

    @Test fun seed_keywords_are_unique() {
        val dupes = SeedRules.all.groupBy { Categorizer.normalize(it.first) }.filter { it.value.size > 1 }
        assertTrue("Duplicate seed keywords: ${dupes.keys}", dupes.isEmpty())
    }
}
