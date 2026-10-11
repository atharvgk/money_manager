package app.moneymanager.core.parse

private val IC = setOf(RegexOption.IGNORE_CASE)

/** Turns raw payee text from an SMS into a clean display name and a stable matching key. */
object MerchantNames {
    private val GATEWAY_PREFIX = Regex(
        """^(?:payu|pyu|raz|razorpay|rzp|ccav|ccavenue|bd|billdesk|paytm|phonepe|pp|gpay|googlepay|sq|paypal|ebs|cf|cashfree|pinelabs|ezetap|mswipe|bharatpe|ipay|instamojo)\s*[*_\-.]\s*""",
        IC,
    )
    private val CHANNEL_PREFIX = Regex(
        """^(?:ach\s*d|ach\s*c|ach|nach|ecs|si|upi|neft|imps|rtgs|pos|ecom|vps|ips|mmt|bil|bbps|inb|ib|onl|vin|ipay|tpt|pca)\b[\s\-/:*]*""",
        IC,
    )
    private val LEGAL_SUFFIX = Regex(
        """[\s,.\-]*\b(?:pvt\.?\s*ltd\.?|private\s+limited|private\s+ltd|pvt|ltd\.?|limited|llp|inc\.?)\s*$""",
        IC,
    )
    private val TRAILING_ID = Regex("""[\s*\-/#_.]*(?:[a-z]{0,3}\d{4,}[a-z0-9]*)$""", IC)
    private val STAR_TAIL = Regex("""\*.*$""")
    private val MULTISPACE = Regex("""\s+""")
    private val PHONE = Regex("""^(?:\+?91)?[6-9]\d{9}$""")
    private val KEY_CLEAN = Regex("""[^a-z0-9@&. ]""")

    /** "AMAZON PAY INDIA PVT LTD" -> "Amazon Pay India", "swiggy@icici" -> "Swiggy". */
    fun display(raw: String?, vpa: String?): String? {
        val source = raw?.trim()?.takeIf { it.isNotEmpty() }
        if (source == null || Extract.isVpa(source)) {
            val v = (source ?: vpa) ?: return null
            return fromVpa(v)
        }
        var s = source.removePrefix("VPA ").removePrefix("vpa ")
        s = CHANNEL_PREFIX.replace(s, "")
        s = GATEWAY_PREFIX.replace(s, "")
        if (s.contains('*')) {
            val head = STAR_TAIL.replace(s, "").trim()
            if (head.length >= 3) s = head
        }
        repeat(2) {
            s = TRAILING_ID.replace(s, "")
            s = LEGAL_SUFFIX.replace(s, "")
        }
        s = s.trim(' ', '-', '.', ',', '/', '*', '_').replace(MULTISPACE, " ")
        if (s.length < 2) return source
        return titleCase(s)
    }

    /** Lower-case key used for "all transactions from this merchant" and for rules. */
    fun key(display: String?): String? =
        display?.lowercase()?.replace(KEY_CLEAN, " ")?.replace(MULTISPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }

    private fun fromVpa(vpa: String): String {
        val local = vpa.substringBefore('@')
        if (PHONE.matches(local)) return vpa.lowercase()
        // Merchant QR handles (paytmqr28100…, q12345678@ybl) don't carry a readable name.
        if (local.count { it.isDigit() } >= 5 || local.length > 24) return vpa.lowercase()
        val words = local.split('.', '_', '-').map { it.trimEnd { c -> c.isDigit() } }.filter { it.length >= 2 }
        if (words.isEmpty()) return vpa.lowercase()
        return titleCase(words.joinToString(" "))
    }

    private val KEEP_UPPER = setOf(
        "ATM", "UPI", "HDFC", "SBI", "ICICI", "IRCTC", "BSNL", "LIC", "KFC", "PVR", "INOX", "IOCL", "HPCL", "BPCL",
        "BESCOM", "MSEDCL", "BSES", "ACT", "OLA", "EMI", "NEFT", "IMPS", "DMRC", "BMTC", "KSRTC", "MSRTC", "TSRTC",
        "UPSRTC", "NHAI", "ITC", "MRF", "TVS", "DTH", "HP", "BP", "IGL", "MGL", "PNB", "OYO", "BBPS", "CRED",
    )

    fun titleCase(s: String): String {
        val hasLower = s.any { it.isLowerCase() }
        if (hasLower && s.first().isUpperCase()) return s
        // Words are split on spaces and hyphens ("GROWW-SIP" -> "Groww-Sip"); separators are kept.
        return s.split(' ').joinToString(" ") { word ->
            word.split('-').joinToString("-") { w ->
                when {
                    w.uppercase() in KEEP_UPPER -> w.uppercase()
                    w.length <= 2 && !hasLower -> w.uppercase()
                    else -> w.lowercase().replaceFirstChar { it.uppercase() }
                }
            }
        }
    }
}

/** Heuristics for "is this payee a person?" — used for the "Transfers to people" category. */
object Payees {
    private val PHONE_LOCAL = Regex("""^(?:\+?91)?[6-9]\d{9}(?:-\d)?$""")
    private val MERCHANT_VPA = Regex(
        """qr|^q\d|paytm|bharatpe|rzp|razorpay|payu|cashfree|billdesk|pinelabs|merchant|store|shop|mart|pay\.|\.pay|ezetap|mswipe|gpay-|bizaxis|okbiz|ptys|yespay|ibkpos|hdfcmerchant|instamojo""",
        IC,
    )
    private val PERSONAL_HANDLES = setOf("okaxis", "okhdfcbank", "okicici", "oksbi", "axl", "ibl", "ybl", "upi", "apl", "yapl", "naviaxis", "waicici", "wahdfcbank", "wasbi", "superyes", "fam", "freecharge", "jupiteraxis", "slc", "kotak", "kmbl", "pz", "abfspay", "axisbank", "icici", "hdfcbank", "sbi", "paytm", "ptsbi", "ptaxis", "pthdfc", "ptyes")
    private val BUSINESS_WORDS = setOf(
        "pvt", "ltd", "limited", "private", "llp", "inc", "corp", "corporation", "company", "co", "store", "stores", "mart", "shop", "shoppe",
        "enterprise", "enterprises", "traders", "trading", "services", "service", "solutions", "technologies", "tech", "foods", "food",
        "restaurant", "cafe", "hotel", "hotels", "bakery", "bakers", "sweets", "medical", "medicals", "pharma", "pharmacy", "chemist",
        "chemists", "hospital", "clinic", "agency", "agencies", "centre", "center", "india", "pay", "payments", "bank", "finance",
        "financial", "capital", "insurance", "fuel", "fuels", "petrol", "petroleum", "motors", "automobiles", "general", "kirana",
        "supermarket", "bazaar", "bazar", "mall", "cinema", "cinemas", "travels", "tours", "electricals", "electronics", "hardware",
        "textiles", "fashion", "fashions", "collection", "boutique", "salon", "parlour", "gym", "fitness", "academy", "school", "college",
        "university", "institute", "classes", "tutorials", "dairy", "milk", "fruits", "vegetables", "chicken", "meat", "fish", "bar",
        "dhaba", "bhavan", "bhawan", "sagar", "vihar", "kitchen", "pizza", "burger", "chai", "tea", "coffee", "juice", "tiffin", "mess",
        "canteen", "group", "industries", "international", "global", "networks", "digital", "online", "retail", "wholesale",
        "distributors", "house", "point", "zone", "hub", "world", "charges", "bill", "recharge", "tax", "govt", "municipal",
        "electricity", "water", "gas", "station", "filling", "provision", "provisions", "traders", "associates", "and", "&", "sons",
        "brothers", "bros", "ventures", "labs", "systems", "infotech", "communications", "telecom", "broadband", "cable", "cabs",
        "logistics", "express", "courier", "pharmacy", "diagnostics", "lab", "opticals", "optical", "jewellers", "jewellery",
        "garments", "footwear", "furniture", "steel", "cement", "constructions", "builders", "developers", "properties", "realty",
        "society", "association", "trust", "foundation", "club", "resort", "lodge", "inn", "stay", "atm", "card", "upi",
    )

    fun looksPersonal(merchant: String?, vpa: String?, p2p: Boolean, p2m: Boolean): Boolean {
        if (p2m) return false
        if (p2p) return true
        if (vpa != null) {
            val local = vpa.substringBefore('@')
            val handle = vpa.substringAfter('@').lowercase()
            if (MERCHANT_VPA.containsMatchIn(vpa)) return false
            if (PHONE_LOCAL.matches(local)) return true
            if (handle.startsWith("ok") && !handle.startsWith("okbiz")) return true
            if (handle in PERSONAL_HANDLES && Regex("""^[a-z]+[._][a-z]+\d{0,4}$""", IC).matches(local)) return true
        }
        val name = merchant?.trim() ?: return false
        if (Extract.isVpa(name)) return false
        val words = name.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.size !in 2..4) return false
        if (words.any { it.lowercase() in BUSINESS_WORDS }) return false
        return words.all { w -> w.length >= 2 && w.all { it.isLetter() || it == '.' } } || words.all { it.length == 1 || it.all(Char::isLetter) }
    }
}
