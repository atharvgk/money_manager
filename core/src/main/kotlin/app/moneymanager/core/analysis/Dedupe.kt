package app.moneymanager.core.analysis

import app.moneymanager.core.model.TxnType
import kotlin.math.abs

/**
 * The same payment often arrives twice (bank SMS + card/UPI SMS). Two records are the same transaction if:
 *  - same direction and amount, and
 *  - both have a reference number and the references match, or
 *  - (when at least one has no reference) they are within 2 minutes and the account numbers don't conflict.
 */
object Dedupe {
    const val WINDOW_MS = 2 * 60 * 1000L

    data class Key(
        val type: TxnType,
        val amountPaise: Long,
        val timestamp: Long,
        val account: String?,
        val reference: String?,
    )

    fun normalizeRef(ref: String?): String? =
        ref?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.length >= 6 }

    fun isSameTransaction(a: Key, b: Key): Boolean {
        if (a.type != b.type || a.amountPaise != b.amountPaise) return false
        val ra = normalizeRef(a.reference)
        val rb = normalizeRef(b.reference)
        if (ra != null && rb != null) {
            return ra == rb || (minOf(ra.length, rb.length) >= 10 && (ra.endsWith(rb) || rb.endsWith(ra)))
        }
        if (abs(a.timestamp - b.timestamp) > WINDOW_MS) return false
        val aa = a.account
        val ab = b.account
        if (aa != null && ab != null && !(aa.endsWith(ab) || ab.endsWith(aa))) return false
        return true
    }
}
