package app.tandemforactual.android

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

/** One tap opens a new transaction, in the account the widget was set to when it has one. It shows no amounts. */
class QuickAddWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, quickAddViews(context, it)) }
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        WidgetSettings.forget(context, appWidgetIds)
    }

    companion object {
        private const val QUICK_ADD_REQUEST_BASE = 20_000

        fun update(
            context: Context,
            widgetId: Int,
        ) {
            AppWidgetManager.getInstance(context).updateAppWidget(widgetId, quickAddViews(context, widgetId))
        }

        fun isQuickAdd(
            context: Context,
            widgetId: Int,
        ): Boolean {
            val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider
            return provider == ComponentName(context, QuickAddWidget::class.java)
        }

        private fun quickAddViews(
            context: Context,
            widgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            val account = WidgetSettings.account(context, widgetId)
            views.setTextViewText(R.id.widget_quick_add_label, WidgetSettings.accountName(context, widgetId) ?: "Add")
            views.setContentDescription(android.R.id.background, "Add transaction")
            views.setOnClickPendingIntent(
                android.R.id.background,
                openApp(context, ACTION_ADD_TRANSACTION, QUICK_ADD_REQUEST_BASE + widgetId, account),
            )
            return views
        }
    }
}
