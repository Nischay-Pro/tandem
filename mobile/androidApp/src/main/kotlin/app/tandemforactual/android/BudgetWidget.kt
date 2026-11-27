package app.tandemforactual.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.budgetWidgetSummary
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen and lock-screen widget with what's ready to assign this month and a button to add a transaction. */
open class BudgetWidget : AppWidgetProvider() {
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
        if (!refreshesOnAction(intent.action)) return super.onReceive(context, intent)
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = allWidgetIds(appContext, manager)
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
        val appContext = context.applicationContext
        if (allWidgetIds(appContext, AppWidgetManager.getInstance(appContext)).isEmpty()) {
            appContext.getSystemService(AlarmManager::class.java)?.cancel(monthChangeIntent(appContext))
        }
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
            val ids = allWidgetIds(appContext, manager)
            if (ids.isNotEmpty()) scope.launch { render(appContext, manager, ids) }
        }

        private fun allWidgetIds(
            context: Context,
            manager: AppWidgetManager,
        ): IntArray =
            listOf(BudgetWidget::class.java, BudgetCoverWidget::class.java)
                .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).asList() }
                .toIntArray()

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
            ids.forEach { manager.updateAppWidget(it, budgetWidgetViews(context, manager, it, summary)) }
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
    }
}

/** The same widget sized for a flip phone's cover screen. */
class BudgetCoverWidget : BudgetWidget()
