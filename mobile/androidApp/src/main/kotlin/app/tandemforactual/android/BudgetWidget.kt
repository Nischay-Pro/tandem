package app.tandemforactual.android

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** A widget drawn from the budget on this device and redrawn after each sync and when the month changes. */
abstract class BudgetAmountWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        TandemWidgets.update(context, goAsync(), appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!TandemWidgets.refreshesOnAction(intent.action)) return super.onReceive(context, intent)
        TandemWidgets.update(context, goAsync(), null)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        WidgetSettings.forget(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        TandemWidgets.cancelMonthChangeIfUnused(context)
    }
}

/** Home-screen and lock-screen widget with what's ready to assign this month and a button to add a transaction. */
open class BudgetWidget : BudgetAmountWidget()

/** The same widget sized for a flip phone's cover screen. */
class BudgetCoverWidget : BudgetWidget()

/** What's left to spend this month in the categories someone picked. */
open class CategoryWidget : BudgetAmountWidget()

/** The categories widget sized for a flip phone's cover screen. */
class CategoryCoverWidget : CategoryWidget()
