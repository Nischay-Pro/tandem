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
import app.tandemforactual.data.store.CategoryWidgetSummary
import app.tandemforactual.data.store.WidgetCategory
import app.tandemforactual.data.store.categoryBalanceText
import app.tandemforactual.ui.core.format.formatBudgetCurrency

/** How many categories fit at each widget size, by the smallest size in dp each needs; one row shows as a single large amount. */
internal enum class CategoryWidgetSize(
    val minWidthDp: Float,
    val minHeightDp: Float,
    val rows: Int,
) {
    COMPACT(110f, 110f, 1),
    SHORT(250f, 110f, 2),
    TALL(180f, 180f, 4),
    TALLER(180f, 250f, 6),
    ;

    companion object {
        fun of(
            widthDp: Float,
            heightDp: Float,
        ): CategoryWidgetSize = entries.lastOrNull { widthDp >= it.minWidthDp && heightDp >= it.minHeightDp } ?: COMPACT
    }
}

internal fun categoryWidgetViews(
    context: Context,
    manager: AppWidgetManager,
    id: Int,
    summary: CategoryWidgetSummary?,
): RemoteViews {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return RemoteViews(
            CategoryWidgetSize.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to categoryViews(context, id, summary, it.rows) },
        )
    }
    val options = manager.getAppWidgetOptions(id)
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val size = if (width == 0 || height == 0) CategoryWidgetSize.TALL else CategoryWidgetSize.of(width.toFloat(), height.toFloat())
    return categoryViews(context, id, summary, size.rows)
}

private fun categoryViews(
    context: Context,
    id: Int,
    summary: CategoryWidgetSummary?,
    rows: Int,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_category)
    views.setOnClickPendingIntent(R.id.widget_add, openApp(context, ACTION_ADD_TRANSACTION, 1))
    val categories = summary?.categories.orEmpty()
    val needsSetup = summary != null && !summary.amountsHidden && categories.isEmpty()
    val message =
        when {
            summary == null -> "Open Tandem to choose a budget."
            summary.locked -> LOCKED_WIDGET_TEXT
            summary.amountsHidden -> "Open Tandem to see your budget."
            needsSetup -> "Tap to choose categories."
            else -> null
        }
    views.setOnClickPendingIntent(android.R.id.background, if (needsSetup) widgetSetup(context, id) else openApp(context, null, 0))
    views.setViewVisibility(R.id.widget_locked, if (message != null) View.VISIBLE else View.GONE)
    message?.let { views.setTextViewText(R.id.widget_locked, it) }
    val single = message == null && (rows == 1 || categories.size == 1)
    views.setViewVisibility(R.id.widget_single, if (single) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.widget_rows, if (message == null && !single) View.VISIBLE else View.GONE)
    val settings = summary?.displaySettings
    if (single) {
        showSingle(views, categories.first(), settings)
    } else if (message == null) {
        views.removeAllViews(R.id.widget_rows)
        categories.take(rows).forEach { views.addView(R.id.widget_rows, categoryRow(context, it, settings)) }
    }
    return views
}

private fun showSingle(
    views: RemoteViews,
    category: WidgetCategory,
    settings: BudgetDisplaySettings?,
) {
    val overspent = category.balanceMinor < 0
    val amount = formatBudgetCurrency(category.balanceMinor, settings)
    views.setTextViewText(R.id.widget_single_amount, amount)
    views.setTextViewText(R.id.widget_single_amount_negative, amount)
    views.setViewVisibility(R.id.widget_single_amount, if (overspent) View.GONE else View.VISIBLE)
    views.setViewVisibility(R.id.widget_single_amount_negative, if (overspent) View.VISIBLE else View.GONE)
    views.setTextViewText(R.id.widget_single_caption, if (overspent) "${category.name} · overspent" else category.name)
}

private fun categoryRow(
    context: Context,
    category: WidgetCategory,
    settings: BudgetDisplaySettings?,
): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_category_row)
    val overspent = category.balanceMinor < 0
    val amount = categoryBalanceText(category.balanceMinor) { formatBudgetCurrency(it, settings) }
    row.setTextViewText(R.id.widget_row_name, category.name)
    row.setTextViewText(R.id.widget_row_amount, amount)
    row.setTextViewText(R.id.widget_row_amount_negative, amount)
    row.setViewVisibility(R.id.widget_row_amount, if (overspent) View.GONE else View.VISIBLE)
    row.setViewVisibility(R.id.widget_row_amount_negative, if (overspent) View.VISIBLE else View.GONE)
    return row
}

/** Opens this widget's settings so someone can pick its categories again. */
internal fun widgetSetup(
    context: Context,
    id: Int,
): PendingIntent =
    PendingIntent.getActivity(
        context,
        WIDGET_SETUP_REQUEST_BASE + id,
        Intent(context, WidgetSetupActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private const val WIDGET_SETUP_REQUEST_BASE = 10_000
