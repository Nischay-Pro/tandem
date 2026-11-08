@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.Badge
import app.tandemforactual.ui.components.CollapsibleSectionCard
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveCategoryGroupSummary
import app.tandemforactual.ui.model.LiveCategorySummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
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
) {
    val palette = tandemPalette
    val scope = rememberCoroutineScope()
    var showCopyLastMonthDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var showBudgetTemplatesDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var showNextMonthHoldDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var budgetTemplateNameDraft by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val compact = state.homeLayout == "Compact"
    val essentialsFirst = state.homeLayout == "Essentials"
    val sectionSpacing = if (compact) 10.dp else 16.dp
    val groups = if (isLiveBudget) liveBudgetMonth?.groups.orEmpty() else previewCategoryGroups
    val currentBudgetTemplates =
        remember(state.budgetTemplatesJson, state.selectedBudgetId) {
            decodeBudgetTemplates(state.budgetTemplatesJson).filter { it.budgetId == state.selectedBudgetId }
        }
    val currentTemplateAssignments =
        remember(groups) {
            groups.flatMap { group -> group.categories }.map { it.id to it.assignedMinor }
        }
    val recentTransactions =
        remember(isLiveBudget, liveTransactions) {
            if (isLiveBudget) {
                liveTransactions.filterNot(TransactionItem::isSplitParent)
            } else {
                liveTransactions.takeIf { it.isNotEmpty() } ?: previewTransactions
            }
        }
    val conflictCount = if (isLiveBudget) mobileSession.pendingWrites.count { it.status == "conflict" } else 0
    val rejectedCount = if (isLiveBudget) mobileSession.pendingWrites.count { it.status == "rejected" } else 0
    val readyToAssignMinor = if (isLiveBudget) liveBudgetMonth?.toBudgetMinor ?: 0 else 124_000L
    val heldForNextMonthMinor = liveBudgetMonth?.heldForNextMonthMinor?.coerceAtLeast(0) ?: 0L
    var nextMonthHoldAmountDraft by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) {
        mutableStateOf(state.formatAmount(readyToAssignMinor.coerceAtLeast(0)))
    }
    val readyToAssignColor =
        when {
            readyToAssignMinor < 0 -> palette.negative
            readyToAssignMinor > 0 -> palette.positive
            else -> palette.muted
        }
    val summaryKey =
        if (isLiveBudget) {
            "live:${state.selectedBudgetId}:summary:${state.selectedMonthId}"
        } else {
            "summary:${state.selectedMonthId}"
        }
    val displayGroups =
        remember(essentialsFirst, groups) {
            if (essentialsFirst) {
                groups
                    .withIndex()
                    .sortedWith(
                        compareBy<IndexedValue<LiveCategoryGroupSummary>>(
                            { essentialGroupOrder(it.value.name) },
                            { it.index },
                        ),
                    ).map { it.value }
            } else {
                groups
            }
        }
    val pinnedCategories =
        remember(groups, state.pinnedCategoryIds) {
            groups.flatMap { group ->
                group.categories.filter { state.isCategoryPinned(it.id) }.map { group to it }
            }
        }

    PageLazyColumn(
        verticalSpacing = sectionSpacing,
        horizontalPadding = if (compact) 16.dp else 20.dp,
        isRefreshing = isLiveBudget && mobileSession.isSyncing,
        onRefresh = onSyncNow.takeIf { isLiveBudget },
    ) {
        if (!isLiveBudget) {
            item(key = "budget-sample-banner") {
                SampleBanner(
                    if (liveTransactions.isNotEmpty()) {
                        "Categories use sample data; activity is synced"
                    } else {
                        "Categories use sample data"
                    },
                )
            }
        }
        if (isLiveBudget) {
            item(key = "budget-conflict-alert") {
                AnimatedReveal(visible = state.conflictReviewAlerts && conflictCount > 0) {
                    AttentionBanner(
                        "$conflictCount change${if (conflictCount == 1) "" else "s"} need${if (conflictCount == 1) "s" else ""} review",
                        "Choose which version of the edit to keep.",
                        "Review changes",
                    ) { navigation.open(AppRoute.CONFLICT_INBOX) }
                }
            }
            item(key = "budget-rejected-alert") {
                AnimatedReveal(visible = state.rejectedEditAlerts && rejectedCount > 0) {
                    AttentionBanner(
                        "$rejectedCount edit${if (rejectedCount == 1) "" else "s"} not applied",
                        "Review the edit.",
                        "Review edits",
                    ) { navigation.open(AppRoute.REJECTED_WRITE) }
                }
            }
        }
        item(key = "budget-overview-title") {
            Heading(
                "Budget overview",
                if (isTrackingBudget) {
                    "Review activity for the selected month."
                } else if (isLiveBudget) {
                    "Review the synced plan and recent activity for the selected month."
                } else {
                    "Review category balances and spending for the selected month."
                },
                compactSubtitle = true,
            )
        }
        item(key = "budget-month-picker") {
            MonthSelectorCard(state.selectedMonth) { navigation.open(AppRoute.MONTH_PICKER) }
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
                            if (isLiveBudget) "${groups.sumOf { it.categories.size }} categories" else "Ready to assign",
                            state.formatCurrency(readyToAssignMinor),
                            actionColor = readyToAssignColor,
                            expanded = summaryExpanded,
                            onToggleExpanded = { state.toggleBudgetSection(summaryKey) },
                        ) {}
                        AnimatedVisibility(
                            visible = summaryExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SurfaceCard(Modifier.padding(top = sectionSpacing)) {
                                Column {
                                    ListRow(
                                        if (isLiveBudget) "Ready to assign" else "Available this month",
                                        if (isLiveBudget) state.selectedMonth else "On track",
                                        state.formatCurrency(readyToAssignMinor),
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
                                                nextMonthHoldAmountDraft = state.formatAmount(readyToAssignMinor.coerceAtLeast(0))
                                                mobileSession.clearBudgetActionMessage()
                                                showNextMonthHoldDialog = true
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isLiveBudget && essentialsFirst) {
                item(key = "sample-upcoming-before-groups") { UpcomingBillsCard(state, navigation) }
            }
            if (pinnedCategories.isNotEmpty()) {
                val pinnedSectionKey = if (isLiveBudget) "live:${state.selectedBudgetId}:pinned" else "sample:pinned"
                val pinnedExpanded = !state.isBudgetSectionCollapsed(pinnedSectionKey)
                item(key = "$pinnedSectionKey:section") {
                    Column {
                        CollapsibleSectionCard(
                            "Pinned",
                            "${pinnedCategories.size} categories",
                            expanded = pinnedExpanded,
                            onToggleExpanded = { state.toggleBudgetSection(pinnedSectionKey) },
                        ) {}
                        AnimatedVisibility(
                            visible = pinnedExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
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
                val groupSectionKey =
                    if (isLiveBudget) {
                        "live:${state.selectedBudgetId}:group:${group.id}"
                    } else {
                        "sample:${group.id.removePrefix("sample-")}"
                    }
                val groupExpanded = !state.isBudgetSectionCollapsed(groupSectionKey)
                val displayCategories =
                    if (essentialsFirst) {
                        group.categories
                            .withIndex()
                            .sortedWith(
                                compareBy<IndexedValue<LiveCategorySummary>>(
                                    { essentialCategoryOrder(it.value.name) },
                                    { it.index },
                                ),
                            ).map { it.value }
                    } else {
                        group.categories
                    }
                item(key = "$groupSectionKey:section") {
                    Column {
                        CollapsibleSectionCard(
                            group.name,
                            if (isTrackingBudget) {
                                "${group.categories.size} categories"
                            } else {
                                "${state.formatCurrency(group.assignedMinor)} assigned · ${group.categories.size} categories"
                            },
                            state.formatCurrency(group.balanceMinor),
                            expanded = groupExpanded,
                            onToggleExpanded = { state.toggleBudgetSection(groupSectionKey) },
                            onClick = {
                                if (isLiveBudget) {
                                    state.selectedCategoryGroupId = group.id
                                    navigation.open(AppRoute.CATEGORY_GROUP)
                                } else {
                                    openPreviewCategoryGroup(state, navigation, group.id)
                                }
                            },
                        ) {}
                        AnimatedVisibility(
                            visible = groupExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(
                                Modifier.padding(top = sectionSpacing),
                                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                            ) {
                                displayCategories.forEach { category ->
                                    SurfaceCard {
                                        ListRow(
                                            category.name,
                                            "Spent ${state.formatCurrency(
                                                kotlin.math.abs(category.activityMinor),
                                            )} · ${state.formatCurrency(category.balanceMinor)} available",
                                            state.formatCurrency(category.assignedMinor),
                                            leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                                            options =
                                                ListRowOptions(
                                                    trailingColor = if (category.balanceMinor >= 0) palette.positive else palette.warning,
                                                    dense = compact,
                                                ),
                                        ) {
                                            if (isLiveBudget) {
                                                state.selectedCategoryGroupId = group.id
                                                state.selectedCategoryId = category.id
                                                navigation.open(AppRoute.CATEGORY_DETAIL)
                                            } else {
                                                openPreviewCategory(state, navigation, group.id, category.id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isLiveBudget && !essentialsFirst) {
                item(key = "sample-upcoming-after-groups") { UpcomingBillsCard(state, navigation) }
            }
            if (isLiveBudget && liveSchedules.isNotEmpty()) {
                item(key = "actual-upcoming-schedules") {
                    LiveUpcomingBillsCard(state, navigation, liveSchedules)
                }
            }
        }
        if (recentTransactions.isNotEmpty()) {
            val recentSectionKey =
                if (isLiveBudget) "live:${state.selectedBudgetId}:recent-activity" else "sample:recent-activity"
            val recentExpanded = !state.isBudgetSectionCollapsed(recentSectionKey)
            item(key = "$recentSectionKey:section") {
                Column {
                    CollapsibleSectionCard(
                        "Recent activity",
                        if (isLiveBudget) "${liveTransactions.size} synced transactions" else "Latest budget changes",
                        "See all",
                        onClick = { navigation.selectTab(AppRoute.ACTIVITY) },
                        expanded = recentExpanded,
                        onToggleExpanded = { state.toggleBudgetSection(recentSectionKey) },
                    ) {}
                    AnimatedVisibility(
                        visible = recentExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            Modifier.padding(top = sectionSpacing),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                        ) {
                            recentTransactions.take(if (isLiveBudget) 3 else 2).forEach { transaction ->
                                SurfaceCard {
                                    TransactionRow(transaction) {
                                        openTransaction(
                                            state,
                                            navigation,
                                            transaction,
                                            isLive = isLiveBudget || liveTransactions.isNotEmpty(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isLiveBudget) {
            item(key = "sample-sync-summary") {
                SurfaceCard(onClick = { navigation.open(AppRoute.SYNC_CENTER) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(TandemSpacing.large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                    ) {
                        Badge("✓", palette.positive)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "All changes accounted for",
                                color = palette.text,
                                fontSize = TandemTypography.compactBody,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (liveTransactions.isNotEmpty()) {
                                    "${liveTransactions.size} recent Actual transactions cached"
                                } else {
                                    "Sample data · nothing has synced"
                                },
                                color = palette.muted,
                                fontSize = TandemTypography.caption,
                            )
                        }
                        Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
                    }
                }
            }
        }
        item(key = "budget-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
    }
    if (showCopyLastMonthDialog) {
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showCopyLastMonthDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Copy last month?",
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
                        ) { showCopyLastMonthDialog = false }
                    }
                    Text(
                        "Replace ${state.selectedMonth} category assignments with the previous month’s amounts.",
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.body,
                    )
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    TandemActionButton(
                        if (mobileSession.isPerformingBudgetAction) "Copying…" else "Copy assignments",
                        TandemActionButtonTone.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Lucide.Check,
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
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { showCopyLastMonthDialog = false }
                }
            }
        }
    }
    if (showBudgetTemplatesDialog && isLiveBudget && !isTrackingBudget) {
        val canSaveCurrentTemplate = currentTemplateAssignments.isNotEmpty() && currentTemplateAssignments.size <= 500
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showBudgetTemplatesDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
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
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    if (currentBudgetTemplates.isEmpty()) {
                        Text("Saved templates will appear here.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                    } else {
                        currentBudgetTemplates.forEach { template ->
                            SurfaceCard {
                                Column(Modifier.fillMaxWidth().padding(TandemSpacing.small)) {
                                    ListRow(
                                        template.name,
                                        subtitle = "${template.assignments.size} categories",
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
    }
    if (showNextMonthHoldDialog && isLiveBudget && !isTrackingBudget) {
        val parsedHoldAmount = state.parseAmountMinorUnits(nextMonthHoldAmountDraft)
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showNextMonthHoldDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
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
                        ) { showNextMonthHoldDialog = false }
                    }
                    ListRow("Ready to assign", trailing = state.formatCurrency(readyToAssignMinor))
                    ListRow("Held", trailing = state.formatCurrency(heldForNextMonthMinor))
                    TextFieldCard(
                        "Amount to hold",
                        nextMonthHoldAmountDraft,
                        { nextMonthHoldAmountDraft = it },
                        TextFieldOptions(
                            placeholder = state.formatAmount(0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textAlign = TextAlign.Center,
                            large = true,
                        ),
                    )
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
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
                                    if (reset) showNextMonthHoldDialog = false
                                }
                            } else {
                                showNextMonthHoldDialog = false
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
                                    parsedHoldAmount != null &&
                                    parsedHoldAmount > 0 &&
                                    parsedHoldAmount <= readyToAssignMinor,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val held =
                                    mobileSession.holdBudgetForNextMonth(
                                        state.selectedBudgetId,
                                        state.selectedMonthId,
                                        parsedHoldAmount ?: 0L,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (held) showNextMonthHoldDialog = false
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun openPreviewCategoryGroup(
    state: AppState,
    navigation: AppNavigation,
    groupId: String,
) {
    state.selectedCategoryGroupId = groupId
    state.selectedCategoryId = ""
    navigation.open(AppRoute.CATEGORY_GROUP)
}

internal fun openPreviewCategory(
    state: AppState,
    navigation: AppNavigation,
    groupId: String,
    categoryId: String,
) {
    state.selectedCategoryGroupId = groupId
    state.selectedCategoryId = categoryId
    navigation.open(AppRoute.CATEGORY_DETAIL)
}

internal fun essentialGroupOrder(name: String): Int =
    if (listOf("essential", "housing", "fixed bill", "bill").any { it in name.lowercase() }) 0 else 1

internal fun essentialCategoryOrder(name: String): Int =
    when {
        listOf("rent", "mortgage", "housing").any { it in name.lowercase() } -> 0
        listOf("utility", "electric", "water", "gas").any { it in name.lowercase() } -> 1
        listOf("insurance", "groceries", "food").any { it in name.lowercase() } -> 2
        else -> 3
    }
