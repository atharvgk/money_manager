package app.moneymanager.core

import app.moneymanager.core.model.IgnoreReason
import app.moneymanager.core.model.ParseResult
import app.moneymanager.core.model.ParsedTxn
import app.moneymanager.core.model.SmsMessage
import app.moneymanager.core.model.TxnType
import app.moneymanager.core.parse.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.time.ZoneId
import java.time.ZonedDateTime

object T {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /** Default "SMS received" time for samples: 5 Oct 2025, 14:24 IST. */
    val ts: Long = at(2025, 10, 5, 14, 24)

    fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, s: Int = 0): Long =
        ZonedDateTime.of(y, m, d, h, min, s, 0, zone).toInstant().toEpochMilli()

    fun parse(sender: String, body: String, time: Long = ts): ParseResult =
        SmsParser.parse(SmsMessage(sender, body, time), zone)

    fun txn(sender: String, body: String, time: Long = ts): ParsedTxn {
        val r = parse(sender, body, time)
        if (r !is ParseResult.Parsed) fail("Expected a transaction but got $r for:\n$body")
        return (r as ParseResult.Parsed).txn
    }

    fun ignored(sender: String, body: String, reason: IgnoreReason? = null) {
        val r = parse(sender, body)
        if (r !is ParseResult.Ignored) fail("Expected Ignored(${reason ?: "any"}) but got $r for:\n$body")
        if (reason != null) assertEquals("Wrong ignore reason for:\n$body", reason, (r as ParseResult.Ignored).reason)
    }

    fun rupees(r: Double): Long = Math.round(r * 100)

    fun ParsedTxn.check(
        type: TxnType,
        amount: Double,
        account: String? = null,
        merchant: String? = null,
        vpa: String? = null,
        ref: String? = null,
        balance: Double? = null,
    ) {
        val ctx = "\n--- for SMS:\n$body\n--- parsed: $this"
        assertEquals("type$ctx", type, this.type)
        assertEquals("amount$ctx", rupees(amount), amountPaise)
        if (account != null) assertEquals("account$ctx", account, this.account)
        if (merchant != null) assertEquals("merchant$ctx", merchant, this.merchant)
        if (vpa != null) assertEquals("vpa$ctx", vpa, this.vpa)
        if (ref != null) assertEquals("ref$ctx", ref, this.reference)
        if (balance != null) assertEquals("balance$ctx", rupees(balance), balancePaise)
    }
}
