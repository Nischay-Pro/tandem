@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.FloatingAction
import app.tandemforactual.ui.app.FloatingTransactionAction
import app.tandemforactual.ui.app.isLandscapeViewport
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.formatShortMonth
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.BUDGET_GROUP_ORDER_SCOPE
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.applyBudgetTemplate
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budget.clearBudgetReorderMessage
import app.tandemforactual.ui.core.session.budget.copyBudgetMonth
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryGroupCreate
import app.tandemforactual.ui.core.session.budget.enqueueBudgetReorder
import app.tandemforactual.ui.core.session.budget.holdBudgetForNextMonth
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.session.budget.resetBudgetHold
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.session.reorderAccessibilityAction
import app.tandemforactual.ui.core.session.reorderModeConsumesItemTap
import app.tandemforactual.ui.core.session.shouldShowBudgetCreateAction
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.categoryCountLabel
import app.tandemforactual.ui.core.state.categoryIconFor
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.homeLayout
import app.tandemforactual.ui.core.state.isBudgetSectionCollapsed
import app.tandemforactual.ui.core.state.isCategoryPinned
import app.tandemforactual.ui.core.state.toggleBudgetSection
import app.tandemforactual.ui.design.buttons.PeriodStepper
import app.tandemforactual.ui.design.buttons.SelectableTile
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.MAX_TRANSACTION_AMOUNT_MINOR_UNITS
import app.tandemforactual.ui.design.inputs.amountDigitExceedsLimit
import app.tandemforactual.ui.design.inputs.appendAmountDigit
import app.tandemforactual.ui.design.inputs.calculateAmountMinorUnits
import app.tandemforactual.ui.design.inputs.removeAmountDigit
import app.tandemforactual.ui.design.money.AvailableAmountPill
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.CollapsibleSectionCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.AttentionBanner
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.TransactionAmountField
import app.tandemforactual.ui.design.templates.TransactionAmountKeypad
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemFloatingEnter
import app.tandemforactual.ui.design.theme.tandemFloatingExit
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit
import app.tandemforactual.ui.feature.schedules.LiveUpcomingBillsCard
import app.tandemforactual.ui.feature.transactions.TransactionRow
import app.tandemforactual.ui.feature.transactions.daysInMonth
import app.tandemforactual.ui.feature.transactions.openTransaction
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val palette = tandemPalette
    val scope = rememberCoroutineScope()
    var showCopyLastMonthDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var showBudgetTemplatesDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var showNextMonthHoldDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var showCreateCategoryGroupDialog by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var isCreatingCategoryGroup by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var categoryGroupNameDraft by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    var newCategoryGroupIsIncome by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var holdSheetMounted by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var budgetTemplateNameDraft by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        PageColumn {
            EmptyState(
                "Choose a budget",
                "Open an Actual budget to view its plan and activity.",
                actionLabel = "Choose budget",
                onAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
            )
        }
        return
    }
    val routeScrollState = LocalRouteScrollState.current
    val routeListState = LocalRouteLazyListState.current
    val createActionsCompact by
        remember(routeScrollState, routeListState) {
            derivedStateOf {
                (routeScrollState?.value ?: 0) > 72 ||
                    routeListState?.firstVisibleItemIndex?.let { it > 0 } == true ||
                    (routeListState?.firstVisibleItemScrollOffset ?: 0) > 72
            }
        }
    val windowSize = LocalWindowInfo.current.containerSize
    val isLandscape = isLandscapeViewport(windowSize.width, windowSize.height)
    var selectedGroupOrderId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    var selectedBudgetCategoryOrderId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    var selectedBudgetCategoryGroupId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    val budgetCategoryDrag = remember(state.selectedBudgetId) { DragReorderState() }
    LaunchedEffect(selectedGroupOrderId) {
        onGroupReorderModeChange(selectedGroupOrderId != null)
    }
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val compact = state.homeLayout == "Compact"
    val sectionSpacing = if (compact) 10.dp else 16.dp
    val pendingDeletedGroupIds =
        mobileSession
            .budgetQueue
            .pendingBudgetMutationsFor(state.selectedBudgetId)
            .filter { it.kind == BudgetReorderKind.DELETE_CATEGORY_GROUP }
            .mapTo(mutableSetOf()) { it.itemId }
    val groups = liveBudgetMonth?.groups.orEmpty().filterNot { it.id in pendingDeletedGroupIds }
    val queuedGroupMoves = mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
    val groupReorderStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
    val groupDrag = remember(state.selectedBudgetId, state.selectedMonthId) { DragReorderState() }
    val queuedGroupOrder =
        budgetOrderForScreen(
            groups.map { it.id },
            groupReorderStatus,
            queuedGroupMoves,
        )
    val orderedGroupIds = groupDrag.currentOrder(queuedGroupOrder)
    val orderedGroups =
        remember(groups, orderedGroupIds) {
            val groupsById = groups.associateBy { it.id }
            orderedGroupIds.mapNotNull(groupsById::get) + groups.filterNot { it.id in orderedGroupIds }
        }
    val currentBudgetTemplates =
        remember(state.budgetTemplatesJson, state.selectedBudgetId) {
            decodeBudgetTemplates(state.budgetTemplatesJson).filter { it.budgetId == state.selectedBudgetId }
        }
    val currentTemplateAssignments =
        remember(groups) {
            groups.flatMap { group -> group.categories }.map { it.id to it.assignedMinor }
        }
    val recentTransactions = remember(liveTransactions) { liveTransactions.filterNot(TransactionItem::isSplitParent) }
    val readyToAssignMinor = liveBudgetMonth?.toBudgetMinor ?: 0
    val assignmentPresentation = presentBudgetAssignment(readyToAssignMinor)
    val displayedAssignmentMinor = assignmentPresentation.amountMinor
    val heldForNextMonthMinor = liveBudgetMonth?.heldForNextMonthMinor?.coerceAtLeast(0) ?: 0L
    var nextMonthHoldAmountMinor by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) {
        mutableStateOf(readyToAssignMinor.coerceAtLeast(0))
    }
    var holdKeypadVisible by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var holdCalculatorAccumulator by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf<Long?>(null) }
    var holdCalculatorOperator by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf<String?>(null) }
    var holdEnterNewOperand by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var holdAmountMessage by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf<String?>(null) }
    val readyToAssignColor =
        when (assignmentPresentation.kind) {
            BudgetAssignmentKind.OVERBUDGETED -> palette.negative
            BudgetAssignmentKind.TO_BUDGET -> palette.positive
            BudgetAssignmentKind.ALL_ASSIGNED -> palette.muted
        }
    val summaryKey = "live:${state.selectedBudgetId}:summary:${state.selectedMonthId}"
    val displayGroups = orderedGroups

    fun latestQueuedGroupOrder(): List<String> =
        budgetOrderForScreen(
            groups.map { it.id },
            mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE),
            mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE),
        )

    fun latestQueuedCategoryOrder(group: LiveCategoryGroupSummary): List<String> =
        budgetOrderForScreen(
            group.categories.map { it.id },
            mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, group.id),
            mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, group.id),
        )

    fun saveGroupOrder(
        groupId: String,
        requestedOrder: List<String>,
    ) {
        // Actual always lists income groups after expense groups, so keep that on screen too.
        val incomeGroupIds = groups.filter { it.isIncome }.mapTo(mutableSetOf()) { it.id }
        val nextOrder = requestedOrder.filterNot { it in incomeGroupIds } + requestedOrder.filter { it in incomeGroupIds }
        if (nextOrder == latestQueuedGroupOrder()) {
            // Nothing changes (for example, Income dropped among expense groups).
            groupDrag.releaseHeldDrop()
            return
        }
        scope.launch {
            mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
            mobileSession.feedback.clearBudgetActionMessage()
            val saved =
                mobileSession.budgetQueue.enqueueBudgetReorder(
                    state.selectedBudgetId,
                    BUDGET_GROUP_ORDER_SCOPE,
                    BudgetReorderKind.CATEGORY_GROUP,
                    groupId,
                    itemTargetIdAfterMove(nextOrder, groupId),
                    nextOrder,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                    baseOrder = groups.map { it.id },
                )
            if (!saved) groupDrag.releaseHeldDrop()
        }
    }

    fun enqueueGroupMove(
        groupId: String,
        direction: Int,
    ) {
        val oldOrder = groupDrag.currentOrder(latestQueuedGroupOrder())
        val nextOrder = reorderItemToIndex(oldOrder, groupId, oldOrder.indexOf(groupId) + direction)
        if (oldOrder == nextOrder) return
        saveGroupOrder(groupId, nextOrder)
    }
    val pinnedCategories =
        remember(groups, state.pinnedCategoryIds) {
            groups.flatMap { group ->
                group.categories.filter { state.isCategoryPinned(it.id) }.map { group to it }
            }
        }

    val showCreateActions =
        shouldShowBudgetCreateAction(
            isLiveBudget,
            selectedGroupOrderId != null || selectedBudgetCategoryOrderId != null,
        )
    val showTransactionAction = showCreateActions && !isLandscape
    val actionDockVisible = showCreateActions
    LaunchedEffect(state.budgetScrollToSummaryRequested, routeListState) {
        val listState = routeListState ?: return@LaunchedEffect
        if (state.budgetScrollToSummaryRequested) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 3 }
            delay(1_000)
            listState.animateScrollToItem(2)
            state.budgetScrollToSummaryRequested = false
        }
    }
    var actionDockScrolledAway by remember { mutableStateOf(false) }
    val dockThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    LaunchedEffect(routeListState, dockThresholdPx) {
        val listState = routeListState ?: return@LaunchedEffect
        var previous = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        var travel = 0f
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { current ->
            val step = scrollStep(previous, current, dockThresholdPx)
            travel = if (step == 0f || (step > 0) == (travel > 0)) travel + step else step
            actionDockScrolledAway = floatingActionsHidden(actionDockScrolledAway, travel, current, dockThresholdPx)
            previous = current
        }
    }

    Box(Modifier.fillMaxSize()) {
        PageLazyColumn(
            verticalSpacing = sectionSpacing,
            horizontalPadding = if (compact) 16.dp else 20.dp,
            isRefreshing = isLiveBudget && mobileSession.isSyncing,
            onRefresh = onSyncNow.takeIf { isLiveBudget },
        ) {
            if (isLiveBudget) {
                if (!mobileSession.isAuthenticated) {
                    item(key = "budget-reauthentication-alert") {
                        AnimatedReveal(visible = true) {
                            AttentionBanner(
                                "Sign in to sync",
                                "This budget is saved on this device. Sign in again to reconnect and sync it.",
                                "Sign in",
                            ) {
                                navigation.open(AppRoute.SIGN_IN)
                            }
                        }
                    }
                }
                budgetPendingChangeBannerItems(state, navigation, mobileSession.pendingWrites)
            }
            item(key = "budget-overview-title") {
                Heading("Overview")
            }
            if (mobileSession.syncComplete && !state.hasDismissedBudgetHint) {
                item(key = "budget-getting-started-hint") {
                    GettingStartedCard { state.hasDismissedBudgetHint = true }
                }
            }
            if (isLiveBudget) {
                groupReorderStatus.message?.let { message ->
                    item(key = "budget-group-reorder-message") {
                        InlineMessage(
                            message,
                            if (groupReorderStatus.warning) InlineMessageKind.WARNING else InlineMessageKind.ERROR,
                            actionLabel = "Try again".takeIf { groupReorderStatus.pendingCount > 0 },
                            onAction =
                                {
                                    mobileSession.budgetQueue.retryQueuedBudgetReorders(
                                        state.selectedBudgetId,
                                        state.encryptedPassword.takeIf(String::isNotBlank),
                                    )
                                }.takeIf { groupReorderStatus.pendingCount > 0 },
                        )
                    }
                }
            }
            if (isLiveBudget && !isTrackingBudget && liveBudgetMonth != null) {
                item(key = "budget-copy-last-month") {
                    SurfaceCard {
                        Column(Modifier.fillMaxWidth().padding(TandemSpacing.small)) {
                            TandemActionButton(
                                "Copy last month",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.CalendarDays,
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                                onClick = {
                                    mobileSession.feedback.clearBudgetActionMessage()
                                    showCopyLastMonthDialog = true
                                },
                            )
                            Spacer(Modifier.height(TandemSpacing.extraSmall))
                            TandemActionButton(
                                "Plan templates",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.CalendarDays,
                                enabled = !mobileSession.isPerformingBudgetAction,
                                onClick = {
                                    mobileSession.feedback.clearBudgetActionMessage()
                                    showBudgetTemplatesDialog = true
                                },
                            )
                        }
                    }
                }
            }
            if (isTrackingBudget) {
                item(key = "budget-tracking-mode") {
                    SurfaceCard {
                        ListRow(
                            "Tracking budget",
                            "Actual does not assign money to categories in this budget.",
                            leading = RowLeading.Icon(Lucide.ChartNoAxesCombined),
                        )
                    }
                }
            }
            if (
                isLiveBudget &&
                liveBudgetMonth == null &&
                !isTrackingBudget &&
                !mobileSession.isLoadingSelectedBudgetMonth &&
                mobileSession.selectedBudgetMonthRecord == null
            ) {
                item(key = "budget-month-empty") {
                    SurfaceCard {
                        EmptyState(
                            "No plan for this month",
                            "Sync a month available in Actual, or choose a different planning period.",
                            modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                        )
                    }
                }
            } else if (isLiveBudget && liveBudgetMonth == null && !isTrackingBudget) {
                item(key = "budget-month-loading") {
                    SurfaceCard {
                        ListRow(
                            "Loading ${state.selectedMonth}",
                            "Checking the saved plan on this device.",
                            leading = RowLeading.Icon(Lucide.CalendarDays),
                        )
                    }
                }
            } else {
                if (!isTrackingBudget) {
                    item(key = summaryKey) {
                        val summaryExpanded = !state.isBudgetSectionCollapsed(summaryKey)
                        Column {
                            CollapsibleSectionCard(
                                "${state.selectedMonth} summary",
                                "${categoryCountLabel(groups.sumOf { it.categories.size })} · ${assignmentPresentation.label}",
                                actionContent = {
                                    PrivacyAmountText(
                                        state.formatCurrency(displayedAssignmentMinor),
                                        privacyModeEnabled = state.privacyModeEnabled,
                                        fractionDigits = state.currencyFractionDigitsForDisplay,
                                        color = readyToAssignColor,
                                        fontSize = TandemTypography.label,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                    )
                                },
                                actionColor = readyToAssignColor,
                                expanded = summaryExpanded,
                                onToggleExpanded = { state.toggleBudgetSection(summaryKey) },
                            ) {}
                            AnimatedVisibility(
                                visible = summaryExpanded,
                                enter = tandemRevealEnter(tandemReduceMotion),
                                exit = tandemRevealExit(tandemReduceMotion),
                            ) {
                                SurfaceCard(Modifier.padding(top = sectionSpacing)) {
                                    Column {
                                        ListRow(
                                            assignmentPresentation.label,
                                            if (isLiveBudget) state.selectedMonth else "On track",
                                            leading = RowLeading.Icon(Lucide.WalletCards),
                                            options = ListRowOptions(trailingColor = readyToAssignColor),
                                            trailingContent = {
                                                PrivacyAmountText(
                                                    state.formatCurrency(displayedAssignmentMinor),
                                                    privacyModeEnabled = state.privacyModeEnabled,
                                                    fractionDigits = state.currencyFractionDigitsForDisplay,
                                                    color = readyToAssignColor,
                                                    fontSize = TandemTypography.compactBody,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            },
                                        )
                                        if (isLiveBudget) {
                                            DividerLine()
                                            ListRow(
                                                "Hold for next month",
                                                leading = RowLeading.Icon(Lucide.CalendarDays),
                                                trailingContent = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                                                    ) {
                                                        if (liveBudgetMonth?.heldForNextMonthPending == true) PendingBudgetSyncIcon()
                                                        PrivacyAmountText(
                                                            state.formatCurrency(heldForNextMonthMinor),
                                                            privacyModeEnabled = state.privacyModeEnabled,
                                                            fractionDigits = state.currencyFractionDigitsForDisplay,
                                                            color = palette.muted,
                                                            fontSize = TandemTypography.compactBody,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    nextMonthHoldAmountMinor =
                                                        suggestedHoldAmount(readyToAssignMinor, heldForNextMonthMinor)
                                                    holdCalculatorAccumulator = null
                                                    holdCalculatorOperator = null
                                                    holdEnterNewOperand = false
                                                    holdAmountMessage = null
                                                    holdKeypadVisible = true
                                                    mobileSession.feedback.clearBudgetActionMessage()
                                                    showNextMonthHoldDialog = true
                                                    holdSheetMounted = true
                                                },
                                            )
                                        }
                                        val today = remember { platformTodayIsoDate() }
                                        val left =
                                            if (isLiveBudget && state.selectedMonthId == today.take(7)) {
                                                stillToSpend(
                                                    groups,
                                                    today.drop(8).take(2).toIntOrNull() ?: 0,
                                                    daysInMonth(state.selectedMonthId) ?: 0,
                                                )
                                            } else {
                                                null
                                            }
                                        if (left != null) {
                                            DividerLine()
                                            ListRow(
                                                "You can still spend",
                                                if (state.privacyModeEnabled) {
                                                    null
                                                } else {
                                                    "About ${state.formatCurrency(
                                                        left.perDayMinor,
                                                    )} a day"
                                                },
                                                leading = RowLeading.Icon(Lucide.Gauge),
                                                trailingContent = {
                                                    PrivacyAmountText(
                                                        state.formatCurrency(left.totalMinor),
                                                        privacyModeEnabled = state.privacyModeEnabled,
                                                        fractionDigits = state.currencyFractionDigitsForDisplay,
                                                        color = palette.text,
                                                        fontSize = TandemTypography.compactBody,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (pinnedCategories.isNotEmpty()) {
                    val pinnedSectionKey = "live:${state.selectedBudgetId}:pinned"
                    val pinnedExpanded = !state.isBudgetSectionCollapsed(pinnedSectionKey)
                    item(key = "$pinnedSectionKey:section") {
                        Column {
                            CollapsibleSectionCard(
                                "Pinned",
                                categoryCountLabel(pinnedCategories.size),
                                expanded = pinnedExpanded,
                                onToggleExpanded = { state.toggleBudgetSection(pinnedSectionKey) },
                            ) {}
                            AnimatedVisibility(
                                visible = pinnedExpanded,
                                enter = tandemRevealEnter(tandemReduceMotion),
                                exit = tandemRevealExit(tandemReduceMotion),
                            ) {
                                Column(
                                    Modifier.padding(top = sectionSpacing),
                                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                                ) {
                                    pinnedCategories.forEach { (group, category) ->
                                        SurfaceCard {
                                            ListRow(
                                                category.name,
                                                group.name,
                                                leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                                                trailingContent = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                                                    ) {
                                                        if (category.pendingChanges) PendingBudgetSyncIcon()
                                                        PrivacyAmountText(
                                                            state.formatCurrency(category.balanceMinor),
                                                            privacyModeEnabled = state.privacyModeEnabled,
                                                            fractionDigits = state.currencyFractionDigitsForDisplay,
                                                            color = palette.text,
                                                            fontSize = TandemTypography.compactBody,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    state.selectedCategoryGroupId = group.id
                                                    state.selectedCategoryId = category.id
                                                    navigation.open(AppRoute.CATEGORY_DETAIL)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                displayGroups.forEach { group ->
                    val groupSectionKey = "live:${state.selectedBudgetId}:group:${group.id}"
                    val groupExpanded = selectedGroupOrderId == null && !state.isBudgetSectionCollapsed(groupSectionKey)
                    val isReordering = selectedGroupOrderId == group.id
                    val groupIndex = displayGroups.indexOfFirst { it.id == group.id }
                    val categoryOrderStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, group.id)
                    val queuedCategoryMoves = mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, group.id)
                    val serverCategoryOrder = group.categories.map { it.id }
                    val queuedCategoryOrder = budgetOrderForScreen(serverCategoryOrder, categoryOrderStatus, queuedCategoryMoves)
                    val categoryOrderIds =
                        if (selectedBudgetCategoryGroupId == group.id) {
                            budgetCategoryDrag.currentOrder(queuedCategoryOrder)
                        } else {
                            queuedCategoryOrder
                        }
                    val categoriesById = group.categories.associateBy { it.id }
                    val orderedCategories =
                        categoryOrderIds.mapNotNull(categoriesById::get) + group.categories.filterNot { it.id in categoryOrderIds }
                    item(key = "$groupSectionKey:section") {
                        fun startDrag() {
                            selectedBudgetCategoryOrderId = null
                            selectedBudgetCategoryGroupId = null
                            selectedGroupOrderId = group.id
                            groupDrag.start(groupDrag.currentOrder(latestQueuedGroupOrder()), group.id)
                        }

                        fun dropDrag() {
                            val result = groupDrag.finish() ?: return
                            if (result.startingOrder != result.finalOrder) {
                                saveGroupOrder(group.id, result.finalOrder)
                            }
                        }
                        SurfaceCard(
                            dragReorderItemModifier(
                                itemId = group.id,
                                dragState = groupDrag,
                                reduceMotion = tandemReduceMotion,
                                dragEnabled = isLiveBudget && !state.readOnlyMode && displayGroups.size > 1,
                                onDragStarted = ::startDrag,
                                onDragBy = { groupDrag.dragBy(it) },
                                onDragStopped = ::dropDrag,
                            ).combinedClickable(
                                role = Role.Button,
                                onClickLabel = "Open ${group.name}",
                                onClick = {
                                    if (selectedGroupOrderId != null) {
                                        selectedGroupOrderId = null
                                    } else {
                                        state.selectedCategoryGroupId = group.id
                                        navigation.open(AppRoute.CATEGORY_GROUP)
                                    }
                                },
                            ).then(
                                if (isLiveBudget && !state.readOnlyMode && displayGroups.size > 1) {
                                    Modifier.reorderAccessibilityAction(group.name) {
                                        selectedBudgetCategoryOrderId = null
                                        selectedBudgetCategoryGroupId = null
                                        selectedGroupOrderId = group.id
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                            animateSizeChanges = false,
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                                    ) {
                                        Text(
                                            group.name,
                                            color = palette.text,
                                            fontSize = TandemTypography.body,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                        when {
                                            isReordering || isTrackingBudget -> {
                                                Text(
                                                    categoryCountLabel(group.categories.size),
                                                    color = palette.muted,
                                                    fontSize = TandemTypography.caption,
                                                    maxLines = 1,
                                                )
                                            }
                                            else -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                                                    PrivacyAmountText(
                                                        state.formatCurrency(group.assignedMinor),
                                                        privacyModeEnabled = state.privacyModeEnabled,
                                                        fractionDigits = state.currencyFractionDigitsForDisplay,
                                                        color = palette.muted,
                                                        fontSize = TandemTypography.caption,
                                                    )
                                                    Text(
                                                        "assigned · ${categoryCountLabel(group.categories.size)}",
                                                        color = palette.muted,
                                                        fontSize = TandemTypography.caption,
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        }
                                        if (state.budgetShowProgressBars && !isTrackingBudget) {
                                            val groupSpent = kotlin.math.abs(group.activityMinor)
                                            val groupRemaining = group.balanceMinor.coerceAtLeast(0L)
                                            val groupTotal = maxOf(group.assignedMinor, groupSpent + groupRemaining, groupSpent)
                                            val groupProgress =
                                                if (groupTotal > 0L) {
                                                    (groupSpent.toDouble() / groupTotal.toDouble()).toFloat().coerceIn(0f, 1f)
                                                } else {
                                                    0f
                                                }
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(CircleShape)
                                                    .background(palette.accent.copy(alpha = 0.18f)),
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth(groupProgress)
                                                        .height(4.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (group.balanceMinor < 0L) palette.warning else palette.accent,
                                                        ),
                                                )
                                            }
                                        }
                                    }
                                    if (isReordering && isLiveBudget) {
                                        BudgetItemReorderActions(
                                            canMoveUp = groupIndex > 0,
                                            canMoveDown = groupIndex >= 0 && groupIndex < displayGroups.lastIndex,
                                            enabled = !state.readOnlyMode,
                                            itemName = group.name,
                                            onDragStarted = ::startDrag,
                                            onDragBy = { groupDrag.dragBy(it) },
                                            onDragStopped = ::dropDrag,
                                            onMoveUp = {
                                                enqueueGroupMove(group.id, -1)
                                            },
                                            onMoveDown = {
                                                enqueueGroupMove(group.id, 1)
                                            },
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                                        ) {
                                            PrivacyAmountText(
                                                state.formatCurrency(group.balanceMinor),
                                                privacyModeEnabled = state.privacyModeEnabled,
                                                fractionDigits = state.currencyFractionDigitsForDisplay,
                                                color = palette.accent,
                                                fontSize = TandemTypography.compactBody,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                if (isTrackingBudget) "Balance" else "Available",
                                                color = palette.muted,
                                                fontSize = TandemTypography.micro,
                                                maxLines = 1,
                                            )
                                        }
                                        TandemIconButton(
                                            if (groupExpanded) Lucide.ChevronDown else Lucide.ChevronRight,
                                            if (groupExpanded) "Collapse ${group.name}" else "Expand ${group.name}",
                                            onClick = { state.toggleBudgetSection(groupSectionKey) },
                                        )
                                    }
                                }
                                DividerLine()
                            }
                        }
                    }
                    if (groupExpanded) {
                        items(orderedCategories, key = { "budget-category:${group.id}:${it.id}" }) { category ->
                            val categoryIndex = orderedCategories.indexOfFirst { it.id == category.id }
                            val categoryIsReordering = selectedBudgetCategoryOrderId == category.id

                            fun startCategoryDrag() {
                                selectedGroupOrderId = null
                                selectedBudgetCategoryGroupId = group.id
                                selectedBudgetCategoryOrderId = category.id
                                mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, group.id)
                                mobileSession.feedback.clearBudgetActionMessage()
                                budgetCategoryDrag.start(budgetCategoryDrag.currentOrder(latestQueuedCategoryOrder(group)), category.id)
                            }

                            fun dragCategoryBy(delta: Float) {
                                if (budgetCategoryDrag.draggedItemId == category.id) {
                                    budgetCategoryDrag.dragBy(delta)
                                }
                            }

                            fun dropCategoryDrag() {
                                budgetCategoryDrag.finish()?.let { result ->
                                    if (result.itemId == category.id && result.finalOrder != result.startingOrder) {
                                        scope.launch {
                                            val saved =
                                                mobileSession.budgetQueue.enqueueBudgetReorder(
                                                    state.selectedBudgetId,
                                                    group.id,
                                                    BudgetReorderKind.CATEGORY,
                                                    result.itemId,
                                                    itemTargetIdAfterMove(result.finalOrder, result.itemId),
                                                    result.finalOrder,
                                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                                    baseOrder = serverCategoryOrder,
                                                )
                                            if (!saved) budgetCategoryDrag.releaseHeldDrop()
                                        }
                                    }
                                }
                            }

                            fun moveCategory(direction: Int) {
                                val currentOrder = budgetCategoryDrag.currentOrder(latestQueuedCategoryOrder(group))
                                val nextOrder = reorderItemToIndex(currentOrder, category.id, currentOrder.indexOf(category.id) + direction)
                                if (nextOrder == currentOrder) return
                                scope.launch {
                                    mobileSession.budgetQueue.enqueueBudgetReorder(
                                        state.selectedBudgetId,
                                        group.id,
                                        BudgetReorderKind.CATEGORY,
                                        category.id,
                                        itemTargetIdAfterMove(nextOrder, category.id),
                                        nextOrder,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                        baseOrder = serverCategoryOrder,
                                    )
                                }
                            }

                            val categoryCardModifier =
                                dragReorderItemModifier(
                                    itemId = category.id,
                                    dragState = budgetCategoryDrag,
                                    reduceMotion = tandemReduceMotion,
                                    dragEnabled = !state.readOnlyMode && orderedCategories.size > 1,
                                    onDragStarted = ::startCategoryDrag,
                                    onDragBy = ::dragCategoryBy,
                                    onDragStopped = ::dropCategoryDrag,
                                )
                            BudgetCategoryCard(
                                state = state,
                                category = category,
                                compact = compact,
                                modifier = categoryCardModifier,
                                isReordering = categoryIsReordering,
                                canMoveUp = categoryIndex > 0,
                                canMoveDown = categoryIndex >= 0 && categoryIndex < orderedCategories.lastIndex,
                                onMoveUp = { moveCategory(-1) },
                                onMoveDown = { moveCategory(1) },
                                onDragStarted = ::startCategoryDrag,
                                onDragBy = ::dragCategoryBy,
                                onDragStopped = ::dropCategoryDrag,
                                onClick = {
                                    if (reorderModeConsumesItemTap(selectedBudgetCategoryOrderId)) {
                                        selectedBudgetCategoryOrderId = null
                                    } else {
                                        state.selectedCategoryGroupId = group.id
                                        state.selectedCategoryId = category.id
                                        navigation.open(AppRoute.CATEGORY_DETAIL)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (isLiveBudget && liveSchedules.isNotEmpty()) {
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
                            "${liveTransactions.size} synced transactions",
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
        AnimatedVisibility(
            visible = actionDockVisible && !actionDockScrolledAway,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = tandemFloatingEnter(tandemReduceMotion),
            exit = tandemFloatingExit(tandemReduceMotion),
        ) {
            Row(
                Modifier
                    .padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingAction(
                    compact = true,
                    enabled = !state.readOnlyMode,
                    label = "Add group",
                    contentDescription = "Add category group",
                    icon = Lucide.Tags,
                    outlined = true,
                    onClick = {
                        categoryGroupNameDraft = ""
                        newCategoryGroupIsIncome = false
                        mobileSession.feedback.clearBudgetActionMessage()
                        showCreateCategoryGroupDialog = true
                    },
                )
                if (showTransactionAction) {
                    FloatingTransactionAction(
                        compact = createActionsCompact,
                        enabled = !state.readOnlyMode,
                        onClick = { navigation.newTransaction(null) },
                    )
                }
            }
        }
    }
    TandemTaskSheet(
        visible = showCopyLastMonthDialog && isLiveBudget && !isTrackingBudget,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showCopyLastMonthDialog = false },
        onDismissed = {},
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                TandemModalHeader(
                    title = "Copy last month",
                    leadingIcon = Lucide.CalendarDays,
                    enabled = !mobileSession.isPerformingBudgetAction,
                    onClose = { if (!mobileSession.isPerformingBudgetAction) showCopyLastMonthDialog = false },
                )
                Text(
                    "Replace ${state.selectedMonth} assignments with last month’s amounts?",
                    color = palette.muted,
                    fontSize = TandemTypography.body,
                )
                budgetActionFeedbackMessage(mobileSession)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.weight(1f),
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { showCopyLastMonthDialog = false }
                    TandemActionButton(
                        if (mobileSession.isPerformingBudgetAction) "Copying…" else "Copy",
                        TandemActionButtonTone.DESTRUCTIVE,
                        modifier = Modifier.weight(1f),
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        loading = mobileSession.isPerformingBudgetAction,
                    ) {
                        scope.launch {
                            val copied =
                                mobileSession.budgetEdits.copyBudgetMonth(
                                    state.selectedBudgetId,
                                    state.selectedMonthId,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (copied) showCopyLastMonthDialog = false
                        }
                    }
                }
            }
        }
    }
    val canSaveCurrentTemplate = currentTemplateAssignments.isNotEmpty() && currentTemplateAssignments.size <= 500
    TandemTaskSheet(
        visible = showBudgetTemplatesDialog && isLiveBudget && !isTrackingBudget,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showBudgetTemplatesDialog = false },
        onDismissed = {},
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                    Text(
                        "Plan templates",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.sectionTitle,
                        fontFamily = tandemAlloFamily(),
                        fontWeight = FontWeight.Bold,
                    )
                    TandemIconButton(
                        Lucide.X,
                        "Close",
                        sizeDp = 40,
                        iconSizeDp = 18,
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { showBudgetTemplatesDialog = false }
                }
                TextFieldCard(
                    "Template name",
                    budgetTemplateNameDraft,
                    { budgetTemplateNameDraft = it.take(60) },
                    TextFieldOptions(placeholder = "Summer plan"),
                )
                TandemActionButton(
                    "Save ${state.selectedMonth} plan",
                    TandemActionButtonTone.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Lucide.Check,
                    enabled =
                        budgetTemplateNameDraft.isNotBlank() &&
                            canSaveCurrentTemplate &&
                            !mobileSession.isPerformingBudgetAction,
                ) {
                    val saved =
                        decodeBudgetTemplates(state.budgetTemplatesJson)
                            .filterNot { it.budgetId == state.selectedBudgetId && it.name.equals(budgetTemplateNameDraft.trim(), true) }
                    state.budgetTemplatesJson =
                        encodeBudgetTemplates(
                            saved + BudgetTemplate(state.selectedBudgetId, budgetTemplateNameDraft.trim(), currentTemplateAssignments),
                        )
                    budgetTemplateNameDraft = ""
                }
                if (currentTemplateAssignments.size > 500) {
                    InlineMessage("This plan has more than 500 categories and cannot be saved as a template.", InlineMessageKind.INFO)
                }
                budgetActionFeedbackMessage(mobileSession)
                if (currentBudgetTemplates.isEmpty()) {
                    Text("Saved templates will appear here.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                } else {
                    currentBudgetTemplates.forEach { template ->
                        SurfaceCard {
                            Column(Modifier.fillMaxWidth().padding(TandemSpacing.small)) {
                                ListRow(
                                    template.name,
                                    subtitle = categoryCountLabel(template.assignments.size),
                                    leading = RowLeading.Icon(Lucide.CalendarDays),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                                    TandemActionButton(
                                        if (mobileSession.isPerformingBudgetAction) "Applying…" else "Apply",
                                        TandemActionButtonTone.PRIMARY,
                                        modifier = Modifier.weight(1f),
                                        icon = Lucide.Check,
                                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                                        loading = mobileSession.isPerformingBudgetAction,
                                    ) {
                                        scope.launch {
                                            val applied =
                                                mobileSession.budgetEdits.applyBudgetTemplate(
                                                    state.selectedBudgetId,
                                                    state.selectedMonthId,
                                                    template.assignments,
                                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                                )
                                            if (applied) showBudgetTemplatesDialog = false
                                        }
                                    }
                                    TandemIconButton(
                                        Lucide.Trash2,
                                        "Delete ${template.name}",
                                        sizeDp = 48,
                                        iconSizeDp = 20,
                                        tint = tandemPalette.negative,
                                        containerColor = tandemPalette.raised,
                                        enabled = !mobileSession.isPerformingBudgetAction,
                                    ) {
                                        state.budgetTemplatesJson =
                                            encodeBudgetTemplates(
                                                decodeBudgetTemplates(state.budgetTemplatesJson) - template,
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
    if (holdSheetMounted && isLiveBudget && !isTrackingBudget) {
        val parsedHoldAmount = nextMonthHoldAmountMinor
        val softwareKeyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(holdKeypadVisible) {
            if (holdKeypadVisible) {
                softwareKeyboard?.hide()
            }
        }

        fun setHoldAmount(value: Long) {
            nextMonthHoldAmountMinor =
                (if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value))
                    .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
        }

        fun enterHoldDigit(digit: Int) {
            val current = if (holdEnterNewOperand) 0L else nextMonthHoldAmountMinor
            val exceededLimit = amountDigitExceedsLimit(current, digit)
            setHoldAmount(appendAmountDigit(current, digit))
            holdAmountMessage =
                if (exceededLimit) {
                    "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
                } else {
                    null
                }
            holdEnterNewOperand = false
        }

        fun chooseHoldOperator(operator: String) {
            val accumulator = holdCalculatorAccumulator
            val pending = holdCalculatorOperator
            if (accumulator != null && pending != null && !holdEnterNewOperand) {
                val result =
                    calculateAmountMinorUnits(
                        accumulator,
                        nextMonthHoldAmountMinor,
                        pending,
                        state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                    )
                if (result == null) {
                    holdAmountMessage =
                        if (pending == "÷" && nextMonthHoldAmountMinor == 0L) "Can’t divide by zero." else "Amount is too large."
                    return
                }
                setHoldAmount(result)
                holdCalculatorAccumulator = kotlin.math.abs(result)
            } else if (accumulator == null) {
                holdCalculatorAccumulator = nextMonthHoldAmountMinor
            }
            holdAmountMessage = null
            holdCalculatorOperator = operator
            holdEnterNewOperand = true
        }

        fun calculateHoldAmount() {
            val accumulator = holdCalculatorAccumulator
            val operator = holdCalculatorOperator
            if (accumulator != null && operator != null) {
                val result =
                    if (holdEnterNewOperand) {
                        accumulator
                    } else {
                        calculateAmountMinorUnits(
                            accumulator,
                            nextMonthHoldAmountMinor,
                            operator,
                            state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                        )
                    }
                if (result == null) {
                    holdAmountMessage =
                        if (operator == "÷" && nextMonthHoldAmountMinor == 0L) "Can’t divide by zero." else "Amount is too large."
                    return
                }
                setHoldAmount(result)
            }
            holdAmountMessage = null
            holdCalculatorAccumulator = null
            holdCalculatorOperator = null
            holdEnterNewOperand = true
        }

        fun closeHoldDialog() {
            holdKeypadVisible = false
            softwareKeyboard?.hide()
            showNextMonthHoldDialog = false
        }
        TandemTaskSheet(
            visible = showNextMonthHoldDialog,
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) closeHoldDialog() },
            onDismissed = { holdSheetMounted = false },
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Hold for next month",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontFamily = tandemAlloFamily(),
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { closeHoldDialog() }
                    }
                    ListRow(
                        if (readyToAssignMinor < 0) "Overbudgeted" else assignmentPresentation.label,
                        trailing = state.formatCurrency(assignmentPresentation.amountMinor),
                        leading = RowLeading.Icon(Lucide.WalletCards),
                    )
                    ListRow(
                        "Held",
                        leading = RowLeading.Icon(Lucide.CalendarDays),
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                            ) {
                                if (liveBudgetMonth?.heldForNextMonthPending == true) PendingBudgetSyncIcon()
                                Text(
                                    state.formatCurrency(heldForNextMonthMinor),
                                    color = palette.muted,
                                    fontSize = TandemTypography.compactBody,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        },
                    )
                    if (readyToAssignMinor < 0) {
                        InfoBubble(
                            if (heldForNextMonthMinor > 0) {
                                "This month is overbudgeted by ${state.formatCurrency(-readyToAssignMinor)}. Lower the hold to cover it."
                            } else {
                                "This month is overbudgeted. Move money back before holding it for next month."
                            },
                        )
                    }
                    TransactionAmountField(
                        amount = state.formatAmount(nextMonthHoldAmountMinor),
                        amountMinorUnits = nextMonthHoldAmountMinor,
                        transactionType = "Neutral",
                        showCursor = holdKeypadVisible,
                        onShowKeypad = { holdKeypadVisible = true },
                        amountColorOverride = palette.accent,
                    )
                    AnimatedReveal(visible = holdAmountMessage != null) {
                        InlineMessage(holdAmountMessage.orEmpty(), InlineMessageKind.ERROR)
                    }
                    if (holdKeypadVisible) {
                        TransactionAmountKeypad(
                            onDigit = ::enterHoldDigit,
                            onDelete = {
                                val current = if (holdEnterNewOperand) 0L else nextMonthHoldAmountMinor
                                setHoldAmount(removeAmountDigit(current))
                                holdAmountMessage = null
                                holdEnterNewOperand = false
                            },
                            onOperator = ::chooseHoldOperator,
                            onEquals = ::calculateHoldAmount,
                            onDone = {
                                holdKeypadVisible = false
                                softwareKeyboard?.hide()
                            },
                        )
                    }
                    budgetActionFeedbackMessage(mobileSession)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            if (heldForNextMonthMinor > 0) "Release all" else "Close",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled =
                                !mobileSession.isPerformingBudgetAction &&
                                    (heldForNextMonthMinor == 0L || !state.readOnlyMode),
                            loading = mobileSession.isPerformingBudgetAction && heldForNextMonthMinor > 0,
                        ) {
                            if (heldForNextMonthMinor > 0) {
                                scope.launch {
                                    val reset =
                                        mobileSession.budgetEdits.resetBudgetHold(
                                            state.selectedBudgetId,
                                            state.selectedMonthId,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (reset) closeHoldDialog()
                                }
                            } else {
                                closeHoldDialog()
                            }
                        }
                        TandemActionButton(
                            when {
                                mobileSession.isPerformingBudgetAction -> "Holding…"
                                heldForNextMonthMinor > 0 -> "Update hold"
                                else -> "Hold amount"
                            },
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    holdAmountAllowed(parsedHoldAmount, readyToAssignMinor, heldForNextMonthMinor),
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val held =
                                    mobileSession.budgetEdits.holdBudgetForNextMonth(
                                        state.selectedBudgetId,
                                        state.selectedMonthId,
                                        parsedHoldAmount,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (held) closeHoldDialog()
                            }
                        }
                    }
                }
            }
        }
    }
    if (isLiveBudget) {
        CategoryGroupNameDialog(
            visible = showCreateCategoryGroupDialog,
            title = "New category group",
            name = categoryGroupNameDraft,
            onNameChange = { categoryGroupNameDraft = it },
            busy = isCreatingCategoryGroup || mobileSession.budgetQueue.isPersistingBudgetMutation,
            message = mobileSession.budgetActionMessage,
            messageKind = mobileSession.feedback.budgetActionMessageKind,
            messageActionLabel = mobileSession.feedback.budgetActionMessageActionLabel,
            onMessageAction =
                if (mobileSession.feedback.budgetActionMessageActionLabel ==
                    null
                ) {
                    null
                } else {
                    mobileSession.feedback::retryBudgetAction
                },
            placeholder = "Travel & Leisure",
            nameLabel = "Group name",
            description = "Keep related categories together under one heading.",
            leadingIcon = Lucide.Tags,
            isIncome = newCategoryGroupIsIncome,
            onIncomeChange = { newCategoryGroupIsIncome = it },
            saveLabel = "Create group",
            onDismiss = { showCreateCategoryGroupDialog = false },
            onSave = {
                if (!isCreatingCategoryGroup) {
                    isCreatingCategoryGroup = true
                    scope.launch {
                        try {
                            if (mobileSession.budgetQueue.enqueueBudgetCategoryGroupCreate(
                                    state.selectedBudgetId,
                                    categoryGroupNameDraft,
                                    newCategoryGroupIsIncome,
                                    displayGroups.map { it.id },
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                ) != null
                            ) {
                                showCreateCategoryGroupDialog = false
                            }
                        } finally {
                            isCreatingCategoryGroup = false
                        }
                    }
                }
            },
        )
    }
    MonthPickerDialog(
        visible = state.isBudgetMonthPickerOpen,
        state = state,
        onDismiss = { state.isBudgetMonthPickerOpen = false },
    )
}

@Composable
private fun MonthPickerDialog(
    visible: Boolean,
    state: AppState,
    onDismiss: () -> Unit,
) {
    var selectedYear by remember(state.selectedBudgetId, state.selectedMonthId) {
        mutableStateOf(state.selectedMonthId.substringBefore('-').toIntOrNull() ?: 2026)
    }
    val months = (1..12).map { month -> month to formatShortMonth(selectedYear, month) }
    TandemChoiceDialog(visible, onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                TandemModalHeader(
                    title = "Choose month",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismiss,
                )
                PeriodStepper(
                    title = selectedYear.toString(),
                    previousLabel = "Previous year",
                    nextLabel = "Next year",
                    onPrevious = { selectedYear -= 1 },
                    onNext = { selectedYear += 1 },
                )
                months.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        row.forEach { (monthNumber, monthLabel) ->
                            val full = formatMonth(selectedYear, monthNumber)
                            val chosen = state.selectedMonth == full
                            SelectableTile(
                                selected = chosen,
                                contentDescription = full,
                                onSelect = {
                                    state.selectedMonth = full
                                    state.selectedMonthId = "$selectedYear-${monthNumber.toString().padStart(2, '0')}"
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                selectedBackground = tandemPalette.accentSurface,
                                unselectedBackground = tandemPalette.surface,
                                selectedBorder = tandemPalette.accent,
                                unselectedBorder = tandemPalette.border,
                            ) {
                                Text(
                                    monthLabel,
                                    color = if (chosen) tandemPalette.accent else tandemPalette.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = TandemTypography.body,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCategoryCard(
    state: AppState,
    category: LiveCategorySummary,
    compact: Boolean,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDragStarted: (() -> Unit)? = null,
    onDragBy: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    if (!state.budgetShowProgressBars) {
        SurfaceCard(modifier = modifier, animateSizeChanges = false) {
            ListRow(
                category.name,
                leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                options = ListRowOptions(dense = compact),
                subtitleContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                    ) {
                        Text("Assigned", color = tandemPalette.muted, fontSize = TandemTypography.caption)
                        PrivacyAmountText(
                            state.formatCurrency(category.assignedMinor),
                            privacyModeEnabled = state.privacyModeEnabled,
                            fractionDigits = state.currencyFractionDigitsForDisplay,
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                        )
                        Text("· Spent", color = tandemPalette.muted, fontSize = TandemTypography.caption)
                        PrivacyAmountText(
                            state.formatCurrency(kotlin.math.abs(category.activityMinor)),
                            privacyModeEnabled = state.privacyModeEnabled,
                            fractionDigits = state.currencyFractionDigitsForDisplay,
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                        )
                    }
                },
                onClick = onClick,
                trailingContent = {
                    if (isReordering) {
                        BudgetItemReorderActions(
                            itemName = category.name,
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            enabled = !state.readOnlyMode,
                            onMoveUp = onMoveUp,
                            onMoveDown = onMoveDown,
                            onDragStarted = onDragStarted,
                            onDragBy = onDragBy,
                            onDragStopped = onDragStopped,
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                        ) {
                            if (category.pendingChanges) PendingBudgetSyncIcon()
                            AvailableAmountPill(
                                state.formatCurrency(category.balanceMinor),
                                isOverspent = category.balanceMinor < 0,
                                privacyModeEnabled = state.privacyModeEnabled,
                                fractionDigits = state.currencyFractionDigitsForDisplay,
                            )
                        }
                    }
                },
            )
        }
        return
    }

    val palette = tandemPalette
    val spentMinor = kotlin.math.abs(category.activityMinor)
    val progress =
        when {
            category.assignedMinor > 0L -> (spentMinor.toDouble() / category.assignedMinor.toDouble()).toFloat().coerceIn(0f, 1f)
            spentMinor > 0L -> 1f
            else -> 0f
        }
    val overspent = category.balanceMinor < 0L
    val statusColor = if (overspent) palette.negative else palette.positive
    SurfaceCard(
        modifier = modifier,
        onClick = onClick,
        animateSizeChanges = false,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TandemLayout.listRowHorizontalInset, vertical = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
        ) {
            Box(
                Modifier
                    .size(TandemLayout.listRowLeadingSize)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    state.categoryIconFor(category.id, category.name),
                    contentDescription = null,
                    modifier = Modifier.size(TandemLayout.listRowIconSize),
                    tint = palette.accent,
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category.name,
                        modifier = Modifier.weight(1f),
                        color = palette.text,
                        fontSize = TandemTypography.body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (category.pendingChanges) PendingBudgetSyncIcon()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                ) {
                    PrivacyAmountText(
                        state.formatCurrency(spentMinor),
                        privacyModeEnabled = state.privacyModeEnabled,
                        fractionDigits = state.currencyFractionDigitsForDisplay,
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                    )
                    Text("spent of", color = palette.muted, fontSize = TandemTypography.caption)
                    PrivacyAmountText(
                        state.formatCurrency(category.assignedMinor),
                        privacyModeEnabled = state.privacyModeEnabled,
                        fractionDigits = state.currencyFractionDigitsForDisplay,
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(palette.accent.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (overspent) palette.negative else palette.accent),
                    )
                }
            }
            if (isReordering) {
                BudgetItemReorderActions(
                    itemName = category.name,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    enabled = !state.readOnlyMode,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDragStarted = onDragStarted,
                    onDragBy = onDragBy,
                    onDragStopped = onDragStopped,
                )
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    PrivacyAmountText(
                        state.formatCurrency(category.balanceMinor),
                        privacyModeEnabled = state.privacyModeEnabled,
                        fractionDigits = state.currencyFractionDigitsForDisplay,
                        color = statusColor,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        if (overspent) "over" else "remaining",
                        color = statusColor,
                        fontSize = TandemTypography.micro,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingBudgetSyncIcon() {
    Icon(
        imageVector = Lucide.CloudSync,
        contentDescription = "Waiting to sync",
        modifier = Modifier.size(18.dp),
        tint = tandemPalette.accent,
    )
}

@Composable
private fun GettingStartedCard(onDismiss: () -> Unit) {
    val palette = tandemPalette
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Getting started",
                    Modifier.weight(1f),
                    color = palette.text,
                    fontSize = TandemTypography.action,
                    fontWeight = FontWeight.SemiBold,
                )
                TandemIconButton(Lucide.X, "Dismiss getting started", sizeDp = 36, iconSizeDp = 16, onClick = onDismiss)
            }
            GettingStartedTip(Lucide.Tags, "Tap a category", "See its activity and change what’s assigned.")
            GettingStartedTip(Lucide.Plus, "Add a transaction", "It’s saved on this device first, then synced.")
            GettingStartedTip(Lucide.RefreshCw, "Sync with Actual", "Pull down here or tap the cloud to sync now.")
            Text(
                "Got it",
                Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(TandemShapes.capsule))
                    .background(palette.accent.copy(alpha = 0.16f))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
                color = palette.accent,
                fontSize = TandemTypography.compactBody,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GettingStartedTip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    val palette = tandemPalette
    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(title, color = palette.text, fontSize = TandemTypography.compactBody, fontWeight = FontWeight.SemiBold)
            Text(detail, color = palette.muted, fontSize = TandemTypography.caption)
        }
    }
}
