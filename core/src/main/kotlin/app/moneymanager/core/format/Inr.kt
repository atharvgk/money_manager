package app.moneymanager.core.format

import kotlin.math.abs

/** Indian-style money formatting (₹1,23,456) that behaves the same on the JVM and on Android. */
object Inr {
    private const val MINUS = "−"

    /** "₹1,23,456" — paise are shown only when non-zero ("₹450.50") unless [alwaysShowPaise]. */
    fun format(paise: Long, alwaysShowPaise: Boolean = false): String {
        val sb = StringBuilder()
        if (paise < 0) sb.append(MINUS)
        val a = abs(paise)
        sb.append('₹').append(groupIndian(a / 100))
        val p = a % 100
        if (p != 0L || alwaysShowPaise) sb.append('.').append(p.toString().padStart(2, '0'))
        return sb.toString()
    }

    /** "+₹1,000" or "−₹450". */
    fun formatSigned(paise: Long): String = (if (paise >= 0) "+" else "") + format(paise)

    /** Rounds to whole rupees: "₹1,23,457". */
    fun formatRounded(paise: Long): String {
        val rupees = Math.round(paise / 100.0)
        return (if (rupees < 0) MINUS else "") + "₹" + groupIndian(abs(rupees))
    }

    /** 1234567 -> "12,34,567". */
    fun groupIndian(n: Long): String {
        val s = abs(n).toString()
        val sign = if (n < 0) "-" else ""
        if (s.length <= 3) return sign + s
        val head = s.dropLast(3)
        val parts = ArrayList<String>()
        var rest = head
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return sign + parts.joinToString(",") + "," + s.takeLast(3)
    }

    /** Short form for chart axes and tight table cells: ₹950, ₹12K, ₹1.2L, ₹3.4Cr. */
    fun compact(paise: Long): String {
        val sign = if (paise < 0) MINUS else ""
        val rupees = abs(paise) / 100.0
        val body = when {
            rupees < 1_000 -> Math.round(rupees).toString()
            rupees < 1_00_000 -> oneDecimal(rupees / 1_000) + "K"
            rupees < 1_00_00_000 -> oneDecimal(rupees / 1_00_000) + "L"
            else -> oneDecimal(rupees / 1_00_00_000) + "Cr"
        }
        return "$sign₹$body"
    }

    private fun oneDecimal(v: Double): String {
        if (v >= 10) return Math.round(v).toString()
        val r = Math.round(v * 10) / 10.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    }
}

object Amounts {
    private val CLEAN = Regex("[,\\s]")

    /** "1,23,456.78" -> 12345678 paise. Returns null for anything that isn't a plain amount. */
    fun parseToPaise(raw: String): Long? {
        val s = raw.replace(CLEAN, "").trimEnd('.', '/', '-')
        if (s.isEmpty() || s.count { it == '.' } > 1) return null
        val parts = s.split('.')
        val rupees = parts[0].ifEmpty { "0" }
        if (!rupees.all { it.isDigit() } || rupees.length > 11) return null
        val frac = parts.getOrNull(1) ?: ""
        if (!frac.all { it.isDigit() }) return null
        val paise = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 10
            else -> frac.take(2).toLong()
        }
        return rupees.toLong() * 100 + paise
    }
}
