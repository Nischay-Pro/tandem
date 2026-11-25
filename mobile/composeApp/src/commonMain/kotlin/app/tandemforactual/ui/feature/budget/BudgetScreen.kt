@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.session.BUDGET_GROUP_ORDER_SCOPE
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.homeLayout
import app.tandemforactual.ui.core.state.isBudgetSectionCollapsed
import app.tandemforactual.ui.core.state.isCategoryPinned
import app.tandemforactual.ui.core.state.presentBudgetAssignment
import app.tandemforactual.ui.core.state.toggleBudgetSection
import app.tandemforactual.ui.core.ui.AutoAssignSheet
import app.tandemforactual.ui.core.ui.LiveUpcomingBillsCard
import app.tandemforactual.ui.core.ui.TransactionRow
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.rows.CollapsibleSectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit
import app.tandemforactual.ui.feature.budget.components.BudgetGroupListContext
import app.tandemforactual.ui.feature.budget.components.BudgetMonthEmptyCard
import app.tandemforactual.ui.feature.budget.components.BudgetMonthLoadingCard
import app.tandemforactual.ui.feature.budget.components.BudgetMonthSummaryCard
import app.tandemforactual.ui.feature.budget.components.BudgetPinnedCategoriesSection
import app.tandemforactual.ui.feature.budget.components.BudgetReorderActions
import app.tandemforactual.ui.feature.budget.components.BudgetScreenDock
import app.tandemforactual.ui.feature.budget.components.BudgetScreenSheets
import app.tandemforactual.ui.feature.budget.components.HoldSheetFigures
import app.tandemforactual.ui.feature.budget.components.budgetGroupItems
import app.tandemforactual.ui.feature.budget.components.budgetOverviewItems
import app.tandemforactual.ui.feature.budget.components.rememberBudgetReorderState
import app.tandemforactual.ui.feature.budget.components.rememberBudgetScreenDialogs
import app.tandemforactual.ui.feature.budget.components.rememberHoldSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
internal fun BudgetScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveTransactions: List<TransactionItem>,
    liveSchedules: List<LiveScheduleSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    onSyncNow: () -> Unit,
    onGroupReorderModeChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dialogs = rememberBudgetScreenDialogs(state.selectedBudgetId, state.selectedMonthId)
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        PageColumn {
            EmptyStateCard(
                "Choose a budget",
                "Open an Actual budget to view its plan and activity.",
                actionLabel = "Choose budget",
                onAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
            )
        }
        return
    }
    val reorder = rememberBudgetReorderState(state.selectedBudgetId, state.selectedMonthId)
    LaunchedEffect(reorder.selectedGroupOrderId) {
        onGroupReorderModeChange(reorder.selectedGroupOrderId != null)
    }
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val groups = visibleBudgetGroups(mobileSession, state.selectedBudgetId, liveBudgetMonth)
    val displayGroups = rememberOrderedGroups(state, mobileSession, groups, reorder.groupDrag)
    val readyToAssignMinor = liveBudgetMonth?.toBudgetMinor ?: 0
    val hold = rememberHoldSheetState(state.selectedBudgetId, state.selectedMonthId, readyToAssignMinor)
    val holdFigures =
        HoldSheetFigures(
            readyToAssignMinor = readyToAssignMinor,
            assignmentPresentation = presentBudgetAssignment(readyToAssignMinor),
            heldForNextMonthMinor = liveBudgetMonth?.heldForNextMonthMinor?.coerceAtLeast(0) ?: 0L,
            heldPending = liveBudgetMonth?.heldForNextMonthPending == true,
        )
    val context =
        BudgetGroupListContext(
            state = state,
            navigation = navigation,
            mobileSession = mobileSession,
            reorder = reorder,
            actions = BudgetReorderActions(state, mobileSession, scope, groups, reorder),
            isLiveBudget = isLiveBudget,
            isTrackingBudget = isTrackingBudget,
            compact = state.homeLayout == "Compact",
            billsDueMinor = billsStillDue(liveSchedules, platformTodayIsoDate()),
        )
    BudgetScrollToSummaryEffect(state)
    Box(Modifier.fillMaxSize()) {
        BudgetList(
            context = context,
            month = BudgetListMonth(liveBudgetMonth, groups, displayGroups, holdFigures),
            liveTransactions = liveTransactions,
            liveSchedules = liveSchedules,
            onSyncNow = onSyncNow,
            onCopyLastMonth = { dialogs.showCopyLastMonth = true },
            onPlanTemplates = { dialogs.showTemplates = true },
            onOpenHold = {
                mobileSession.feedback.clearBudgetActionMessage()
                hold.open(readyToAssignMinor, holdFigures.heldForNextMonthMinor)
            },
            onAutoAssign = { dialogs.showAutoAssign = true },
        )
        BudgetScreenDock(state, navigation, reorder, isLiveBudget, Modifier.align(Alignment.BottomEnd)) {
            mobileSession.feedback.clearBudgetActionMessage()
            dialogs.openCreateCategoryGroup()
        }
    }
    BudgetScreenSheets(dialogs, hold, state, mobileSession, groups, displayGroups, holdFigures, isLiveBudget, isTrackingBudget, scope)
    AutoAssignSheet(dialogs.showAutoAssign, state, mobileSession, liveBudgetMonth) { dialogs.showAutoAssign = false }
}

private class BudgetListMonth(
    val liveBudgetMonth: LiveBudgetMonthSummary?,
    val groups: List<LiveCategoryGroupSummary>,
    val displayGroups: List<LiveCategoryGroupSummary>,
    val figures: HoldSheetFigures,
)

private fun visibleBudgetGroups(
    mobileSession: TandemMobileSession,
    budgetId: String,
    liveBudgetMonth: LiveBudgetMonthSummary?,
): List<LiveCategoryGroupSummary> {
    val pendingDeletedGroupIds =
        mobileSession
            .budgetQueue
            .pendingBudgetMutationsFor(budgetId)
            .filter { it.kind == BudgetReorderKind.DELETE_CATEGORY_GROUP }
            .mapTo(mutableSetOf()) { it.itemId }
    return liveBudgetMonth?.groups.orEmpty().filterNot { it.id in pendingDeletedGroupIds }
}

@Composable
private fun rememberOrderedGroups(
    state: AppState,
    mobileSession: TandemMobileSession,
    groups: List<LiveCategoryGroupSummary>,
    groupDrag: DragReorderState,
): List<LiveCategoryGroupSummary> {
    val queuedGroupMoves = mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
    val groupReorderStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
    val queuedGroupOrder =
        budgetOrderForScreen(
            groups.map { it.id },
            groupReorderStatus,
            queuedGroupMoves,
        )
    val orderedGroupIds = groupDrag.currentOrder(queuedGroupOrder)
    return remember(groups, orderedGroupIds) {
        val groupsById = groups.associateBy { it.id }
        orderedGroupIds.mapNotNull(groupsById::get) + groups.filterNot { it.id in orderedGroupIds }
    }
}

@Composable
private fun BudgetScrollToSummaryEffect(state: AppState) {
    val routeListState = LocalRouteLazyListState.current
    LaunchedEffect(state.budgetScrollToSummaryRequested, routeListState) {
        val listState = routeListState ?: return@LaunchedEffect
        if (state.budgetScrollToSummaryRequested) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 3 }
            delay(1_000)
            listState.animateScrollToItem(2)
            state.budgetScrollToSummaryRequested = false
        }
    }
}

@Composable
private fun BudgetList(
    context: BudgetGroupListContext,
    month: BudgetListMonth,
    liveTransactions: List<TransactionItem>,
    liveSchedules: List<LiveScheduleSummary>,
    onSyncNow: () -> Unit,
    onCopyLastMonth: () -> Unit,
    onPlanTemplates: () -> Unit,
    onOpenHold: () -> Unit,
    onAutoAssign: () -> Unit,
) {
    val state = context.state
    val mobileSession = context.mobileSession
    val isLiveBudget = context.isLiveBudget
    val isTrackingBudget = context.isTrackingBudget
    val liveBudgetMonth = month.liveBudgetMonth
    val sectionSpacing = if (context.compact) 10.dp else 16.dp
    val groupReorderStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
    val pinnedCategories =
        remember(month.groups, state.pinnedCategoryIds) {
            month.groups.flatMap { group ->
                group.categories.filter { state.isCategoryPinned(it.id) }.map { group to it }
            }
        }
    val recentTransactions = remember(liveTransactions) { liveTransactions.filterNot(TransactionItem::isSplitParent) }
    PageLazyColumn(
        verticalSpacing = sectionSpacing,
        horizontalPadding = if (context.compact) 16.dp else 20.dp,
        isRefreshing = isLiveBudget && mobileSession.isSyncing,
        onRefresh = onSyncNow.takeIf { isLiveBudget },
    ) {
        budgetOverviewItems(
            state,
            context.navigation,
            mobileSession,
            isLiveBudget,
            isTrackingBudget,
            liveBudgetMonth,
            groupReorderStatus,
            onCopyLastMonth,
            onPlanTemplates,
        )
        if (
            isLiveBudget &&
            liveBudgetMonth == null &&
            !isTrackingBudget &&
            !mobileSession.isLoadingSelectedBudgetMonth &&
            mobileSession.selectedBudgetMonthRecord == null
        ) {
            item(key = "budget-month-empty") { BudgetMonthEmptyCard() }
        } else if (isLiveBudget && liveBudgetMonth == null && !isTrackingBudget) {
            item(key = "budget-month-loading") { BudgetMonthLoadingCard(state.selectedMonth) }
        } else {
            budgetMonthItems(context, month, pinnedCategories, sectionSpacing, onOpenHold, onAutoAssign)
        }
        budgetActivityItems(context, liveTransactions, liveSchedules, recentTransactions, sectionSpacing)
    }
}

private fun LazyListScope.budgetMonthItems(
    context: BudgetGroupListContext,
    month: BudgetListMonth,
    pinnedCategories: List<Pair<LiveCategoryGroupSummary, LiveCategorySummary>>,
    sectionSpacing: Dp,
    onOpenHold: () -> Unit,
    onAutoAssign: () -> Unit,
) {
    val state = context.state
    if (!context.isTrackingBudget) {
        val summaryKey = "live:${state.selectedBudgetId}:summary:${state.selectedMonthId}"
        item(key = summaryKey) {
            BudgetMonthSummaryCard(
                state = state,
                summaryKey = summaryKey,
                groups = month.groups,
                assignmentPresentation = month.figures.assignmentPresentation,
                heldForNextMonthMinor = month.figures.heldForNextMonthMinor,
                heldPending = month.figures.heldPending,
                isLiveBudget = context.isLiveBudget,
                billsDueMinor = context.billsDueMinor,
                sectionSpacing = sectionSpacing,
                onOpenHold = onOpenHold,
                onAutoAssign = onAutoAssign,
            )
        }
    }
    if (pinnedCategories.isNotEmpty()) {
        val pinnedSectionKey = "live:${state.selectedBudgetId}:pinned"
        val pinnedExpanded = !state.isBudgetSectionCollapsed(pinnedSectionKey)
        item(key = "$pinnedSectionKey:section") {
            BudgetPinnedCategoriesSection(state, context.navigation, pinnedCategories, pinnedSectionKey, pinnedExpanded, sectionSpacing)
        }
    }
    month.displayGroups.forEach { group ->
        budgetGroupItems(group, month.displayGroups, context)
    }
}

private fun LazyListScope.budgetActivityItems(
    context: BudgetGroupListContext,
    liveTransactions: List<TransactionItem>,
    liveSchedules: List<LiveScheduleSummary>,
    recentTransactions: List<TransactionItem>,
    sectionSpacing: Dp,
) {
    val state = context.state
    val navigation = context.navigation
    if (context.isLiveBudget && liveSchedules.isNotEmpty()) {
        item(key = "actual-upcoming-schedules") {
            LiveUpcomingBillsCard(state, navigation, liveSchedules)
        }
    }
    if (recentTransactions.isNotEmpty()) {
        val recentSectionKey = "live:${state.selectedBudgetId}:recent-activity"
        val recentExpanded = !state.isBudgetSectionCollapsed(recentSectionKey)
        item(key = "$recentSectionKey:section") {
            Column {
                CollapsibleSectionCard(
                    "Recent activity",
                    "${formatInteger(liveTransactions.size)} synced transactions",
                    "See all",
                    onClick = { openRecentActivity(state, navigation) },
                    expanded = recentExpanded,
                    onToggleExpanded = { state.toggleBudgetSection(recentSectionKey) },
                ) {}
                AnimatedVisibility(
                    visible = recentExpanded,
                    enter = tandemRevealEnter(tandemReduceMotion),
                    exit = tandemRevealExit(tandemReduceMotion),
                ) {
                    Column(
                        Modifier.padding(top = sectionSpacing),
                        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    ) {
                        recentTransactions.take(3).forEach { transaction ->
                            SurfaceCard {
                                TransactionRow(transaction) {
                                    openTransaction(
                                        state,
                                        navigation,
                                        transaction,
                                        isLive = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
