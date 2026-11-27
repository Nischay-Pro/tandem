package app.tandemforactual.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.tandemforactual.watch.HIDDEN_AMOUNT
import app.tandemforactual.watch.WatchState
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.wear.data.WatchText
import kotlin.math.abs

/** Complication data built only from the summary on the watch, so it shows the same privacy state as the app. */
object WatchComplications {
    /** Left to spend in [categoryId], or the whole budget without one; a ranged value falls back to the first category. */
    fun leftToSpend(
        summary: WatchSummary?,
        categoryId: String?,
        type: ComplicationType,
        tap: PendingIntent?,
    ): ComplicationData {
        if (summary == null) return NoDataComplicationData()
        val category =
            summary.categories.firstOrNull { it.id == categoryId }
                ?: summary.categories.firstOrNull().takeIf { type == ComplicationType.RANGED_VALUE }
        val left = category?.leftMinor ?: summary.leftToSpendMinor.takeIf { category == null }
        val text = shown(summary, left)
        val title = category?.name ?: "Left"
        val description = plain("${category?.name ?: WatchText.LEFT_TO_SPEND}: ${if (summary.amountsShown) text else "hidden"}")
        if (type == ComplicationType.RANGED_VALUE && category != null) {
            val budgeted = (category.budgetedMinor ?: 0).coerceAtLeast(1).toFloat()
            val value = if (summary.amountsShown) (left ?: 0).coerceIn(0, budgeted.toLong()).toFloat() else 0f
            return RangedValueComplicationData
                .Builder(value, 0f, budgeted, description)
                .setText(plain(text))
                .setTitle(plain(title))
                .setTapAction(tap)
                .build()
        }
        return ShortTextComplicationData
            .Builder(plain(text), description)
            .setTitle(plain(title))
            .setTapAction(tap)
            .build()
    }

    fun toBudget(
        summary: WatchSummary?,
        tap: PendingIntent?,
    ): ComplicationData {
        if (summary == null) return NoDataComplicationData()
        val text = shown(summary, summary.toBudgetMinor)
        return ShortTextComplicationData
            .Builder(plain(text), plain("${WatchText.TO_BUDGET}: ${if (summary.amountsShown) text else "hidden"}"))
            .setTitle(plain("Budget"))
            .setTapAction(tap)
            .build()
    }

    /** The next bill: its amount titled with its name, or name, amount and date where there's room. */
    fun nextBill(
        summary: WatchSummary?,
        type: ComplicationType,
        tap: PendingIntent?,
    ): ComplicationData {
        if (summary == null) return NoDataComplicationData()
        val locked = summary.state == WatchState.LOCKED
        val bill = summary.bills.firstOrNull().takeUnless { locked }
        val name = bill?.name ?: "Bills"
        val amount =
            when {
                locked -> shown(summary, null)
                bill == null -> "None"
                else -> shown(summary, bill.amountMinor?.let(::abs))
            }
        val description =
            plain(if (bill == null) "Next bill: $amount" else "Next bill: ${bill.name}, ${WatchText.billDate(bill.date)}")
        if (type == ComplicationType.LONG_TEXT && bill != null) {
            val line = "${bill.name} · ${WatchText.billAmount(summary, bill.amountMinor)} · ${WatchText.billDate(bill.date)}"
            return LongTextComplicationData
                .Builder(plain(line), description)
                .setTapAction(tap)
                .build()
        }
        return ShortTextComplicationData
            .Builder(plain(amount), description)
            .setTitle(plain(name))
            .setTapAction(tap)
            .build()
    }

    private fun shown(
        summary: WatchSummary,
        minor: Long?,
    ): String =
        when (summary.state) {
            WatchState.LOCKED -> "Locked"
            WatchState.AMOUNTS_HIDDEN -> HIDDEN_AMOUNT
            WatchState.VISIBLE -> WatchText.shortAmount(summary, minor)
        }

    private fun plain(text: String) = PlainComplicationText.Builder(text).build()
}
