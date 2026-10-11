package app.moneymanager.core.parse

/**
 * Understands Indian SMS sender IDs.
 *
 * DLT headers look like "VM-HDFCBK", "JD-ICICIT-S" or "AX-SBIUPI-T": a 2-letter operator/circle
 * prefix, a 6-character header, and (since 2024) an optional -P/-S/-T/-G suffix where -P means
 * promotional. Some phones show just the header ("HDFCBK").
 */
object Senders {
    data class Info(
        val raw: String,
        val header: String,
        val isPromotional: Boolean,
        val isPhoneNumber: Boolean,
        val bank: String?,
        val looksLikeBank: Boolean,
        /** True when we have no sender at all (e.g. samples pasted without one). */
        val unknown: Boolean,
    )

    private val DLT = Regex("""^[A-Z]{2}-([A-Z0-9]{3,10})(?:-([PSTG]))?$""")
    private val PHONE = Regex("""^\+?[0-9][0-9 \-]{6,}$""")

    /** Header fragment -> display name. First match wins, so put specific entries first. */
    private val BANKS: List<Pair<Regex, String>> = listOf(
        "SBICRD|SBICARD|SBIPSG|SBMCRD" to "SBI Card",
        "HDFC" to "HDFC Bank",
        "SBI|CBSSBI|SBMSMS" to "SBI",
        "ICICI|ICIBNK" to "ICICI Bank",
        "AXIS|UTIBNK" to "Axis Bank",
        "KOTAK|KMBL" to "Kotak",
        "PNB" to "PNB",
        "BOB|BARODA" to "Bank of Baroda",
        "CANBNK|CANARA" to "Canara Bank",
        "UNION|UBOI|UBIN" to "Union Bank",
        "IDFC" to "IDFC FIRST",
        "INDUS|INDUSB" to "IndusInd",
        "YESBNK|YESBK|YESB" to "Yes Bank",
        "FEDBNK|FEDFIB|FEDERAL" to "Federal Bank",
        "AUBANK|AUSFB" to "AU Bank",
        "RBL" to "RBL Bank",
        "IDBI" to "IDBI Bank",
        "BOIIND|BOISMS" to "Bank of India",
        "CENTBK|CBOI" to "Central Bank",
        "INDBNK|INDIAN" to "Indian Bank",
        "IOB" to "IOB",
        "UCO" to "UCO Bank",
        "PAYTMB|PYTMBK|PAYTM" to "Paytm",
        "AIRBNK|AIRTELP" to "Airtel Payments Bank",
        "JIOPBK|JIOPAY" to "Jio Payments Bank",
        "AMEX" to "American Express",
        "ONECRD|ONECARD" to "OneCard",
        "SLICE" to "slice",
        "CITI" to "Citi",
        "SCBANK|STANCB|SCBNK" to "Standard Chartered",
        "HSBC" to "HSBC",
        "DBS" to "DBS",
        "EQUITAS|ESFBNK" to "Equitas",
        "UJJIVN" to "Ujjivan",
        "BANDHN" to "Bandhan Bank",
        "SIBSMS|SIBANK" to "South Indian Bank",
        "KBLBNK" to "Karnataka Bank",
        "KVBANK" to "KVB",
        "CUBANK" to "City Union Bank",
        "DCBBNK" to "DCB Bank",
        "SARASW" to "Saraswat Bank",
        "JUPITR" to "Jupiter",
    ).map { (pattern, name) -> Regex(pattern) to name }

    private val BANKISH = Regex("""BNK|BANK|BK$|CRD|CARD|UPI""")

    fun analyze(sender: String): Info {
        val raw = sender.trim()
        if (raw.isEmpty()) return Info(raw, "", false, false, null, true, unknown = true)
        val upper = raw.uppercase()
        val isPhone = PHONE.matches(upper)
        val m = DLT.matchEntire(upper)
        val header = m?.groupValues?.get(1) ?: upper.substringAfter('-').substringBefore('-')
        val promo = m?.groupValues?.get(2) == "P"
        val bank = if (isPhone) null else BANKS.firstOrNull { it.first.containsMatchIn(header) }?.second
        val bankish = !isPhone && (bank != null || BANKISH.containsMatchIn(header))
        return Info(raw, header, promo, isPhone, bank, bankish, unknown = false)
    }

    /** Best-effort bank name from the message body, used when the sender is unknown. */
    fun bankFromBody(text: String): String? {
        val lower = text.lowercase()
        return BODY_BANKS.firstOrNull { lower.contains(it.first) }?.second
    }

    private val BODY_BANKS = listOf(
        "sbi card" to "SBI Card",
        "hdfc" to "HDFC Bank",
        "icici" to "ICICI Bank",
        "axis bank" to "Axis Bank",
        "kotak" to "Kotak",
        "-sbi" to "SBI",
        "sbi " to "SBI",
        "state bank" to "SBI",
        "canara" to "Canara Bank",
        "union bank" to "Union Bank",
        "pnb" to "PNB",
        "idfc" to "IDFC FIRST",
        "indusind" to "IndusInd",
        "yes bank" to "Yes Bank",
        "federal bank" to "Federal Bank",
        "bank of baroda" to "Bank of Baroda",
        "-bob" to "Bank of Baroda",
        "amex" to "American Express",
        "onecard" to "OneCard",
    )
}
