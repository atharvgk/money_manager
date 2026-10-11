package app.moneymanager.sms

import android.content.Context
import android.provider.Telephony
import app.moneymanager.core.model.SmsMessage
import java.security.MessageDigest
import kotlin.math.abs

object SmsReader {
    private const val DAY = 24 * 3600 * 1000L

    /**
     * The inbox stores both the receive time (date) and the bank's send time (date_sent). The broadcast only
     * gives us the send time reliably, so both paths pick the same value here and therefore the same hash.
     */
    fun chooseTimestamp(received: Long, sent: Long): Long =
        if (sent > 0 && abs(sent - received) < DAY) sent else received

    /** Stable id for an SMS so the same message is never imported twice. */
    fun hash(m: SmsMessage): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${m.sender}|${m.timestamp / 1000}|${m.body}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    /** Reads inbox messages received at or after [since], oldest first. */
    fun readInbox(context: Context, since: Long): List<SmsMessage> {
        val out = ArrayList<SmsMessage>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val iSent = c.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
            while (c.moveToNext()) {
                val body = c.getString(iBody) ?: continue
                val received = c.getLong(iDate)
                val sent = if (c.isNull(iSent)) 0L else c.getLong(iSent)
                out += SmsMessage(c.getString(iAddr) ?: "", body, chooseTimestamp(received, sent))
            }
        }
        return out
    }
}
