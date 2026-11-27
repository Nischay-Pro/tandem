package app.tandemforactual.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetWidgetSummary
import app.tandemforactual.ui.core.format.formatBudgetCurrency

/** The widget's layouts by the smallest size, in dp, each one needs; the launcher shows the largest that fits. */
internal enum class BudgetWidgetSize(
    val minWidthDp: Float,
    val minHeightDp: Float,
    val layout: Int,
) {
    SMALL(180f, 110f, R.layout.widget_budget_small),
    MEDIUM(250f, 110f, R.layout.widget_budget),
    LARGE(250f, 200f, R.layout.widget_budget_large),
    ;

    companion object {
        fun of(
            widthDp: Float,
            heightDp: Float,
        ): BudgetWidgetSize = entries.lastOrNull { widthDp >= it.minWidthDp && heightDp >= it.minHeightDp } ?: SMALL
    }
}

/** One widget's views: a size map the launcher picks from on Android 12+, or the layout its reported size fits before that. */
internal fun budgetWidgetViews(
    context: Context,
    manager: AppWidgetManager,
    id: Int,
    summary: BudgetWidgetSummary?,
): RemoteViews {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return RemoteViews(BudgetWidgetSize.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to budgetViews(context, summary, it) })
    }
    val options = manager.getAppWidgetOptions(id)
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val size = if (width == 0 || height == 0) BudgetWidgetSize.MEDIUM else BudgetWidgetSize.of(width.toFloat(), height.toFloat())
    return budgetViews(context, summary, size)
}

private fun budgetViews(
    context: Context,
    summary: BudgetWidgetSummary?,
    size: BudgetWidgetSize,
): RemoteViews {
    val views = RemoteViews(context.packageName, size.layout)
    views.setOnClickPendingIntent(android.R.id.background, openApp(context, null, 0))
    views.setOnClickPendingIntent(R.id.widget_add, openApp(context, ACTION_ADD_TRANSACTION, 1))
    views.setTextViewText(R.id.widget_budget_name, summary?.budgetName ?: "Tandem")
    val locked = summary == null || summary.amountsHidden
    views.setViewVisibility(R.id.widget_locked, if (locked) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.widget_amounts, if (locked) View.GONE else View.VISIBLE)
    views.setTextViewText(
        R.id.widget_locked,
        when {
            summary == null -> "Open Tandem to choose a budget."
            summary.locked -> LOCKED_WIDGET_TEXT
            else -> "Open Tandem to see your budget."
        },
    )
    if (!locked) {
        val ready = summary.readyToAssignMinor
        val negative = ready != null && ready < 0
        val readyText = formatAmount(ready, summary.displaySettings)
        views.setTextViewText(R.id.widget_ready_label, summary.headline)
        views.setTextViewText(R.id.widget_ready, readyText)
        views.setTextViewText(R.id.widget_ready_negative, readyText)
        views.setViewVisibility(R.id.widget_ready, if (negative) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_ready_negative, if (negative) View.VISIBLE else View.GONE)
        if (size != BudgetWidgetSize.SMALL) {
            views.setTextViewText(R.id.widget_spent, formatAmount(summary.spentMinor, summary.displaySettings))
        }
    }
    return views
}

/** Opens Tandem, or runs [action] in it; [accountId] starts a new transaction in that account. */
internal fun openApp(
    context: Context,
    action: String?,
    requestCode: Int,
    accountId: String? = null,
): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java)
            .setAction(action ?: Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    accountId?.let { intent.putExtra(EXTRA_WIDGET_ACCOUNT, it) }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

private fun formatAmount(
    minor: Long?,
    settings: BudgetDisplaySettings?,
): String = minor?.let { formatBudgetCurrency(it, settings) } ?: "—"

/** An encrypted budget's widgets show nothing from it, only that it's locked. */
internal const val LOCKED_WIDGET_TEXT = "Locked. Open Tandem to unlock it."
