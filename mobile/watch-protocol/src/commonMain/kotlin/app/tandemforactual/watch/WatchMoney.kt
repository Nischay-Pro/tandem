package app.tandemforactual.watch

import kotlin.math.abs

const val HIDDEN_AMOUNT = "•••"

/** [minor] in hundredths, written with the budget's symbol, separators and decimals; negative amounts get a minus sign. */
fun WatchMoneyFormat.format(minor: Long): String {
    val magnitude = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else abs(minor)
    val digits = fractionDigits.coerceIn(0, 2)
    val rounded = if (digits == 0) (magnitude + 50) / 100 else magnitude / 100
    val whole = rounded.toString().reversed().chunked(3).joinToString(group).reversed()
    val fraction =
        when (digits) {
            0 -> ""
            1 -> decimal + ((magnitude % 100) / 10)
            else -> decimal + (magnitude % 100).toString().padStart(2, '0')
        }
    val number = whole + fraction
    val gap = if (space) " " else ""
    val amount = if (symbolAfter) number + gap + symbol else symbol + gap + number
    return if (minor < 0 && magnitude != 0L) "−$amount" else amount
}

/** The amount, or [HIDDEN_AMOUNT] when the summary may not show amounts or it's unknown. */
fun WatchSummary.money(minor: Long?): String = if (!amountsShown || minor == null) HIDDEN_AMOUNT else money.format(minor)
