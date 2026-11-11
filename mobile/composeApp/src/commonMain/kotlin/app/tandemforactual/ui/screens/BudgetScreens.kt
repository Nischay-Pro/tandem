@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.AvailableAmountPill
import app.tandemforactual.ui.components.CollapsibleSectionCard
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.PeriodStepper
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemChoiceDialog
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemModalHeader
import app.tandemforactual.ui.components.TandemTaskSheet
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

internal fun LazyListScope.budgetPendingChangeBannerItems(
    state: AppState,
    navigation: AppNavigation,
    pendingWrites: List<OutboxOperationEntity>,
) {
    val reviewableWrites = pendingWrites.filter { it.recordType in setOf("transaction", "budget-mutation") }
    val conflictCount = reviewableWrites.count { it.status == "conflict" }
    val rejectedWrites = reviewableWrites.filter { it.status == "rejected" }
    val uncertainWrites = reviewableWrites.filter { it.status == "uncertain" }

    item(key = "budget-conflict-alert") {
        AnimatedReveal(visible = conflictCount > 0) {
            AttentionBanner(
                "Needs review",
                if (conflictCount == 1) "Choose which version to keep." else "Review $conflictCount changes.",
                "Review",
            ) {
                state.selectedConflictOperationId = ""
                navigation.open(AppRoute.CONFLICT_INBOX)
            }
        }
    }
    item(key = "budget-rejected-alert") {
        AnimatedReveal(visible = rejectedWrites.isNotEmpty()) {
            AttentionBanner(
                "Rejected",
                if (rejectedWrites.size == 1) {
                    "Actual kept the previous value. Review it."
                } else {
                    "Actual kept the previous values. Review them."
                },
                "Review",
            ) {
                state.selectedConflictOperationId = rejectedWrites.firstOrNull()?.operationId.orEmpty()
                navigation.open(AppRoute.UNCERTAIN_WRITE)
            }
        }
    }
    item(key = "budget-status-unknown-alert") {
        AnimatedReveal(visible = uncertainWrites.isNotEmpty()) {
            AttentionBanner(
                "Status unknown",
                "Tandem hasn’t confirmed these edits. Check their status.",
                "Check status",
            ) {
                state.selectedConflictOperationId = uncertainWrites.firstOrNull()?.operationId.orEmpty()
                navigation.open(AppRoute.UNCERTAIN_WRITE)
            }
        }
    }
}

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
    var showMonthPicker by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
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
                onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
            )
        }
        return
    }
    val routeScrollState = LocalRouteScrollState.current
    val routeListState = LocalRouteLazyListState.current
    val transactionActionCompact by remember(routeScrollState, routeListState) {
        derivedStateOf {
            (routeScrollState?.value ?: 0) > 72 ||
                (routeListState?.firstVisibleItemIndex ?: 0) > 0 ||
                (routeListState?.firstVisibleItemScrollOffset ?: 0) > 72
        }
    }
    val categoryGroupActionEndPadding = if (transactionActionCompact) 92.dp else 216.dp
    var selectedGroupOrderId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedGroupOrderId) {
        onGroupReorderModeChange(selectedGroupOrderId != null)
    }
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val compact = state.homeLayout == "Compact"
    val sectionSpacing = if (compact) 10.dp else 16.dp
    val groups = liveBudgetMonth?.groups.orEmpty()
    val queuedGroupMoves = mobileSession.budgetReorderMovesFor(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
    val groupReorderStatus = mobileSession.budgetReorderStatus(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
    val orderedGroupIds =
        budgetOrderForScreen(
            groups.map { it.id },
            groupReorderStatus,
            queuedGroupMoves,
        )
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

    fun enqueueGroupMove(
        groupId: String,
        nextIndex: Int,
    ) {
        val oldOrder = displayGroups.map { it.id }
        val nextOrder = reorderItemToIndex(oldOrder, groupId, nextIndex)
        if (oldOrder == nextOrder) return
        scope.launch {
            mobileSession.clearBudgetReorderMessage(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
            mobileSession.clearBudgetActionMessage()
            mobileSession.enqueueBudgetReorder(
                state.selectedBudgetId,
                BUDGET_GROUP_ORDER_SCOPE,
                BudgetReorderKind.CATEGORY_GROUP,
                groupId,
                itemTargetIdAfterMove(nextOrder, groupId),
                nextOrder,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        }
    }
    val pinnedCategories =
        remember(groups, state.pinnedCategoryIds) {
            groups.flatMap { group ->
                group.categories.filter { state.isCategoryPinned(it.id) }.map { group to it }
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
                budgetPendingChangeBannerItems(state, navigation, mobileSession.pendingWrites)
            }
            item(key = "budget-overview-title") {
                Heading("Budget overview")
            }
            if (mobileSession.syncComplete && !state.hasDismissedBudgetHint) {
                item(key = "budget-getting-started-hint") {
                    SurfaceCard {
                        Row(
                            Modifier.fillMaxWidth().padding(TandemSpacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Lucide.Info,
                                contentDescription = null,
                                modifier = Modifier.padding(top = 2.dp).size(20.dp),
                                tint = palette.accent,
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                            ) {
                                Text(
                                    "Getting started",
                                    color = palette.text,
                                    fontSize = TandemTypography.compactBody,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Choose a category to review activity. Changes save here and sync when connected.",
                                    color = palette.muted,
                                    fontSize = TandemTypography.label,
                                    lineHeight = TandemTypography.compactLineHeight,
                                )
                            }
                            TandemIconButton(Lucide.X, "Dismiss getting-started hint", sizeDp = 36, iconSizeDp = 16) {
                                state.hasDismissedBudgetHint = true
                            }
                        }
                    }
                }
            }
            item(key = "budget-month-picker") {
                MonthSelectorCard(state.selectedMonth) { showMonthPicker = true }
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
                                    mobileSession.retryQueuedBudgetReorders(
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
                                    mobileSession.clearBudgetActionMessage()
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
                                    mobileSession.clearBudgetActionMessage()
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
            if (isLiveBudget && liveBudgetMonth == null && !isTrackingBudget) {
                item(key = "budget-month-empty") {
                    SurfaceCard {
                        EmptyState(
                            "No plan for this month",
                            "Sync a month available in Actual, or choose a different planning period.",
                            modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
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
                                state.formatCurrency(displayedAssignmentMinor),
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
                                            state.formatCurrency(displayedAssignmentMinor),
                                            leading = RowLeading.Icon(Lucide.WalletCards),
                                            options = ListRowOptions(trailingColor = readyToAssignColor),
                                        )
                                        if (isLiveBudget) {
                                            DividerLine()
                                            ListRow(
                                                "Hold for next month",
                                                trailing = state.formatCurrency(heldForNextMonthMinor),
                                                leading = RowLeading.Icon(Lucide.CalendarDays),
                                                onClick = {
                                                    nextMonthHoldAmountMinor = readyToAssignMinor.coerceAtLeast(0)
                                                    holdCalculatorAccumulator = null
                                                    holdCalculatorOperator = null
                                                    holdEnterNewOperand = false
                                                    holdAmountMessage = null
                                                    holdKeypadVisible = true
                                                    mobileSession.clearBudgetActionMessage()
                                                    showNextMonthHoldDialog = true
                                                    holdSheetMounted = true
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
                                                state.formatCurrency(category.balanceMinor),
                                                leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                                            ) {
                                                state.selectedCategoryGroupId = group.id
                                                state.selectedCategoryId = category.id
                                                navigation.open(AppRoute.CATEGORY_DETAIL)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                displayGroups.forEach { group ->
                    val groupSectionKey = "live:${state.selectedBudgetId}:group:${group.id}"
                    val groupExpanded = !state.isBudgetSectionCollapsed(groupSectionKey)
                    val isReordering = selectedGroupOrderId == group.id
                    val groupIndex = displayGroups.indexOfFirst { it.id == group.id }
                    val displayCategories = group.categories
                    item(key = "$groupSectionKey:section") {
                        SurfaceCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .combinedClickable(
                                                role = Role.Button,
                                                onClickLabel = "Open ${group.name}",
                                                onLongClickLabel = "Reorder ${group.name}",
                                                onClick = {
                                                    if (selectedGroupOrderId == group.id) {
                                                        selectedGroupOrderId = null
                                                    } else if (selectedGroupOrderId != null) {
                                                        selectedGroupOrderId = null
                                                    } else {
                                                        state.selectedCategoryGroupId = group.id
                                                        navigation.open(AppRoute.CATEGORY_GROUP)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (isLiveBudget &&
                                                        !state.readOnlyMode
                                                    ) {
                                                        selectedGroupOrderId = if (isReordering) null else group.id
                                                    }
                                                },
                                            ).padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                                    ) {
                                        Text(
                                            group.name,
                                            color = palette.text,
                                            fontSize = TandemTypography.body,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                        Text(
                                            when {
                                                isReordering -> categoryCountLabel(group.categories.size)
                                                isTrackingBudget -> categoryCountLabel(group.categories.size)
                                                else -> "${state.formatCurrency(
                                                    group.assignedMinor,
                                                )} assigned · ${categoryCountLabel(group.categories.size)}"
                                            },
                                            color = palette.muted,
                                            fontSize = TandemTypography.caption,
                                            maxLines = 1,
                                        )
                                    }
                                    if (isReordering && isLiveBudget) {
                                        BudgetItemReorderActions(
                                            canMoveUp = groupIndex > 0,
                                            canMoveDown = groupIndex >= 0 && groupIndex < displayGroups.lastIndex,
                                            enabled = !state.readOnlyMode,
                                            itemName = group.name,
                                            onMoveUp = {
                                                enqueueGroupMove(group.id, groupIndex - 1)
                                            },
                                            onMoveDown = {
                                                enqueueGroupMove(group.id, groupIndex + 1)
                                            },
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                                        ) {
                                            Text(
                                                state.formatCurrency(group.balanceMinor),
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
                        items(displayCategories, key = { "budget-category:${group.id}:${it.id}" }) { category ->
                            SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                                ListRow(
                                    category.name,
                                    "Assigned ${state.formatCurrency(category.assignedMinor)} · spent ${state.formatCurrency(
                                        kotlin.math.abs(category.activityMinor),
                                    )}",
                                    leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                                    options =
                                        ListRowOptions(
                                            dense = compact,
                                        ),
                                    onClick = {
                                        state.selectedCategoryGroupId = group.id
                                        state.selectedCategoryId = category.id
                                        navigation.open(AppRoute.CATEGORY_DETAIL)
                                    },
                                    trailingContent = {
                                        AvailableAmountPill(
                                            state.formatCurrency(category.balanceMinor),
                                            isOverdrawn = category.balanceMinor < 0,
                                        )
                                    },
                                )
                            }
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
                            onClick = { navigation.selectTab(AppRoute.ACTIVITY) },
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
            item(key = "budget-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
        }
        if (shouldShowBudgetCreateAction(isLiveBudget, selectedGroupOrderId != null)) {
            FloatingAction(
                compact = true,
                enabled = !state.readOnlyMode,
                label = "Add category group",
                contentDescription = "Add category group",
                icon = Lucide.Plus,
                outlined = true,
                onClick = {
                    categoryGroupNameDraft = ""
                    newCategoryGroupIsIncome = false
                    mobileSession.clearBudgetActionMessage()
                    showCreateCategoryGroupDialog = true
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = categoryGroupActionEndPadding, bottom = TandemSpacing.large),
            )
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
                                mobileSession.copyBudgetMonth(
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
                                                mobileSession.applyBudgetTemplate(
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
                        trailing = state.formatCurrency(heldForNextMonthMinor),
                        leading = RowLeading.Icon(Lucide.CalendarDays),
                    )
                    if (readyToAssignMinor < 0) {
                        InfoBubble("This month is overbudgeted. Move money back before holding it for next month.")
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
                            if (heldForNextMonthMinor > 0) "Reset hold" else "Close",
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
                                        mobileSession.resetBudgetHold(
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
                            if (mobileSession.isPerformingBudgetAction) "Holding…" else "Hold amount",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    parsedHoldAmount > 0 &&
                                    parsedHoldAmount <= readyToAssignMinor,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val held =
                                    mobileSession.holdBudgetForNextMonth(
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
            title = "Add category group",
            name = categoryGroupNameDraft,
            onNameChange = { categoryGroupNameDraft = it },
            busy = isCreatingCategoryGroup || mobileSession.isPersistingBudgetMutation,
            message = mobileSession.budgetActionMessage,
            messageKind = mobileSession.budgetActionMessageKind,
            messageActionLabel = mobileSession.budgetActionMessageActionLabel,
            onMessageAction = if (mobileSession.budgetActionMessageActionLabel == null) null else mobileSession::retryBudgetAction,
            placeholder = "Travel & Leisure",
            isIncome = newCategoryGroupIsIncome,
            onIncomeChange = { newCategoryGroupIsIncome = it },
            saveLabel = "Add group",
            onDismiss = { showCreateCategoryGroupDialog = false },
            onSave = {
                if (!isCreatingCategoryGroup) {
                    isCreatingCategoryGroup = true
                    scope.launch {
                        try {
                            if (mobileSession.enqueueBudgetCategoryGroupCreate(
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
        visible = showMonthPicker,
        state = state,
        onDismiss = { showMonthPicker = false },
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
