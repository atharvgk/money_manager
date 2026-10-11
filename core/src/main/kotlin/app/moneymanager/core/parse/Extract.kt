package app.moneymanager.core.parse

import app.moneymanager.core.format.Amounts
import app.moneymanager.core.model.AccountKind
import app.moneymanager.core.model.Channel
import app.moneymanager.core.model.TxnHint
import app.moneymanager.core.model.TxnType

private val IC = setOf(RegexOption.IGNORE_CASE)

/** Regex fragments shared by the extractors and by [BankRules]. */
object Frag {
    /** Currency marker: Rs, Rs., INR, ₹ (not the "rs" inside words like "hours"). */
    const val CUR = """(?:(?<![a-z])(?:rs|inr)\.?|₹)\s*[:.]?\s*"""
    const val NUM = """\d[\d,]*(?:\.\d{1,2})?"""
    /** Masked account digits: "XX1234", "*1234", "1234XXXX5678", "X1234", "123". */
    const val MASKED = """(?:\d{0,6}[x*#][x*#\s]*)?\d{3,18}"""
}

data class AccountRef(val digits: String, val kind: AccountKind, val index: Int)

internal object Extract {

    // ---------------------------------------------------------------- amounts

    private val AMOUNT = Regex(Frag.CUR + "(" + Frag.NUM + ")", IC)
    private val SKIP_CONTEXT = Regex(
        """(?:bal(?:ance)?|lim(?:it)?|lmt|avl|avbl|available|outstanding|o/s|due|limit\s+is)[\s.:\-]*(?:is|of|as\s+on)?[\s.:\-]*$""",
        IC,
    )
    private val VERB_NUMBER = Regex("""\b(?:debited|credited|debit|credit)\s+(?:by|with|for|of)\s+(""" + Frag.NUM + """)(?![\d/\-])""", IC)

    /** The transaction amount in paise: the first currency amount that isn't a balance/limit/due figure. */
    fun amount(text: String): Long? {
        for (m in AMOUNT.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 24), m.range.first)
            if (SKIP_CONTEXT.containsMatchIn(before)) continue
            val paise = Amounts.parseToPaise(m.groupValues[1]) ?: continue
            if (paise > 0) return paise
        }
        val v = VERB_NUMBER.find(text) ?: return null
        return Amounts.parseToPaise(v.groupValues[1])?.takeIf { it > 0 }
    }

    /** Any rupee figure at all, including balances, limits and dues. */
    fun hasAnyAmount(text: String) = AMOUNT.containsMatchIn(text) || VERB_NUMBER.containsMatchIn(text)

    private val BALANCE = Regex(
        """(?:(?:avl|avbl|avail|aval|available|clear|closing|ledger|new|updated|total\s+avail)\.?\s*|(?<![a-z]))bal(?:ance)?\b""" +
            """\s*(?:is|of)?\s*[:\-]?\s*(?:(?:rs|inr)\.?|₹)?\s*[:\-]?\s*(-?""" + Frag.NUM + ")",
        IC,
    )

    fun balance(text: String): Long? {
        val m = BALANCE.find(text) ?: return null
        val raw = m.groupValues[1]
        val neg = raw.startsWith("-")
        return Amounts.parseToPaise(raw.removePrefix("-"))?.let { if (neg) -it else it }
    }

    // ---------------------------------------------------------------- accounts

    private val ACCOUNT = Regex(
        """(?:\ba/c|\bacct|\baccount|\bac)\.?\s*(?:no\.?|number|num)?\s*[:\-.]?\s*(?:ending\s*(?:with|in)?\s*)?(""" + Frag.MASKED + """)\b""",
        IC,
    )
    private val CARD = Regex(
        """\bcard\b\s*(?:no\.?|number)?\s*(?:ending\s+with|ending\s+in|ending|ends\s+with|end\s+with)?\s*[:\-]?\s*(""" + Frag.MASKED + """)\b""",
        IC,
    )
    private val TRAILING_DIGITS = Regex("""\d+$""")

    private fun lastDigits(masked: String): String? {
        val d = TRAILING_DIGITS.find(masked.trim())?.value ?: return null
        return if (d.length > 4) d.takeLast(4) else d
    }

    /** All account/card references, in order of appearance. */
    fun accounts(text: String): List<AccountRef> {
        val lower = text.lowercase()
        val out = ArrayList<AccountRef>()
        for (m in CARD.findAll(text)) {
            val digits = lastDigits(m.groupValues[1]) ?: continue
            if (digits.length < 3) continue
            val before = lower.substring(maxOf(0, m.range.first - 12), m.range.first)
            val kind = when {
                before.contains("credit") -> AccountKind.CREDIT_CARD
                before.contains("debit") -> AccountKind.DEBIT_CARD
                else -> AccountKind.CARD
            }
            out += AccountRef(digits, kind, m.range.first)
        }
        for (m in ACCOUNT.findAll(text)) {
            val digits = lastDigits(m.groupValues[1]) ?: continue
            if (digits.length < 3) continue
            if (out.any { it.index <= m.range.first && m.range.first < it.index + 30 && it.digits == digits }) continue
            out += AccountRef(digits, AccountKind.BANK, m.range.first)
        }
        return out.sortedBy { it.index }
    }

    fun primaryAccount(text: String): AccountRef? {
        return accounts(text).firstOrNull()
    }

    fun hasAccountRef(text: String) = accounts(text).isNotEmpty()

    // ---------------------------------------------------------------- references

    private val REF_LABELED = Regex(
        """(?:\bupi\s*(?:ref(?:erence)?|txn|transaction)?\.?\s*(?:no|number|id)?\.?""" +
            """|\bref(?:erence)?\.?\s*(?:no|number|id)?\.?|\brefno|\butr\s*(?:no|number)?\.?|\brrn\s*(?:no)?\.?""" +
            """|\btxn\s*(?:id|no|ref)\.?|\btransaction\s*(?:id|number|no|ref)\.?|\bimps\s*(?:ref)?\s*(?:no)?\.?|\bneft\s*(?:ref)?\s*(?:no)?\.?)""" +
            """\s*[:\-#]?\s*([a-z0-9]*\d{6,}[a-z0-9]*)""",
        IC,
    )
    private val REF_SLASH = Regex("""\b(?:upi|imps|neft|rtgs)/(?:p2[am]/|cr/|dr/)?(\d{6,})/""", IC)
    private val TWELVE = Regex("""(?<![\d.,])(\d{12})(?![\d.,])""")
    private val UPI_WORD = Regex("""\bupi\b""", IC)
    /** Phone numbers ("SMS BLOCK to 919951860002") must never be mistaken for a UPI reference. */
    private val PHONE_CONTEXT = Regex("""(?:\bto|call|sms|contact|dial|whatsapp|helpline|toll)\W{0,15}$""", IC)
    private val INDIAN_MOBILE = Regex("""^91[6-9]\d{9}$""")

    fun reference(text: String): String? {
        REF_SLASH.find(text)?.let { return it.groupValues[1] }
        for (m in REF_LABELED.findAll(text)) {
            val ref = m.groupValues[1]
            if (ref.count { it.isDigit() } >= 6) return ref.uppercase()
        }
        if (UPI_WORD.containsMatchIn(text)) {
            for (m in TWELVE.findAll(text)) {
                val n = m.groupValues[1]
                val before = text.substring(maxOf(0, m.range.first - 20), m.range.first)
                if (INDIAN_MOBILE.matches(n) || PHONE_CONTEXT.containsMatchIn(before)) continue
                return n
            }
        }
        return null
    }

    // ---------------------------------------------------------------- VPA

    private val VPA = Regex("""\b([a-z0-9][a-z0-9.\-_]{1,255}@[a-z][a-z0-9]{1,63})\b(?![a-z0-9]*\.[a-z])""", IC)

    fun vpa(text: String): String? = VPA.find(text)?.groupValues?.get(1)?.lowercase()

    fun isVpa(s: String) = VPA.matches(s.trim())

    // ---------------------------------------------------------------- merchant

    private const val NAME = """([a-z0-9&@._'*\-][a-z0-9&@._'*\- ]{0,60}?)"""
    private const val TERM =
        """(?=\s+(?:on|at|via|using|for|ref|refno|upi|avl|avbl|bal|txn|info|not\s+you|if\s+not|call|sms|thru|through|dated|date|is|has|was|by|with|in|from|towards|and|-|\d)\b""" +
            """|\s*[(;,\n/]|\.(?:\s|$)|\s+-\s|\s*-+$|\s*$)"""

    /** "UPI/P2M/527812345678/SWIGGY", "UPI/527812345678/swiggy", "NEFT/ACME CORP PVT LTD/SALARY", "IMPS/P2A/123456/JOHN". */
    private val UPI_SLASH_NAME = Regex("""\b(?:upi|imps|neft|rtgs)/(?:p2[am]/|cr/|dr/)?(?:[a-z0-9]{6,}/)?""" + NAME + TERM, IC)
    private val ICICI_CREDITED = Regex(""";\s*""" + NAME + """\s+credited\b""", IC)
    private val TO = Regex("""\b(?:to|trf\s+to|paid\s+to|sent\s+to|transferred\s+to)\s+(?:vpa\s+|a/c\s+linked\s+to\s+vpa\s+)?""" + NAME + TERM, IC)
    private val AT = Regex("""\bat\s+""" + NAME + TERM, IC)
    private val FROM = Regex("""\bfrom\s+(?:vpa\s+)?""" + NAME + TERM, IC)
    private val BY = Regex("""\bby\s+(?:vpa\s+)?""" + NAME + TERM, IC)
    private val INFO = Regex("""\binfo\s*[:\-]?\s*""" + NAME + TERM, IC)
    private val NEFT_REMITTER = Regex("""\b(?:neft|imps|rtgs)\s*(?:cr|dr)?[\s\-/:]+[a-z0-9]{6,}[\-/]([a-z][a-z .&']{2,40}?)(?=[\-/]|$)""", IC)

    private val REJECT_START = Regex(
        """^(?:your|ur|you|my|a/c|a/cx|ac|acct|account|card|bank|upi|vpa|the|self|rs|inr|imps|neft|rtgs|mob|mobile|net|internet|xx|transfer|txn|transaction|a|an|on|us|date|atm\s+pin|beneficiary|linked|registered|wallet|ending)(?:\b|$)""",
        IC,
    )
    private val HAS_LETTER = Regex("""[a-z]{2}""", IC)
    private val MASK_INSIDE = Regex("""(?:[x*#]{2,}\d)|\ba/c\b""", IC)
    private val PHONE_ONLY = Regex("""^\+?\d[\d\s\-]{7,}$""")
    private val STARTS_WITH_AMOUNT = Regex("""^(?:rs|inr|₹)\.?\s*\d""", IC)

    fun validMerchant(raw: String): String? {
        val c = raw.trim().trim('.', ',', '-', ':', ';', '\'', '*', '/').trim()
        if (c.length < 2) return null
        if (isVpa(c)) return c
        if (!HAS_LETTER.containsMatchIn(c)) return null
        if (REJECT_START.containsMatchIn(c) || STARTS_WITH_AMOUNT.containsMatchIn(c)) return null
        if (MASK_INSIDE.containsMatchIn(c)) return null
        if (PHONE_ONLY.matches(c)) return null
        if (Dates.looksLikeDate(c)) return null
        return c
    }

    private fun firstValid(regex: Regex, text: String): String? =
        regex.findAll(text).firstNotNullOfOrNull { validMerchant(it.groupValues[1]) }

    /** Raw payee/payer text (may be a VPA). [MerchantNames] turns it into a display name. */
    fun merchant(text: String, type: TxnType): String? {
        val order = if (type == TxnType.DEBIT) {
            listOf(UPI_SLASH_NAME, ICICI_CREDITED, TO, AT, INFO)
        } else {
            listOf(UPI_SLASH_NAME, FROM, BY, INFO)
        }
        for (r in order) firstValid(r, text)?.let { return it }
        if (type == TxnType.CREDIT) {
            NEFT_REMITTER.find(text)?.let { m -> validMerchant(m.groupValues[1])?.let { return it } }
        }
        return null
    }

    // ---------------------------------------------------------------- hints & channel

    private fun r(p: String) = Regex(p, IC)
    private val H_ATM = r("""\batm\b|\bcash\s+(?:withdrawal|wdl|wd)\b|\bnfs\b|\bwithdrawn\s+at\b""")
    private val H_SALARY = r("""\bsalary\b|\bsal\s+(?:cr|credit)|\bpayroll\b""")
    private val H_REFUND = r("""\brefund(?:ed)?\b|\brevers(?:al|ed)\b|\brevert(?:ed)?\b|\bchargeback\b""")
    private val H_CASHBACK = r("""\bcash\s*back\b""")
    private val H_INTEREST = r("""\binterest\b|\bint\.?\s*(?:pd|cr|credit)\b""")
    private val H_CC_BILL = r(
        """\bcredit\s*card\s+(?:bill|payment|dues)\b|\bcard\s+bill\b|\bcc\s+(?:bill|payment|pymt)\b|\bcred\s*club\b|\bcredclub\b|\bcred\.club\b""" +
            """|\btowards\s+(?:your\s+)?(?:\w+\s+){0,3}credit\s*card\b|\bpayment\b[^\n]{0,40}\breceived\b[^\n]{0,60}\bcredit\s*card\b""" +
            """|\breceived\b[^\n]{0,30}\bpayment\b[^\n]{0,60}\bcredit\s*card\b""",
    )
    private val H_EMI = r("""\bemi\b""")
    private val H_AUTOPAY = r("""\bauto-?pay\b|\bauto[\s-]?debit|\bmandate\b|\bnach\b|\bach\b|\becs\b|\bstanding\s+instruction\b|\bsi\b""")
    private val H_P2A = r("""\bupi/p2a/""")
    private val H_P2M = r("""\bupi/p2m/""")

    fun hints(text: String, type: TxnType): Set<TxnHint> {
        val h = HashSet<TxnHint>()
        if (H_ATM.containsMatchIn(text) && type == TxnType.DEBIT) h += TxnHint.ATM
        if (H_SALARY.containsMatchIn(text) && type == TxnType.CREDIT) h += TxnHint.SALARY
        if (H_REFUND.containsMatchIn(text) && type == TxnType.CREDIT) h += TxnHint.REFUND
        if (H_CASHBACK.containsMatchIn(text) && type == TxnType.CREDIT) h += TxnHint.CASHBACK
        if (H_INTEREST.containsMatchIn(text) && type == TxnType.CREDIT) h += TxnHint.INTEREST
        if (H_CC_BILL.containsMatchIn(text)) h += TxnHint.CC_BILL_PAYMENT
        if (H_EMI.containsMatchIn(text)) h += TxnHint.EMI
        if (H_AUTOPAY.containsMatchIn(text)) h += TxnHint.AUTOPAY
        if (H_P2A.containsMatchIn(text)) h += TxnHint.P2P
        if (H_P2M.containsMatchIn(text)) h += TxnHint.P2M
        return h
    }

    private val C_UPI = r("""\bupi\b|@[a-z]{2,}\b|\bvpa\b""")
    private val C_NEFT = r("""\bneft\b""")
    private val C_IMPS = r("""\bimps\b""")
    private val C_RTGS = r("""\brtgs\b""")
    private val C_ACH = r("""\bach\b|\bnach\b|\becs\b""")
    private val C_NB = r("""\bnet\s*banking\b|\binb\b""")
    private val C_WALLET = r("""\bwallet\b""")
    private val C_CHEQUE = r("""\bcheque\b|\bchq\b|\bclg\b""")

    fun channel(text: String, account: AccountRef?, hints: Set<TxnHint>): Channel? = when {
        TxnHint.ATM in hints -> Channel.ATM
        C_UPI.containsMatchIn(text) -> Channel.UPI
        C_IMPS.containsMatchIn(text) -> Channel.IMPS
        C_NEFT.containsMatchIn(text) -> Channel.NEFT
        C_RTGS.containsMatchIn(text) -> Channel.RTGS
        C_ACH.containsMatchIn(text) -> Channel.ACH
        account != null && account.kind != AccountKind.BANK -> Channel.CARD
        C_NB.containsMatchIn(text) -> Channel.NETBANKING
        C_WALLET.containsMatchIn(text) -> Channel.WALLET
        C_CHEQUE.containsMatchIn(text) -> Channel.CHEQUE
        else -> null
    }
}
