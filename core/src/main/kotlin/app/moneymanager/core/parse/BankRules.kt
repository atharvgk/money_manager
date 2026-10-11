package app.moneymanager.core.parse

import app.moneymanager.core.model.Channel
import app.moneymanager.core.model.TxnHint
import app.moneymanager.core.model.TxnType
import app.moneymanager.core.parse.Frag.CUR
import app.moneymanager.core.parse.Frag.NUM

/**
 * One bank SMS format.
 *
 * [pattern] is matched (find, not full match) against the normalised SMS text. Supported named groups:
 *   amount (required), account, merchant, vpa, ref, balance.
 * Anything a rule doesn't capture is filled in by the generic extractors, so a rule only needs to
 * pin down what the generic code would get wrong (usually the merchant).
 *
 * To add a format: append a rule to [BankRules.rules] and add a test case to BankRulesTest.
 */
class SmsRule(
    val id: String,
    pattern: String,
    /** Fixed direction; null = decide from the first debit/credit verb. */
    val type: TxnType? = null,
    /** Only try this rule when the sender header matches (e.g. "HDFC"). Null = any sender. */
    sender: String? = null,
    val channel: Channel? = null,
    val hints: Set<TxnHint> = emptySet(),
) {
    val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val senderRegex = sender?.let { Regex(it, RegexOption.IGNORE_CASE) }
    val groupNames: Set<String> = Regex("""\(\?<([a-zA-Z][a-zA-Z0-9]*)>""").findAll(pattern).map { it.groupValues[1] }.toSet()

    fun group(m: MatchResult, name: String): String? =
        if (name in groupNames) m.groups[name]?.value?.trim()?.takeIf { it.isNotEmpty() } else null
}

object BankRules {
    private const val AMT = "$CUR(?<amount>$NUM)"
    private const val ACCT = """[x*#]*(?<account>\d{3,4})"""
    private const val DATE = """\d{1,2}[-/]\d{1,2}(?:[-/]\d{2,4})?"""

    val rules: List<SmsRule> = listOf(
        // ---------------- HDFC Bank
        // Sent Rs.450.00 / From HDFC Bank A/C *1234 / To SWIGGY / On 05/10/25 / Ref 527812345678
        SmsRule(
            "hdfc_upi_sent",
            """^(?:amt\s+)?sent\s+$AMT\s+from\s+[^\n]*?a/c\s*$ACCT\s+to\s+(?<merchant>[^\n]+?)\s+on\s+$DATE(?:\s+ref\s+(?<ref>\d{6,}))?""",
            type = TxnType.DEBIT, channel = Channel.UPI,
        ),
        // Spent Rs.1234.50 On HDFC Bank Card 5678 At AMAZON PAY INDIA On 2025-10-05:14:23:11 Bal Rs.45678.00
        SmsRule(
            "hdfc_card_spent",
            """\bspent\s+$AMT\s+on\s+[^\n]*?card\s*$ACCT\s+at\s+(?<merchant>.+?)\s+on\s+\d""",
            type = TxnType.DEBIT, channel = Channel.CARD,
        ),
        // Txn Rs.499.00 / On HDFC Bank Card 5678 / At NETFLIX / by UPI 527812345678 / On 05-10
        SmsRule(
            "hdfc_card_txn",
            """^txn\s+$AMT\s+on\s+[^\n]*?card\s*$ACCT\s+at\s+(?<merchant>[^\n]+?)\s+(?:by\s+upi\s+(?<ref>\d{6,})\s+)?on\s+\d""",
            type = TxnType.DEBIT, channel = Channel.CARD,
        ),
        // INR 25,000.00 deposited in HDFC Bank A/c XX1234 on 01-OCT-25 for NEFT Cr-ICIC0000123-ACME CORP-…
        SmsRule(
            "hdfc_neft_deposit",
            """$AMT\s+deposited\s+in\s+[^\n]*?a/c\s*$ACCT\s+on\s+\S+\s+for\s+(?:neft|imps|rtgs)\s*cr[-\s]+[a-z0-9]+-(?<merchant>[^-\n]+)""",
            type = TxnType.CREDIT,
        ),

        // ---------------- ICICI Bank
        // ICICI Bank Acct XX123 debited for Rs 450.00 on 05-Oct-25; SWIGGY credited. UPI:527812345678.
        SmsRule(
            "icici_upi_debit",
            """\bacct\s*$ACCT\s+debited\s+(?:for|with)\s+$AMT\s+on\s+[^;\n]+;\s*(?<merchant>[^;\n]+?)\s+credited""",
            type = TxnType.DEBIT,
        ),
        // INR 1,234.00 spent using ICICI Bank Card XX5678 on 05-Oct-25 on AMAZON. Avl Limit: …
        SmsRule(
            "icici_card_spent",
            """$AMT\s+spent\s+(?:using|on)\s+[^\n]*?card\s*$ACCT\s+on\s+\S+\s+(?:on|at)\s+(?<merchant>.+?)(?:\.\s|\.$|\s+avl\b|$)""",
            type = TxnType.DEBIT, channel = Channel.CARD,
        ),
        // ICICI Bank Credit Card XX5678 has been used for a transaction of INR 499.00 on Oct 05, 2025 at 10:20:30. Info: NETFLIX.
        SmsRule(
            "icici_card_used",
            """card\s*$ACCT\s+has\s+been\s+used\s+for\s+a\s+transaction\s+of\s+$AMT.*?\binfo\s*[:\-]?\s*(?<merchant>[^.\n]+)""",
            type = TxnType.DEBIT, channel = Channel.CARD,
        ),

        // ---------------- Axis Bank
        // Spent / Card no. XX5678 / INR 1234.00 / 05-10-25 14:23:11 / AMAZON / Avl Lmt INR 98,766.00
        SmsRule(
            "axis_card_spent",
            """^spent\s+card\s+no\.?\s*$ACCT\s+$AMT\s+\d{1,2}-\d{1,2}-\d{2,4}[,\s]+\d{1,2}:\d{2}(?::\d{2})?\s+(?:ist\s+)?(?<merchant>[^\n]+?)(?=\s*\n|\s+avl\b|\s*$)""",
            type = TxnType.DEBIT, channel = Channel.CARD,
        ),
        // INR 450.00 debited / A/c no. XX1234 / 05-10-25, 14:23:11 / UPI/P2M/527812345678/SWIGGY
        SmsRule(
            "axis_upi",
            """^(?:(?:debit|credit)\s+)?$AMT(?:\s+(?:debited|credited))?\s+a/c\s+no\.?\s*$ACCT.*?\bupi/(?:p2[am]/)?(?<ref>\d{6,})/(?<merchant>[^/\n]+?)(?=\s*\n|\s+not\s+you|\s*$)""",
            channel = Channel.UPI,
        ),

        // ---------------- SBI
        // Dear UPI user A/C X1234 debited by 450.0 on date 05Oct25 trf to SWIGGY Refno 527812345678.
        SmsRule(
            "sbi_upi_debit",
            """a/c\s*$ACCT\s+debited\s+by\s+(?:$CUR)?(?<amount>$NUM)\s+on\s+(?:date\s+)?\S+\s+trf\s+to\s+(?<merchant>.+?)\s+ref\s*no\.?\s*(?<ref>\d{6,})""",
            type = TxnType.DEBIT, channel = Channel.UPI,
        ),
        // Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 05Oct25 by (Ref no 527812345678)
        // Dear SBI User, your A/c X1234-credited by Rs.500 on 05Oct25 transfer from JOHN DOE Ref No 527812345678
        SmsRule(
            "sbi_upi_credit",
            """a/c\s*$ACCT[\s\-]*credited\s+by\s+(?:$CUR)?(?<amount>$NUM)\s+on\s+(?:date\s+)?\S+\s+(?:by\s+|transfer\s+from\s+)?(?<merchant>[^(\n]*?)\s*\(?\s*ref\s*no\.?\s*(?<ref>\d{6,})""",
            type = TxnType.CREDIT, channel = Channel.UPI,
        ),
        // Rs.1,234.00 spent on your SBI Credit Card ending 5678 at AMAZON on 05/10/25.
        SmsRule(
            "card_spent_at",
            """$AMT\s+spent\s+on\s+(?:your\s+)?[^\n]*?card\s+(?:ending\s+(?:with\s+)?)?$ACCT\s+at\s+(?<merchant>.+?)\s+on\s+\d""",
            type = TxnType.DEBIT, channel = Channel.CARD,
        ),

        // ---------------- Kotak
        // Sent Rs.450.00 from Kotak Bank AC X1234 to swiggy@ybl on 05-10-25.UPI Ref 527812345678.
        SmsRule(
            "kotak_upi_sent",
            """^sent\s+$AMT\s+from\s+kotak[^\n]*?\bac\s*$ACCT\s+to\s+(?<vpa>[a-z0-9.\-_]+@[a-z0-9]+)\s+on\s+$DATE\.?\s*upi\s*ref[:\s]*(?<ref>\d{6,})""",
            type = TxnType.DEBIT, channel = Channel.UPI,
        ),
        // Received Rs.1000.00 in your Kotak Bank AC X1234 from john@okaxis on 05-10-25.UPI Ref:527812345678.
        SmsRule(
            "kotak_upi_received",
            """^received\s+$AMT\s+in\s+your\s+kotak[^\n]*?\bac\s*$ACCT\s+from\s+(?<vpa>[a-z0-9.\-_]+@[a-z0-9]+)\s+on\s+$DATE\.?\s*upi\s*ref[:\s]*(?<ref>\d{6,})""",
            type = TxnType.CREDIT, channel = Channel.UPI,
        ),

        // ---------------- Bank of Baroda
        // Rs.450.00 Dr. from A/C XXXXXX1234 and Cr. to swiggy@ybl. Ref:527812345678. AvlBal:Rs12345.00
        SmsRule(
            "bob_dr_cr",
            """$AMT\s+dr\.?\s+from\s+a/c\s*$ACCT\s+and\s+cr\.?\s+to\s+(?<merchant>[^\s]+?)\.?\s+ref""",
            type = TxnType.DEBIT,
        ),

        // ---------------- Credit card bill payments (card side). Auto-excluded to avoid double counting.
        // PAYMENT OF RS. 5000.00 RECEIVED TOWARDS YOUR HDFC BANK CREDIT CARD ENDING 5678
        // Payment of INR 10,000.00 has been received on your ICICI Bank Credit Card Account XX5678
        SmsRule(
            "cc_payment_received",
            """\bpayment\s+of\s+$AMT\s+(?:has\s+been\s+)?received\s+(?:towards|on|for|against)\s+(?:your\s+)?[^\n]*?credit\s*card""",
            type = TxnType.CREDIT, hints = setOf(TxnHint.CC_BILL_PAYMENT),
        ),
        // We have received payment of Rs.5,000 via BBPS on your SBI Credit Card ending 5678
        SmsRule(
            "cc_received_payment",
            """\breceived\s+(?:your\s+)?payment\s+of\s+$AMT[^\n]*?credit\s*card""",
            type = TxnType.CREDIT, hints = setOf(TxnHint.CC_BILL_PAYMENT),
        ),
    )
}
