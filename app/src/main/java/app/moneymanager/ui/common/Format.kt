package app.moneymanager.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.moneymanager.core.categorize.Categories
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.ui.theme.LocalMoneyColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val zone: ZoneId get() = ZoneId.systemDefault()
private val locale = Locale.forLanguageTag("en-IN")

private val DAY_HEADER = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
private val DAY_HEADER_YEAR = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", locale)
private val FULL = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", locale)
private val TIME = DateTimeFormatter.ofPattern("h:mm a", locale)

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun dayHeader(date: LocalDate): String {
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) DAY_HEADER.format(date) else DAY_HEADER_YEAR.format(date)
    }
}

fun shortDate(ts: Long): String = SHORT_DATE.format(Instant.ofEpochMilli(ts).atZone(zone))
fun fullDateTime(ts: Long): String = FULL.format(Instant.ofEpochMilli(ts).atZone(zone))
fun timeOfDay(ts: Long): String = TIME.format(Instant.ofEpochMilli(ts).atZone(zone))

fun monthLabel(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL, locale) + if (m.year != YearMonth.now(zone).year) " ${m.year}" else ""

fun monthShort(m: YearMonth): String = m.month.getDisplayName(TextStyle.SHORT, locale)

fun monthStart(m: YearMonth): Long = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
fun monthEnd(m: YearMonth): Long = m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

/** Category colour for the current light/dark mode. */
@Composable
fun CategoryEntity?.color(): Color {
    val dark = LocalMoneyColors.current.dark
    if (this == null) return Color(if (dark) Categories.get(Categories.OTHER).colorDark else Categories.get(Categories.OTHER).colorLight)
    return Color(if (dark) colorDark else colorLight)
}

@Composable
fun categoryColor(key: String, categories: Map<String, CategoryEntity>): Color = categories[key].color()
