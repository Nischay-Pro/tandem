@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.format.rememberTodayIsoDate
import app.tandemforactual.ui.core.session.budget.accountClosedOverridesFor
import app.tandemforactual.ui.core.session.budget.accountNameOverridesFor
import app.tandemforactual.ui.core.session.budget.accountRecordsWithPendingCreates
import app.tandemforactual.ui.core.session.budget.categoryGroupIdOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryGroupNameOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryGroupVisibilityOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryNameOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryVisibilityOverridesFor
import app.tandemforactual.ui.core.session.budget.deletedCategoryIdsFor
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.session.budget.pendingCategoryBudgetOverrides
import app.tandemforactual.ui.core.session.budget.pendingCategoryCarryoverOverrides
import app.tandemforactual.ui.core.session.budget.pendingCategoryCreatesFor
import app.tandemforactual.ui.core.session.budget.pendingCategoryGroupCreatesFor
import app.tandemforactual.ui.core.state.buildLiveTransactionItems
import app.tandemforactual.ui.core.state.liveAccountSummaries
import app.tandemforactual.ui.core.state.liveBudgetMonthSummary
import app.tandemforactual.ui.core.state.liveScheduleSummaries
import app.tandemforactual.ui.core.state.liveUncategorizedItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rebuilds the live transaction, account and schedule lists from the session's records off the main thread. */
@Composable
internal fun LiveDataEffects(ctx: ShellContext) {
    val state = ctx.state
    val shell = ctx.shell
    val mobileSession = ctx.mobileSession
    LaunchedEffect(
        state.selectedBudgetId,
        mobileSession.budgetDisplaySettingsBudgetId,
        mobileSession.budgetDisplaySettings,
    ) {
        state.budgetDisplaySettings =
            mobileSession.budgetDisplaySettings.takeIf {
                mobileSession.budgetDisplaySettingsBudgetId == state.selectedBudgetId
            }
    }
    val accountRecords = mobileSession.budgetQueue.accountRecordsWithPendingCreates(state.selectedBudgetId, mobileSession.accountRecords)
    LiveTransactionEffects(ctx, accountRecords)
    UncategorizedEffect(ctx, accountRecords)
    val accountNameOverrides = mobileSession.budgetQueue.accountNameOverridesFor(state.selectedBudgetId)
    val accountClosedOverrides = mobileSession.budgetQueue.accountClosedOverridesFor(state.selectedBudgetId)
    LaunchedEffect(accountRecords, accountNameOverrides, accountClosedOverrides) {
        shell.liveAccounts =
            withContext(Dispatchers.Default) {
                liveAccountSummaries(accountRecords, mobileSession.accountGroupRecords, accountNameOverrides, accountClosedOverrides)
            }
    }
    LaunchedEffect(
        mobileSession.scheduleRecords,
        mobileSession.payeeRecords,
        accountRecords,
        mobileSession.schedules.pendingScheduleActions,
    ) {
        val schedules = mobileSession.scheduleRecords
        val payees = mobileSession.payeeRecords
        val pendingScheduleActions = mobileSession.schedules.pendingScheduleActions
        shell.liveSchedules =
            withContext(Dispatchers.Default) {
                liveScheduleSummaries(schedules, payees, accountRecords, pendingScheduleActions)
            }
    }
}

/** The live transaction and activity lists, rebuilt when their records change or the day turns over. */
@Composable
private fun LiveTransactionEffects(
    ctx: ShellContext,
    accountRecords: List<BudgetRecordEntity>,
) {
    val shell = ctx.shell
    val mobileSession = ctx.mobileSession
    val transactionRecords = mobileSession.transactionRecords
    val activityTransactionRecords = mobileSession.activity.activityTransactionRecords
    val activityTransactionRecordsVersion = mobileSession.activity.activityTransactionRecordsVersion
    val payeeRecords = mobileSession.payeeRecords
    val categoryRecords = mobileSession.categoryRecords
    val displaySettings = mobileSession.budgetDisplaySettings
    val todayIso = rememberTodayIsoDate()
    LaunchedEffect(
        transactionRecords,
        accountRecords,
        payeeRecords,
        categoryRecords,
        displaySettings,
        shell.merchantIconMatcher,
        todayIso,
    ) {
        shell.liveTransactions =
            withContext(Dispatchers.Default) {
                buildLiveTransactionItems(
                    transactionRecords,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                    shell.merchantIconMatcher,
                    todayIso,
                )
            }
    }
    LaunchedEffect(
        activityTransactionRecordsVersion,
        accountRecords,
        payeeRecords,
        categoryRecords,
        displaySettings,
        shell.merchantIconMatcher,
        todayIso,
    ) {
        val activityTransactionRecordsSnapshot = activityTransactionRecords.toList()
        shell.liveActivityTransactions =
            withContext(Dispatchers.Default) {
                buildLiveTransactionItems(
                    activityTransactionRecordsSnapshot,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                    shell.merchantIconMatcher,
                    todayIso,
                )
            }
    }
}

/** Rebuilds the selected month's budget summary with the queued, not yet synced category edits applied. */
@Composable
internal fun LiveBudgetMonthEffect(ctx: ShellContext) {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    val budgetQueue = mobileSession.budgetQueue
    val categoryVisibilityOverrides = budgetQueue.categoryVisibilityOverridesFor(state.selectedBudgetId)
    val categoryGroupNameOverrides = budgetQueue.categoryGroupNameOverridesFor(state.selectedBudgetId)
    val categoryNameOverrides = budgetQueue.categoryNameOverridesFor(state.selectedBudgetId)
    val categoryGroupIdOverrides = budgetQueue.categoryGroupIdOverridesFor(state.selectedBudgetId)
    val categoryGroupVisibilityOverrides = budgetQueue.categoryGroupVisibilityOverridesFor(state.selectedBudgetId)
    val deletedCategoryIds = budgetQueue.deletedCategoryIdsFor(state.selectedBudgetId)
    val pendingCategoryGroups = budgetQueue.pendingCategoryGroupCreatesFor(state.selectedBudgetId)
    val pendingCategories = budgetQueue.pendingCategoryCreatesFor(state.selectedBudgetId)
    val pendingBudgetMutations = budgetQueue.pendingBudgetMutationsFor(state.selectedBudgetId)
    val categoryBudgetOverrides = budgetQueue.pendingCategoryBudgetOverrides(state.selectedBudgetId, state.selectedMonthId)
    val categoryCarryoverOverrides = budgetQueue.pendingCategoryCarryoverOverrides(state.selectedBudgetId, state.selectedMonthId)
    LaunchedEffect(
        mobileSession.selectedBudgetMonthRecord,
        mobileSession.categoryRecords,
        mobileSession.categoryGroupRecords,
        categoryVisibilityOverrides,
        categoryGroupNameOverrides,
        categoryNameOverrides,
        categoryGroupIdOverrides,
        categoryGroupVisibilityOverrides,
        deletedCategoryIds,
        pendingCategoryGroups,
        pendingCategories,
        pendingBudgetMutations,
        categoryBudgetOverrides,
        categoryCarryoverOverrides,
        state.selectedMonthId,
    ) {
        val monthRecord =
            mobileSession.selectedBudgetMonthRecord?.takeIf {
                it.budgetId == state.selectedBudgetId && it.recordId == state.selectedMonthId
            }
        val monthCategories = mobileSession.categoryRecords
        val monthCategoryGroups = mobileSession.categoryGroupRecords
        ctx.shell.liveBudgetMonth =
            withContext(Dispatchers.Default) {
                liveBudgetMonthSummary(
                    monthRecord,
                    monthCategories,
                    monthCategoryGroups,
                    categoryVisibilityOverrides,
                    categoryGroupNameOverrides,
                    categoryNameOverrides,
                    categoryGroupIdOverrides,
                    categoryGroupVisibilityOverrides,
                    deletedCategoryIds,
                    pendingCategoryGroups,
                    pendingCategories,
                    categoryBudgetOverrides,
                    categoryCarryoverOverrides,
                    pendingBudgetMutations,
                )
            }
    }
}

/** Builds every transaction waiting for a category, for Home's count, its review inbox and the review screen. */
@Composable
private fun UncategorizedEffect(
    ctx: ShellContext,
    accountRecords: List<BudgetRecordEntity>,
) {
    val mobileSession = ctx.mobileSession
    val shell = ctx.shell
    val records = mobileSession.uncategorizedTransactionRecords
    val payeeRecords = mobileSession.payeeRecords
    val categoryRecords = mobileSession.categoryRecords
    val displaySettings = mobileSession.budgetDisplaySettings
    val todayIso = rememberTodayIsoDate()
    LaunchedEffect(records, accountRecords, payeeRecords, categoryRecords, displaySettings, shell.merchantIconMatcher, todayIso) {
        shell.liveUncategorized =
            withContext(Dispatchers.Default) {
                liveUncategorizedItems(
                    records,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                    shell.merchantIconMatcher,
                    todayIso,
                )
            }
    }
}
