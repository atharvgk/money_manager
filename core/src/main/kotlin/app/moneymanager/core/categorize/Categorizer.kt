package app.moneymanager.core.categorize

import app.moneymanager.core.model.TxnHint
import app.moneymanager.core.model.TxnType
import app.moneymanager.core.parse.Payees

data class KeywordRule(val keyword: String, val categoryKey: String, val isUser: Boolean)

data class Categorization(val categoryKey: String, val autoExclude: Boolean = false, val matchedKeyword: String? = null)

/**
 * Decides a category. Order: credit-card bill payments (auto-excluded) → your own rules → credits
 * (refund/income) → ATM → seeded merchant keywords → person-to-person heuristic → Other.
 */
class Categorizer(rules: List<KeywordRule>) {
    private val userRules = rules.filter { it.isUser }.map { it.copy(keyword = normalize(it.keyword)) }.filter { it.keyword.isNotEmpty() }
    private val seedRules = rules.filter { !it.isUser }.map { it.copy(keyword = normalize(it.keyword)) }.filter { it.keyword.isNotEmpty() }

    fun categorize(type: TxnType, merchant: String?, vpa: String?, hints: Set<TxnHint>): Categorization {
        if (TxnHint.CC_BILL_PAYMENT in hints) return Categorization(Categories.OTHER, autoExclude = true)
        val text = normalize(listOfNotNull(merchant, vpa).joinToString(" "))
        if (text.isNotEmpty()) best(userRules, text)?.let { return Categorization(it.categoryKey, matchedKeyword = it.keyword) }
        if (type == TxnType.CREDIT) {
            return if (TxnHint.REFUND in hints || TxnHint.CASHBACK in hints) Categorization(Categories.REFUNDS)
            else Categorization(Categories.INCOME)
        }
        if (TxnHint.ATM in hints) return Categorization(Categories.ATM_CASH)
        if (text.isNotEmpty()) best(seedRules, text)?.let { return Categorization(it.categoryKey, matchedKeyword = it.keyword) }
        if (Payees.looksPersonal(merchant, vpa, TxnHint.P2P in hints, TxnHint.P2M in hints)) return Categorization(Categories.TRANSFERS)
        return Categorization(Categories.OTHER)
    }

    /** The user rule that would apply to this merchant, if any. */
    fun userRuleFor(merchant: String?, vpa: String?): KeywordRule? {
        val text = normalize(listOfNotNull(merchant, vpa).joinToString(" "))
        return if (text.isEmpty()) null else best(userRules, text)
    }

    private fun best(rules: List<KeywordRule>, text: String): KeywordRule? =
        rules.filter { matches(text, it.keyword) }.maxByOrNull { it.keyword.length }

    companion object {
        private val NON_KEY = Regex("""[^a-z0-9@&.]+""")

        fun normalize(s: String): String = s.lowercase().replace(NON_KEY, " ").trim()

        /** Keyword must start at a word start; short keywords (≤4 chars) must also end at a word end. */
        fun matches(text: String, keyword: String): Boolean {
            var idx = text.indexOf(keyword)
            while (idx >= 0) {
                val startOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
                val end = idx + keyword.length
                val endOk = keyword.length > 4 || end == text.length || !text[end].isLetterOrDigit()
                if (startOk && endOk) return true
                idx = text.indexOf(keyword, idx + 1)
            }
            return false
        }
    }
}
