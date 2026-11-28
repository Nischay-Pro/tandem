package app.tandemforactual.android

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import app.tandemforactual.data.store.NetWorthWidgetSummary
import app.tandemforactual.ui.core.format.formatBudgetCurrency

/** The net worth widget: the total in large type, with what the accounts hold and owe under it. */
internal fun netWorthWidgetViews(
    context: Context,
    summary: NetWorthWidgetSummary?,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_net_worth)
    views.setOnClickPendingIntent(android.R.id.background, openApp(context, null, NET_WORTH_REQUEST))
    views.setTextViewText(R.id.widget_budget_name, summary?.budgetName?.let { "Net worth · $it" } ?: "Net worth")
    val hidden = summary == null || summary.amountsHidden
    views.setViewVisibility(R.id.widget_locked, if (hidden) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.widget_amounts, if (hidden) View.GONE else View.VISIBLE)
    views.setTextViewText(
        R.id.widget_locked,
        when {
            summary == null -> "Open Tandem to choose a budget."
            summary.locked -> LOCKED_WIDGET_TEXT
            else -> "Open Tandem to see your net worth."
        },
    )
    if (summary != null && !hidden) {
        val total = summary.netWorthMinor
        val text = total?.let { formatBudgetCurrency(it, summary.displaySettings) } ?: "—"
        val negative = total != null && total < 0
        views.setTextViewText(R.id.widget_net_worth, text)
        views.setTextViewText(R.id.widget_net_worth_negative, text)
        views.setViewVisibility(R.id.widget_net_worth, if (negative) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_net_worth_negative, if (negative) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_net_worth_detail, netWorthDetail(summary))
    }
    return views
}

/** "Assets $2,500 · Debts $400", the line under the total. */
internal fun netWorthDetail(summary: NetWorthWidgetSummary): String {
    fun amount(minor: Long?) = minor?.let { formatBudgetCurrency(it, summary.displaySettings) } ?: "—"
    return "Assets ${amount(summary.assetsMinor)} · Debts ${amount(summary.debtsMinor)}"
}

private const val NET_WORTH_REQUEST = 7
