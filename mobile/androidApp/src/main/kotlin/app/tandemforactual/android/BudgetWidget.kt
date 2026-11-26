package app.tandemforactual.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetWidgetSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.budgetWidgetSummary
import app.tandemforactual.ui.core.format.formatBudgetCurrency
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen widget with what's ready to assign this month and a button to add a transaction. */
class BudgetWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!refreshesOnAction(intent.action)) return super.onReceive(context, intent)
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(ComponentName(appContext, BudgetWidget::class.java))
        if (ids.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                render(appContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(monthChangeIntent(context))
    }

    companion object {
        private const val ACTION_MONTH_CHANGED = "app.tandemforactual.android.WIDGET_MONTH_CHANGED"
        private val DATE_ACTIONS =
            setOf(ACTION_MONTH_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun refreshesOnAction(action: String?): Boolean = action in DATE_ACTIONS

        internal fun nextMonthStart(now: ZonedDateTime): ZonedDateTime =
            now
                .toLocalDate()
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay(now.zone)

        fun refresh(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, BudgetWidget::class.java))
            if (ids.isNotEmpty()) scope.launch { render(appContext, manager, ids) }
        }

        private suspend fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val summary =
                runCatching {
                    val database = openTandemDatabase(tandemDatabaseBuilder(context))
                    try {
                        budgetWidgetSummary(
                            LocalBudgetRepository(database) { System.currentTimeMillis() },
                            AppPreferencesStore(database).read(),
                            LocalDate.now().toString(),
                        )
                    } finally {
                        database.close()
                    }
                }.getOrNull()
            manager.updateAppWidget(ids, views(context, summary))
            scheduleMonthChange(context)
        }

        private fun scheduleMonthChange(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = nextMonthStart(ZonedDateTime.now()).toInstant().toEpochMilli()
            alarms.set(AlarmManager.RTC, at, monthChangeIntent(context))
        }

        private fun monthChangeIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, BudgetWidget::class.java).setAction(ACTION_MONTH_CHANGED),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun views(
            context: Context,
            summary: BudgetWidgetSummary?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_budget)
            views.setOnClickPendingIntent(R.id.widget_root, launch(context, null, 0))
            views.setOnClickPendingIntent(R.id.widget_add, launch(context, ACTION_ADD_TRANSACTION, 1))
            views.setTextViewText(R.id.widget_budget_name, summary?.budgetName ?: "Tandem")
            val locked = summary == null || summary.amountsHidden
            views.setViewVisibility(R.id.widget_locked, if (locked) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_amounts, if (locked) View.GONE else View.VISIBLE)
            views.setTextViewText(
                R.id.widget_locked,
                if (summary == null) "Open Tandem to choose a budget." else "Open Tandem to see your budget.",
            )
            if (!locked) {
                val ready = summary.readyToAssignMinor
                views.setTextViewText(R.id.widget_ready, formatAmount(ready, summary.displaySettings))
                val readyColor = if (ready != null && ready < 0) R.color.widget_negative else R.color.widget_text
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(R.id.widget_ready, "setTextColor", readyColor)
                } else {
                    views.setTextColor(R.id.widget_ready, context.getColor(readyColor))
                }
                views.setTextViewText(R.id.widget_spent, formatAmount(summary.spentMinor, summary.displaySettings))
            }
            return views
        }

        private fun launch(
            context: Context,
            action: String?,
            requestCode: Int,
        ): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java)
                    .setAction(action ?: Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
    }
}
