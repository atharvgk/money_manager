package app.moneymanager.core.parse

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val IC = setOf(RegexOption.IGNORE_CASE)

/**
 * Finds the transaction date/time inside an SMS and reconciles it with the time the SMS arrived.
 * Indian banks write dates day-first: 05-10-25, 05/10/2025, 05Oct25, 05-OCT-2025, Oct 05, 2025,
 * 2025-10-05:14:23:11, 2025:10:05 14:23:11 …
 */
object Dates {
    data class Found(val date: LocalDate, val time: LocalTime?, val index: Int)

    private const val MON = """(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*"""
    private val YMD = Regex("""\b(20\d{2})[-/:.](\d{1,2})[-/:.](\d{1,2})""")
    private val D_MON_Y = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s\-/.]*$MON[\s\-/.,']*(\d{4}|\d{2})(?!\d)""", IC)
    private val MON_D_Y = Regex("""\b$MON\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})\b""", IC)
    private val DMY = Regex("""(?<![\d/\-.])(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?![\d/\-])""")
    private val ON_DM = Regex("""\bon\s+(\d{1,2})[-/](\d{1,2})(?![\d/\-])""", IC)
    private val TIME = Regex("""^[\s,:T]*(?:at\s+)?(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(am|pm|hrs|ist)?""", IC)
    private val ANY_DATE = Regex("""^\d{1,4}[-/.:]\d{1,2}([-/.:]\d{1,4})?$""")

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun looksLikeDate(s: String) = ANY_DATE.matches(s.trim()) ||
        D_MON_Y.matchEntire(s.trim()) != null

    private fun month(name: String) = MONTHS.indexOf(name.lowercase().take(3)) + 1

    private fun year(y: String, ref: LocalDate): Int =
        if (y.length == 2) 2000 + y.toInt() else y.toInt()

    private fun date(y: Int, m: Int, d: Int): LocalDate? =
        if (m !in 1..12 || d !in 1..31 || y !in 2000..2099) null
        else runCatching { LocalDate.of(y, m, d) }.getOrNull()

    /** The first date in [text], plus a time if one follows it closely. */
    fun find(text: String, ref: LocalDate): Found? {
        val candidates = ArrayList<Found>()
        YMD.findAll(text).forEach { m ->
            val (y, mo, d) = m.destructured
            date(y.toInt(), mo.toInt(), d.toInt())?.let { candidates += Found(it, timeAfter(text, m.range.last + 1), m.range.first) }
        }
        D_MON_Y.findAll(text).forEach { m ->
            val (d, mo, y) = m.destructured
            date(year(y, ref), month(mo), d.toInt())?.let { candidates += Found(it, timeAfter(text, m.range.last + 1), m.range.first) }
        }
        MON_D_Y.findAll(text).forEach { m ->
            val (mo, d, y) = m.destructured
            date(y.toInt(), month(mo), d.toInt())?.let { candidates += Found(it, timeAfter(text, m.range.last + 1), m.range.first) }
        }
        DMY.findAll(text).forEach { m ->
            val (d, mo, y) = m.destructured
            date(year(y, ref), mo.toInt(), d.toInt())?.let { candidates += Found(it, timeAfter(text, m.range.last + 1), m.range.first) }
        }
        if (candidates.isEmpty()) {
            ON_DM.findAll(text).forEach { m ->
                val (d, mo) = m.destructured
                // No year: pick the one that puts the date closest to (and not after) the SMS.
                val sameYear = date(ref.year, mo.toInt(), d.toInt())
                val dt = if (sameYear != null && sameYear.isAfter(ref.plusDays(1))) date(ref.year - 1, mo.toInt(), d.toInt()) else sameYear
                dt?.let { candidates += Found(it, timeAfter(text, m.range.last + 1), m.range.first) }
            }
        }
        return candidates.minByOrNull { it.index }
    }

    private fun timeAfter(text: String, from: Int): LocalTime? {
        if (from >= text.length) return null
        val m = TIME.find(text.substring(from, minOf(text.length, from + 24))) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].toIntOrNull() ?: 0
        when (m.groupValues[4].lowercase()) {
            "pm" -> if (h < 12) h += 12
            "am" -> if (h == 12) h = 0
        }
        if (h !in 0..23 || min !in 0..59 || sec !in 0..59) return null
        return LocalTime.of(h, min, sec)
    }

    /**
     * The best timestamp for a transaction. The SMS arrival time is precise, so it wins unless the body
     * names a different day (e.g. a NEFT credited yesterday) or an explicit time.
     */
    fun resolve(text: String, smsTimestamp: Long, zone: ZoneId): Long {
        val sms = Instant.ofEpochMilli(smsTimestamp).atZone(zone)
        val found = find(text, sms.toLocalDate()) ?: return smsTimestamp
        val daysBefore = ChronoUnit.DAYS.between(found.date, sms.toLocalDate())
        if (daysBefore < -1 || daysBefore > 7) return smsTimestamp
        if (found.time != null) {
            val t = found.date.atTime(found.time).atZone(zone).toInstant().toEpochMilli()
            if (abs(t - smsTimestamp) <= 8L * 24 * 3600 * 1000) return t
        }
        if (found.date == sms.toLocalDate()) return smsTimestamp
        return found.date.atTime(sms.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
    }
}
