package app.tandemforactual.android

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import app.tandemforactual.data.store.DueSchedule
import app.tandemforactual.data.store.UpcomingBillsWidgetSummary
import app.tandemforactual.ui.core.format.formatBudgetCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The bills due soon, one row each with its date and amount, or why there's nothing to show. */
internal fun upcomingBillsWidgetViews(
    context: Context,
    summary: UpcomingBillsWidgetSummary?,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_bills)
    views.setOnClickPendingIntent(android.R.id.background, openApp(context, null, BILLS_REQUEST))
    val message = billsMessage(summary)
    views.setViewVisibility(R.id.widget_locked, if (message != null) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.widget_rows, if (message == null) View.VISIBLE else View.GONE)
    message?.let { views.setTextViewText(R.id.widget_locked, it) }
    if (message == null && summary != null) {
        views.removeAllViews(R.id.widget_rows)
        summary.bills.take(MAX_BILL_ROWS).forEach { bill ->
            val row = RemoteViews(context.packageName, R.layout.widget_category_row)
            row.setTextViewText(R.id.widget_row_name, billLabel(bill))
            row.setTextViewText(
                R.id.widget_row_amount,
                bill.amountMinor?.let { formatBudgetCurrency(kotlin.math.abs(it), summary.displaySettings) } ?: "",
            )
            views.addView(R.id.widget_rows, row)
        }
    }
    return views
}

/** Why the widget lists no bills, or null when it has some to list. */
internal fun billsMessage(summary: UpcomingBillsWidgetSummary?): String? =
    when {
        summary == null -> "Open Tandem to choose a budget."
        summary.locked -> LOCKED_WIDGET_TEXT
        summary.amountsHidden -> "Open Tandem to see your bills."
        summary.bills.isEmpty() -> "Nothing due in the next two weeks."
        else -> null
    }

/** "28 Nov · Rent": when it's due, then what it is. */
internal fun billLabel(bill: DueSchedule): String {
    val date = runCatching { LocalDate.parse(bill.date).format(DateTimeFormatter.ofPattern("d MMM")) }.getOrDefault(bill.date)
    return "$date · ${bill.name}"
}

private const val BILLS_REQUEST = 8
private const val MAX_BILL_ROWS = 5
