package app.moneymanager.core.parse

import app.moneymanager.core.model.TxnType

private val IC = setOf(RegexOption.IGNORE_CASE)

internal object Text {
    private val SPACES = Regex("""[ \t  -​  　]+""")

    /** Unifies line endings and spacing. Keeps single newlines because some banks use them as field separators. */
    fun normalize(body: String): String =
        body.replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence()
            .map { it.replace(SPACES, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}

/** Finds the money-movement verb that appears first in the text, which decides debit vs credit. */
internal object Verbs {
    private val DEBIT = listOf(
        """\bdebited\b""",
        """\bdebit\b(?!\s*card)""",
        """\bspent\b""",
        """\bpaid\b""",
        """\bsent\b""",
        """\bwithdrawn\b""",
        """\bwithdrawal\b""",
        """\bpurchased?\b""",
        """\btransferred\b(?!\s+to\s+(?:your|ur)\b)""",
        """\btrf\s+to\b""",
        """\bdr\b\.?(?=[\s.,:\-]|$)""",
        """\bdeducted\b""",
        """\bcharged\b""",
        """\bused\s+(?:for|at)\b""",
        """\btxn\s+(?:of\s+)?(?=(?:rs|inr|₹))""",
        """\btransaction\s+of\b""",
        """\bauto-?pay\b""",
        """\bauto[\s-]?debit(?:ed)?\b""",
        """\bexecuted\b""",
    ).map { Regex(it, IC) }

    private val CREDIT = listOf(
        """\bcredited\b""",
        """\bcredit\b(?!\s*(?:card|limit|lmt|score|line|cards))""",
        """\breceived\b""",
        """\bdeposited\b""",
        """\bdeposit\b""",
        """\badded\s+to\b""",
        """\bcr\b\.?(?=[\s.,:\-]|$)""",
        """\brefund(?:ed)?\b""",
        """\breversed\b""",
        """\breversal\b""",
        """\btransferred\s+to\s+(?:your|ur)\b""",
    ).map { Regex(it, IC) }

    data class Hit(val type: TxnType, val index: Int, val length: Int)

    fun first(text: String): Hit? {
        var best: Hit? = null
        fun consider(type: TxnType, patterns: List<Regex>) {
            for (p in patterns) {
                val m = p.find(text) ?: continue
                val hit = Hit(type, m.range.first, m.value.length)
                val b = best
                if (b == null || hit.index < b.index || (hit.index == b.index && hit.length > b.length)) best = hit
            }
        }
        consider(TxnType.DEBIT, DEBIT)
        consider(TxnType.CREDIT, CREDIT)
        return best
    }

    private val PAST_CREDIT = Regex("""\b(?:credited|reversed|refunded)\b""", IC)
    fun hasPastCredit(text: String) = PAST_CREDIT.containsMatchIn(text)
}

/** Recognises SMS that mention money but are not transactions. Order of checks lives in [SmsParser]. */
internal object Filters {
    private val OTP_WORD = Regex(
        """\b(?:otp|one[\s-]?time[\s-]?pass(?:word|code)|verification\s+code|passcode|security\s+code|auth(?:entication)?\s+code)\b""",
        IC,
    )
    private val OTP_CODE = listOf(
        """\b\d{4,8}\b[^\n.]{0,20}?\b(?:is|as)\s+(?:your|the)?\s*(?:otp|one[\s-]?time|verification|code|password)""",
        """\b(?:otp|code|password|pin)\b\s*(?:is|:|-|=)\s*\d{4,8}\b""",
        """\bis\s+\d{4,8}\b\s*(?:\.|$|valid|do\s+not|don't|never|for)""",
        """\buse\s+\d{4,8}\b""",
    ).map { Regex(it, IC) }

    fun isOtp(text: String, hasVerb: Boolean): Boolean =
        OTP_WORD.containsMatchIn(text) && (!hasVerb || OTP_CODE.any { it.containsMatchIn(text) })

    private val REQUEST = Regex(
        """\b(?:has\s+requested|have\s+requested|is\s+requesting|requested\s+(?:money|rs|inr|₹|payment|you)|collect\s+request|payment\s+request|request\s+(?:of|for)\s+(?:rs|inr|₹))""",
        IC,
    )

    fun isRequest(text: String) = REQUEST.containsMatchIn(text)

    /** Always a reminder, even if a verb such as "debited" appears ("will be debited"). */
    private val FUTURE = Regex(
        """\bwill\s+be\s+(?:auto[\s-]?)?(?:debited|deducted|charged|credited|processed|refunded|reversed|presented|paid|transferred)\b""" +
            """|\bto\s+be\s+(?:debited|charged|paid)\b""" +
            """|\bmandate\s+(?:has\s+been\s+|is\s+|was\s+)?(?:successfully\s+)?(?:created|registered|set\s*up|setup|modified|revoked|cancell?ed|canceled|paused|resumed|approved)\b""" +
            """|\b(?:e-?mandate|mandate)\s+(?:registration|request)\b""" +
            """|\bauto-?pay\s+(?:has\s+been\s+|is\s+)?(?:set\s*up|setup|registered|activated|enabled|request|scheduled)\b""",
        IC,
    )

    fun isFuture(text: String) = FUTURE.containsMatchIn(text)

    /** Reminder words; only used when there is no completed-transaction verb. */
    private val REMINDER = Regex(
        """\b(?:is|are|was)\s+due\b|\bdue\s+(?:date|on|by)\b|\b(?:total|min(?:imum)?)\.?\s+(?:amt|amount)?\s*due\b""" +
            """|\b(?:amount|amt|payment)\s+due\b|\bstatement\b|\bupcoming\b|\bscheduled\s+(?:for|on)\b|\breminder\b""" +
            """|\bpay\s+(?:now|by|before)\b|\boverdue\b|\bbill\s+(?:is\s+)?generated\b""",
        IC,
    )

    fun isReminder(text: String) = REMINDER.containsMatchIn(text)

    private val FAILED = Regex(
        """\b(?:failed|failure|declined|unsuccessful|not\s+successful|could\s+not\s+be\s+(?:processed|completed)""" +
            """|was\s+not\s+(?:processed|completed|successful)|insufficient\s+(?:funds|balance|limit)|rejected|timed?\s*out""" +
            """|is\s+pending|remains\s+pending)\b""",
        IC,
    )

    fun isFailed(text: String) = FAILED.containsMatchIn(text)

    private val PROMO = Regex(
        """\b(?:offers?|discount|cashback\s+(?:of\s+)?up\s*to|up\s*to\s+\d+\s*%|get\s+(?:up\s*to|flat|instant|extra|\d)|win|won|winner|congrat\w*""" +
            """|lucky|vouchers?|coupons?|sale|shop\s+now|apply\s+(?:now|today)|click|tap\s+here|download|install|pre-?approved|eligible""" +
            """|limited\s+period|hurry|expir\w+|last\s+(?:day|date|chance)|exclusive|instant\s+loan|personal\s+loan""" +
            """|loan\s+(?:offer|of\s+up\s*to|up\s*to|approved|amount)|credit\s+limit\s+(?:increase|enhance)\w*|upgrade""" +
            """|emi\s+(?:starting|@|at\s+just)|starting\s+(?:at|from)|just\s+(?:rs|inr|₹)|avail\s+now|invest\s+now)\b""" +
            """|\bt\s*&\s*c\b""",
        IC,
    )

    fun isPromo(text: String, hasVerb: Boolean, hasAccountRef: Boolean) =
        PROMO.containsMatchIn(text) && (!hasVerb || !hasAccountRef)

    private val BALANCE = Regex(
        """\b(?:avl|avbl|available|avail|aval|clear|closing|ledger|current|total)\.?\s*(?:bal|balance)\b|\bbalance\s+(?:in|of|is|for)\b|\bavlbal\b""",
        IC,
    )

    fun isBalanceOnly(text: String) = BALANCE.containsMatchIn(text)

    private val ACCOUNT_INFO = Regex(
        """\b(?:beneficiary|payee|log(?:ged)?\s*in|login|password|pin|blocked|unblocked|activated|registered|deregistered""" +
            """|linked|delinked|kyc|cheque\s*book|mobile\s+number|email|device|nominee|limit\s+(?:has\s+been\s+)?""" +
            """(?:changed|set|updated|enhanced|increased|reduced|modified))\b""",
        IC,
    )

    fun isAccountInfo(text: String) = ACCOUNT_INFO.containsMatchIn(text)

    private val BANK_WORDS = Regex("""\b(?:a/?c|acct|account|card|bank|upi|neft|imps|rtgs|wallet)\b""", IC)

    fun mentionsBanking(text: String) = BANK_WORDS.containsMatchIn(text)
}
