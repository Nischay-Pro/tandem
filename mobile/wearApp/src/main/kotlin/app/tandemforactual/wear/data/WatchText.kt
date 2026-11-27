package app.tandemforactual.wear.data

import app.tandemforactual.watch.HIDDEN_AMOUNT
import app.tandemforactual.watch.WatchCategory
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.watch.format
import app.tandemforactual.watch.money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/** The words the watch shows, kept apart from layout so tiles, complications and screens say the same thing. */
object WatchText {
    const val LOCKED = "Locked · open Tandem on your phone"
    const val NO_BUDGET = "Open Tandem on your phone to choose a budget"
    const val LEFT_TO_SPEND = "Left to spend"
    const val TO_BUDGET = "To budget"

    /** "Synced 10:42", or null before the phone has synced. */
    fun synced(
        summary: WatchSummary,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String? {
        val at = summary.syncedAtEpochMs ?: return null
        val time = Instant.ofEpochMilli(at).atZone(zone)
        val today = LocalDate.now(zone)
        val pattern =
            if (time.toLocalDate() == today) {
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            } else {
                DateTimeFormatter.ofPattern("d MMM", locale)
            }
        return "Synced ${pattern.withLocale(locale).format(time)}"
    }

    /** "€20 left" or "€20 over", or the hidden mark when amounts can't be shown. */
    fun categoryStatus(
        summary: WatchSummary,
        category: WatchCategory,
    ): String {
        val left = category.leftMinor
        if (!summary.amountsShown || left == null) return HIDDEN_AMOUNT
        val amount = summary.money.format(abs(left))
        return if (left < 0) "$amount over" else "$amount left"
    }

    fun billDate(
        isoDate: String,
        locale: Locale = Locale.getDefault(),
    ): String =
        runCatching { DateTimeFormatter.ofPattern("EEE d MMM", locale).format(LocalDate.parse(isoDate)) }
            .getOrDefault(isoDate)

    /** A bill's amount as money going out, without a minus sign. */
    fun billAmount(
        summary: WatchSummary,
        minor: Long?,
    ): String = summary.money(minor?.let(::abs))

    fun shortAmount(
        summary: WatchSummary,
        minor: Long?,
    ): String {
        if (!summary.amountsShown || minor == null) return HIDDEN_AMOUNT
        val units = abs(minor) / 100
        val sign = if (minor < 0) "−" else ""
        val symbol =
            summary.money.symbol
                .takeIf { it.length <= 2 }
                .orEmpty()
        val number =
            when {
                units >= 1_000_000 -> "${units / 1_000_000}M"
                units >= 10_000 -> "${units / 1_000}k"
                else -> units.toString()
            }
        return if (summary.money.symbolAfter) "$sign$number$symbol" else "$sign$symbol$number"
    }
}
