package app.tandemforactual.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetWidgetSummary
import app.tandemforactual.data.store.CategoryWidgetSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.budgetWidgetSummary
import app.tandemforactual.data.store.categoryWidgetSummary
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Draws every widget that shows budget amounts from the budget on this device; nothing here uses the network. */
internal object TandemWidgets {
    private const val ACTION_MONTH_CHANGED = "app.tandemforactual.android.WIDGET_MONTH_CHANGED"
    private val dateActions = setOf(ACTION_MONTH_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    private val budgetProviders = listOf(BudgetWidget::class.java, BudgetCoverWidget::class.java)
    private val categoryProviders = listOf(CategoryWidget::class.java, CategoryCoverWidget::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshesOnAction(action: String?): Boolean = action in dateActions

    fun nextMonthStart(now: ZonedDateTime): ZonedDateTime =
        now
            .toLocalDate()
            .withDayOfMonth(1)
            .plusMonths(1)
            .atStartOfDay(now.zone)

    /** Redraws every widget, after a sync or when the app leaves the screen. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch { render(appContext, null) }
    }

    /** Redraws [widgetIds], or every widget when null, while a broadcast stays alive for it. */
    fun update(
        context: Context,
        pending: BroadcastReceiver.PendingResult,
        widgetIds: IntArray?,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                render(appContext, widgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    /** Stops the month-change alarm once the last widget that shows amounts is removed. */
    fun cancelMonthChangeIfUnused(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        if (ids(appContext, manager, budgetProviders + categoryProviders, null).isEmpty()) {
            appContext.getSystemService(AlarmManager::class.java)?.cancel(monthChangeIntent(appContext))
        }
    }

    private suspend fun render(
        context: Context,
        only: IntArray?,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val budgetIds = ids(context, manager, budgetProviders, only)
        val categoryIds = ids(context, manager, categoryProviders, only)
        if (budgetIds.isEmpty() && categoryIds.isEmpty()) return
        val choices = categoryIds.associateWith { WidgetSettings.categories(context, it) }
        val data = runCatching { readWidgetData(context, budgetIds.isNotEmpty(), choices) }.getOrNull()
        budgetIds.forEach { manager.updateAppWidget(it, budgetWidgetViews(context, manager, it, data?.budget)) }
        categoryIds.forEach { manager.updateAppWidget(it, categoryWidgetViews(context, manager, it, data?.categories?.get(it))) }
        scheduleMonthChange(context)
    }

    private class WidgetData(
        val budget: BudgetWidgetSummary?,
        val categories: Map<Int, CategoryWidgetSummary?>,
    )

    private suspend fun readWidgetData(
        context: Context,
        includeBudget: Boolean,
        categoryChoices: Map<Int, List<String>>,
    ): WidgetData {
        val database = openTandemDatabase(tandemDatabaseBuilder(context))
        try {
            val repository = LocalBudgetRepository(database) { System.currentTimeMillis() }
            val preferences = AppPreferencesStore(database).read()
            val today = LocalDate.now().toString()
            val budget = if (includeBudget) budgetWidgetSummary(repository, preferences, today) else null
            val categories = categoryChoices.mapValues { (_, ids) -> categoryWidgetSummary(repository, preferences, today, ids) }
            return WidgetData(budget, categories)
        } finally {
            database.close()
        }
    }

    private fun ids(
        context: Context,
        manager: AppWidgetManager,
        providers: List<Class<*>>,
        only: IntArray?,
    ): List<Int> =
        providers
            .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).asList() }
            .filter { only == null || it in only }

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
