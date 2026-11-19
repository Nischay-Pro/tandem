@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
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
import app.tandemforactual.ui.components.PrivacyAmountText
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
import app.tandemforactual.ui.model.LiveCategoryGroupSummary
import app.tandemforactual.ui.model.LiveCategorySummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tags
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

    if (conflictCount > 0) {
        item(key = "budget-conflict-alert") {
            AnimatedReveal(visible = true) {
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
    }
    if (rejectedWrites.isNotEmpty()) {
        item(key = "budget-rejected-alert") {
            AnimatedReveal(visible = true) {
                AttentionBanner(
                    "Rejected",
                    "${rejectedWrites.size} ${if (rejectedWrites.size == 1) "change" else "changes"} were rejected.",
                    "Review",
                ) {
                    state.selectedConflictOperationId = rejectedWrites.firstOrNull()?.operationId.orEmpty()
                    navigation.open(AppRoute.UNCERTAIN_WRITE)
                }
            }
        }
    }
    if (uncertainWrites.isNotEmpty()) {
        item(key = "budget-status-unknown-alert") {
            AnimatedReveal(visible = true) {
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
            .pendingBudgetMutationsFor(state.selectedBudgetId)
            .filter { it.kind == BudgetReorderKind.DELETE_CATEGORY_GROUP }
            .mapTo(mutableSetOf()) { it.itemId }
    val groups = liveBudgetMonth?.groups.orEmpty().filterNot { it.id in pendingDeletedGroupIds }
    val queuedGroupMoves = mobileSession.budgetReorderMovesFor(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
    val groupReorderStatus = mobileSession.budgetReorderStatus(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
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

    // Drag callbacks can outlive the composition that created them, so read the
    // queue's latest state when they run instead of a captured order.
    fun latestQueuedGroupOrder(): List<String> =
        budgetOrderForScreen(
            groups.map { it.id },
            mobileSession.budgetReorderStatus(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE),
            mobileSession.budgetReorderMovesFor(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE),
        )

    fun latestQueuedCategoryOrder(group: LiveCategoryGroupSummary): List<String> =
        budgetOrderForScreen(
            group.categories.map { it.id },
            mobileSession.budgetReorderStatus(state.selectedBudgetId, group.id),
            mobileSession.budgetReorderMovesFor(state.selectedBudgetId, group.id),
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
            mobileSession.clearBudgetReorderMessage(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
            mobileSession.clearBudgetActionMessage()
            val saved =
                mobileSession.enqueueBudgetReorder(
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
                    val categoryOrderStatus = mobileSession.budgetReorderStatus(state.selectedBudgetId, group.id)
                    val queuedCategoryMoves = mobileSession.budgetReorderMovesFor(state.selectedBudgetId, group.id)
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
                                mobileSession.clearBudgetReorderMessage(state.selectedBudgetId, group.id)
                                mobileSession.clearBudgetActionMessage()
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
                                                mobileSession.enqueueBudgetReorder(
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
                                    mobileSession.enqueueBudgetReorder(
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
        if (actionDockVisible) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
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
                        mobileSession.clearBudgetActionMessage()
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
            title = "New category group",
            name = categoryGroupNameDraft,
            onNameChange = { categoryGroupNameDraft = it },
            busy = isCreatingCategoryGroup || mobileSession.isPersistingBudgetMutation,
            message = mobileSession.budgetActionMessage,
            messageKind = mobileSession.budgetActionMessageKind,
            messageActionLabel = mobileSession.budgetActionMessageActionLabel,
            onMessageAction = if (mobileSession.budgetActionMessageActionLabel == null) null else mobileSession::retryBudgetAction,
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
