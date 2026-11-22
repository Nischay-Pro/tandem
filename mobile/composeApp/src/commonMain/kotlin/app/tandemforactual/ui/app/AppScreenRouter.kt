@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.MerchantIconCatalog
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.SelectorGroup
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.about.AboutScreen
import app.tandemforactual.ui.feature.about.LocalDataScreen
import app.tandemforactual.ui.feature.about.MerchantIconLicensesScreen
import app.tandemforactual.ui.feature.accounts.AccountDetailScreen
import app.tandemforactual.ui.feature.accounts.AccountOptionsScreen
import app.tandemforactual.ui.feature.accounts.AccountsScreen
import app.tandemforactual.ui.feature.activity.ActivityScreen
import app.tandemforactual.ui.feature.activity.ActivitySearchScreen
import app.tandemforactual.ui.feature.activity.ActivityTransactionSortCache
import app.tandemforactual.ui.feature.banking.BankConnectionsScreen
import app.tandemforactual.ui.feature.budget.BudgetPreferencesScreen
import app.tandemforactual.ui.feature.budget.BudgetScreen
import app.tandemforactual.ui.feature.budget.CategoryDetailScreen
import app.tandemforactual.ui.feature.budget.CategoryGroupScreen
import app.tandemforactual.ui.feature.budget.SyncPreferencesScreen
import app.tandemforactual.ui.feature.budget.openReportCategoryActivity
import app.tandemforactual.ui.feature.checkup.DataHealthScreen
import app.tandemforactual.ui.feature.currency.ExchangeRatesScreen
import app.tandemforactual.ui.feature.export.ExportScreen
import app.tandemforactual.ui.feature.home.HomeLayoutScreen
import app.tandemforactual.ui.feature.home.HomeScreen
import app.tandemforactual.ui.feature.home.PersonalizeScreen
import app.tandemforactual.ui.feature.household.HouseholdScreen
import app.tandemforactual.ui.feature.insights.CashFlowScreen
import app.tandemforactual.ui.feature.insights.MonthlyReviewScreen
import app.tandemforactual.ui.feature.insights.SpendingForecastScreen
import app.tandemforactual.ui.feature.insights.SpendingTrendsScreen
import app.tandemforactual.ui.feature.onboarding.BudgetChooserScreen
import app.tandemforactual.ui.feature.onboarding.BudgetUnlockScreen
import app.tandemforactual.ui.feature.onboarding.FirstSyncScreen
import app.tandemforactual.ui.feature.onboarding.ServerAddressScreen
import app.tandemforactual.ui.feature.onboarding.SetupCompleteScreen
import app.tandemforactual.ui.feature.onboarding.SignInScreen
import app.tandemforactual.ui.feature.onboarding.StartupScreen
import app.tandemforactual.ui.feature.onboarding.SwitchBudgetScreen
import app.tandemforactual.ui.feature.onboarding.WelcomeScreen
import app.tandemforactual.ui.feature.planning.DebtPayoffScreen
import app.tandemforactual.ui.feature.planning.GoalTimelineScreen
import app.tandemforactual.ui.feature.planning.WhatIfScreen
import app.tandemforactual.ui.feature.reimbursements.ReimbursementsScreen
import app.tandemforactual.ui.feature.reports.CustomReportScreen
import app.tandemforactual.ui.feature.reports.CustomReportsScreen
import app.tandemforactual.ui.feature.reports.ReportDetailScreen
import app.tandemforactual.ui.feature.reports.ReportKind
import app.tandemforactual.ui.feature.reports.ReportsDataCache
import app.tandemforactual.ui.feature.reports.ReportsScreen
import app.tandemforactual.ui.feature.reports.label
import app.tandemforactual.ui.feature.retirement.MatchEditorScreen
import app.tandemforactual.ui.feature.retirement.RetirementScreen
import app.tandemforactual.ui.feature.retirement.RetirementSetupScreen
import app.tandemforactual.ui.feature.rules.BudgetRulesScreen
import app.tandemforactual.ui.feature.rules.ManagePayeesScreen
import app.tandemforactual.ui.feature.rules.RuleEditorSheet
import app.tandemforactual.ui.feature.schedules.UpcomingSchedulesScreen
import app.tandemforactual.ui.feature.settings.AppearanceScreen
import app.tandemforactual.ui.feature.settings.LocalServerAddressScreen
import app.tandemforactual.ui.feature.settings.NotificationsScreen
import app.tandemforactual.ui.feature.settings.SecurityScreen
import app.tandemforactual.ui.feature.settings.ServerDeviceScreen
import app.tandemforactual.ui.feature.settings.SettingsScreen
import app.tandemforactual.ui.feature.sync.BudgetMutationReviewScreen
import app.tandemforactual.ui.feature.sync.ChangeHistoryScreen
import app.tandemforactual.ui.feature.sync.ConflictInboxScreen
import app.tandemforactual.ui.feature.sync.ConflictReviewScreen
import app.tandemforactual.ui.feature.sync.PendingChangesScreen
import app.tandemforactual.ui.feature.sync.RejectedWriteScreen
import app.tandemforactual.ui.feature.sync.SyncCenterScreen
import app.tandemforactual.ui.feature.sync.UncertainWriteScreen
import app.tandemforactual.ui.feature.transactions.SelectorScreen
import app.tandemforactual.ui.feature.transactions.TransactionDetailScreen
import app.tandemforactual.ui.feature.transactions.TransactionFormScreen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Tags
import kotlinx.coroutines.launch

@Composable
internal fun ScreenContent(
    route: AppRoute,
    isForegroundRoute: Boolean,
    state: AppState,
    deviceName: String,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    reportsDataCache: ReportsDataCache,
    onSignIn: () -> Unit,
    onStartOpenIdSignIn: () -> Unit,
    onRefreshBudgets: () -> Unit,
    onPrepareBudgetUnlock: (BudgetCatalogItem) -> Unit,
    onOpenSelectedBudget: (BudgetCatalogItem) -> Unit,
    onRetrySync: () -> Unit,
    backgroundSyncSupported: Boolean,
    merchantIconMatcher: MerchantIconMatcherPack?,
    merchantIconMatcherLoadError: String?,
    merchantIconCatalog: MerchantIconCatalog?,
    merchantIconCatalogLoadError: String?,
    liveTransactions: List<TransactionItem>,
    liveActivityTransactions: List<TransactionItem>,
    onActivitySelectionTotalChanged: (Long?) -> Unit,
    activitySortCache: ActivityTransactionSortCache,
    liveAccounts: List<LiveAccountSummary>,
    liveSchedules: List<LiveScheduleSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    onSyncNow: () -> Unit,
    onBudgetGroupReorderModeChange: (Boolean) -> Unit,
    onSaveTransaction: (String?, (Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    onCreateTransaction: (String?) -> Unit,
    onResolveConflict: (String, Boolean, String) -> Unit,
    onDiscardWrite: (String) -> Unit,
    onRetryUncertainWrite: (String) -> Unit,
    onCheckUncertainWriteStatus: (String) -> Unit,
    onForgetDevice: () -> Unit,
    onForgetDeviceLocally: () -> Unit,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
    notificationPermissionEnabled: Boolean?,
    onToggleNotificationPermission: ((Boolean) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    when (route) {
        AppRoute.STARTUP -> StartupScreen()
        AppRoute.WELCOME ->
            WelcomeScreen(navigation, mobileSession.signOutNotice) {
                uriHandler.openUri("https://github.com/Nischay-Pro/tandem#getting-started")
            }
        AppRoute.SERVER_ADDRESS -> ServerAddressScreen(state, navigation)
        AppRoute.SIGN_IN -> SignInScreen(state, mobileSession, navigation, onSignIn, onStartOpenIdSignIn)
        AppRoute.BUDGET_CHOOSER ->
            BudgetChooserScreen(state, mobileSession, navigation, onRefreshBudgets, onPrepareBudgetUnlock, onOpenSelectedBudget)
        AppRoute.LOCAL_SERVER_ADDRESS -> LocalServerAddressScreen(state, mobileSession, navigation)
        AppRoute.SWITCH_BUDGET ->
            SwitchBudgetScreen(state, mobileSession, navigation, onRefreshBudgets, onPrepareBudgetUnlock, onOpenSelectedBudget)
        AppRoute.BUDGET_UNLOCK -> BudgetUnlockScreen(state, navigation, onOpenSelectedBudget)
        AppRoute.FIRST_SYNC ->
            FirstSyncScreen(
                state = state,
                mobileSession = mobileSession,
                navigation = navigation,
                onRetrySync = onRetrySync,
                notificationPermissionEnabled = notificationPermissionEnabled,
            )
        AppRoute.SETUP_COMPLETE -> SetupCompleteScreen(state, navigation)
        AppRoute.PERSONALIZE -> PersonalizeScreen(state, navigation)
        AppRoute.HOME_LAYOUT -> HomeLayoutScreen(state, navigation)
        AppRoute.HOME ->
            HomeScreen(
                state,
                mobileSession,
                navigation,
                liveAccounts,
                liveBudgetMonth,
                liveSchedules,
                liveActivityTransactions,
                onSyncNow,
            )
        AppRoute.BUDGET ->
            BudgetScreen(
                state,
                navigation,
                mobileSession,
                liveTransactions,
                liveSchedules,
                liveBudgetMonth,
                onSyncNow,
                onBudgetGroupReorderModeChange,
            )
        AppRoute.CATEGORY_GROUP -> CategoryGroupScreen(state, navigation, liveBudgetMonth, mobileSession)
        AppRoute.CATEGORY_DETAIL -> CategoryDetailScreen(state, navigation, liveBudgetMonth, mobileSession)
        AppRoute.SCHEDULES ->
            UpcomingSchedulesScreen(
                state,
                mobileSession,
                liveSchedules,
                liveAccounts,
                mobileSession.payeeRecords,
            )
        AppRoute.MANAGE_PAYEES ->
            ManagePayeesScreen(
                state,
                navigation,
                mobileSession,
                mobileSession.payeeRecords,
                mobileSession.categoryRecords,
                mobileSession.ruleRecords,
                merchantIconMatcher,
            )
        AppRoute.BUDGET_RULES -> {
            BudgetRulesScreen(
                mobileSession.ruleRecords,
                mobileSession.accountRecords,
                mobileSession.categoryRecords,
                mobileSession.categoryGroupRecords,
                mobileSession.payeeRecords,
                canAddRules = !state.readOnlyMode,
                onOpenRule = { ruleId -> state.ruleEditorRuleId = ruleId },
            )
            RuleEditorSheet(state, mobileSession)
        }
        AppRoute.ACCOUNTS ->
            ListDetailPanes(
                detailKey = state.selectedAccountId.takeIf { selected -> liveAccounts.any { it.id == selected } },
                emptyTitle = "Choose an account",
                emptyMessage = "Its balance and activity appear here.",
                list = { detailInPane ->
                    AccountsScreen(state, navigation, liveAccounts, merchantIconMatcher, mobileSession, detailInPane)
                },
                detail = {
                    AccountDetailScreen(state, navigation, liveAccounts, mobileSession, merchantIconMatcher, isForegroundRoute)
                },
            )
        AppRoute.ACCOUNT_DETAIL ->
            AccountDetailScreen(state, navigation, liveAccounts, mobileSession, merchantIconMatcher, isForegroundRoute)
        AppRoute.ACCOUNT_OPTIONS -> AccountOptionsScreen(state, liveAccounts, mobileSession, navigation)
        AppRoute.CLOSED_ACCOUNTS ->
            AccountsScreen(state, navigation, liveAccounts, merchantIconMatcher, mobileSession, closedOnly = true)
        AppRoute.ACTIVITY ->
            ListDetailPanes(
                detailKey =
                    state.selectedTransactionRecordId.takeIf { selected ->
                        selected.isNotBlank() && liveActivityTransactions.any { it.recordId == selected }
                    },
                emptyTitle = "Choose a transaction",
                emptyMessage = "Its details appear here.",
                list = { detailInPane ->
                    ActivityScreen(
                        state,
                        mobileSession,
                        navigation,
                        liveActivityTransactions,
                        activitySortCache,
                        onActivitySelectionTotalChanged,
                        mobileSession.isSyncing,
                        onSyncNow,
                        detailInPane,
                    )
                },
                detail = { TransactionDetailScreen(state, mobileSession, navigation, merchantIconMatcher, showPayee = true) },
            )
        AppRoute.REPORTS ->
            ReportsScreen(
                state,
                mobileSession,
                liveAccounts,
                reportsDataCache,
                onOpenReport = { kind ->
                    state.selectedReportKindName = kind.name
                    navigation.open(AppRoute.REPORT_DETAIL)
                },
                onOpenCategoryActivity = { categoryId, categoryName, window ->
                    openReportCategoryActivity(state, navigation, categoryId, categoryName, window)
                },
                onAddTransaction = { navigation.newTransaction(null) },
                onChooseBudget = { navigation.open(AppRoute.SWITCH_BUDGET) },
                onOpenInsight = { navigation.open(it) },
            )
        AppRoute.REPORT_DETAIL -> {
            val kind =
                ReportKind.entries.firstOrNull { it.name == state.selectedReportKindName }
                    ?: ReportKind.SPENDING_BY_CATEGORY
            ReportDetailScreen(
                kind,
                state,
                mobileSession,
                liveAccounts,
                reportsDataCache,
                onOpenCategoryActivity = { categoryId, categoryName, window ->
                    openReportCategoryActivity(state, navigation, categoryId, categoryName, window)
                },
                onEmptyAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
            )
        }
        AppRoute.ACTIVITY_SEARCH ->
            ActivitySearchScreen(
                state,
                mobileSession,
                navigation,
                merchantIconMatcher,
                onActivitySelectionTotalChanged,
                autoFocusSearch = true,
            )
        AppRoute.TRANSACTION_DETAIL -> TransactionDetailScreen(state, mobileSession, navigation, merchantIconMatcher)
        AppRoute.NEW_TRANSACTION, AppRoute.EDIT_TRANSACTION ->
            TransactionFormScreen(
                route,
                state,
                navigation,
                mobileSession,
                liveAccounts,
                onSaveTransaction,
                onDeleteTransaction,
                onCreateTransaction,
            )
        AppRoute.CHOOSE_PAYEE -> {
            val scope = rememberCoroutineScope()
            val isLiveBudget = state.selectedBudgetId.isNotBlank()
            val selectingTransferDestination = isLiveBudget && state.transactionType == "Transfer"
            val activePayees =
                remember(mobileSession.payeeRecords) {
                    mobileSession.payeeRecords.filterNot { record ->
                        val body = record.recordBody()
                        record.localDeleted || record.remoteDeleted || body?.booleanValue("tombstone") == true
                    }
                }
            val values =
                remember(isLiveBudget, selectingTransferDestination, state.newTransactionAccountId, activePayees) {
                    if (!isLiveBudget) {
                        listOf("Corner Market", "Electric Company", "Acme Corp", "Riverside Cafe", "City Transit")
                            .map { SelectorItem(it, it) }
                    } else if (selectingTransferDestination) {
                        activePayees
                            .mapNotNull { record ->
                                val destinationId = record.recordBody()?.stringValue("transfer_acct")
                                val name = record.recordName()
                                if (destinationId.isNullOrBlank() ||
                                    destinationId == state.newTransactionAccountId ||
                                    name.isNullOrBlank()
                                ) {
                                    null
                                } else {
                                    val accountName = name.removePrefix("Transfer:").trim().ifBlank { name }
                                    SelectorItem(record.recordId, accountName)
                                }
                            }.distinctBy { it.id }
                    } else {
                        listOf(SelectorItem("", "No payee")) +
                            activePayees
                                .filter { it.recordBody()?.stringValue("transfer_acct").isNullOrBlank() }
                                .mapNotNull { record -> record.recordName()?.let { SelectorItem(record.recordId, it) } }
                                .distinctBy { it.id }
                    }
                }
            var recentPayeeIds by remember(state.selectedBudgetId) { mutableStateOf(emptyList<String>()) }
            LaunchedEffect(state.selectedBudgetId, isLiveBudget) {
                if (isLiveBudget) recentPayeeIds = mobileSession.recentPayeeIds(state.selectedBudgetId)
            }
            val payeeGroups =
                remember(selectingTransferDestination, values, recentPayeeIds) {
                    when {
                        selectingTransferDestination && values.isNotEmpty() -> listOf(SelectorGroup("To account", values))
                        selectingTransferDestination -> emptyList()
                        else -> recentPayeeGroup(values, recentPayeeIds)
                    }
                }
            SelectorScreen(
                values,
                if (isLiveBudget) state.newTransactionPayeeId else state.transactionPayee,
                live = isLiveBudget,
                groups = payeeGroups,
                searchLabel = if (selectingTransferDestination) "Search accounts" else "Search payees",
                searchPlaceholder = if (selectingTransferDestination) "Find an account" else "Find a payee",
                noMatches =
                    if (isLiveBudget) {
                        { query ->
                            val newName = query.trim()
                            if (selectingTransferDestination) {
                                EmptyState("No destination account found", modifier = Modifier.padding(TandemSpacing.large))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                                    if (newName.isNotEmpty()) {
                                        SurfaceCard {
                                            ListRow(
                                                if (mobileSession.isPerformingBudgetAction) "Adding payee…" else "Add $newName",
                                                leading = RowLeading.Icon(Lucide.Plus),
                                                onClick = {
                                                    if (!mobileSession.isPerformingBudgetAction) {
                                                        scope.launch {
                                                            val payeeId =
                                                                mobileSession.createBudgetPayee(
                                                                    state.selectedBudgetId,
                                                                    newName,
                                                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                                                )
                                                            if (payeeId != null) {
                                                                state.transactionPayee = newName
                                                                state.newTransactionPayeeId = payeeId
                                                                navigation.back()
                                                            }
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        EmptyState("No matches", modifier = Modifier.padding(TandemSpacing.large))
                                    }
                                    budgetActionFeedbackMessage(mobileSession)
                                }
                            }
                        }
                    } else {
                        null
                    },
                onSelect = { item ->
                    state.transactionPayee = if (item.label == "No payee") "" else item.label
                    state.newTransactionPayeeId = if (isLiveBudget) item.id else ""
                    if (isLiveBudget && !selectingTransferDestination && state.newTransactionCategoryId.isBlank()) {
                        scope.launch {
                            val categoryId = mobileSession.suggestedCategoryForPayee(state.selectedBudgetId, item.id)
                            val categoryName = mobileSession.categoryRecords.firstOrNull { it.recordId == categoryId }?.recordName()
                            if (categoryId != null && categoryName != null && state.newTransactionCategoryId.isBlank()) {
                                state.newTransactionCategoryId = categoryId
                                state.transactionCategory = categoryName
                            }
                        }
                    }
                    navigation.back()
                },
            )
        }
        AppRoute.CHOOSE_CATEGORY -> {
            val isLiveBudget = state.selectedBudgetId.isNotBlank()
            val visibleMonthGroups =
                liveBudgetMonth
                    ?.groups
                    ?.filter { it.isIncome == (state.transactionType == "Income") }
                    .orEmpty()
            val allLiveCategoryItems =
                if (!isLiveBudget) {
                    emptyList()
                } else {
                    val cachedCategories =
                        mobileSession.categoryRecords
                            .mapNotNull { record ->
                                val body = record.recordBody()
                                if (body?.booleanValue("hidden") == true || body?.booleanValue("deleted") == true) {
                                    null
                                } else {
                                    record.recordName()?.let { SelectorItem(record.recordId, it) }
                                }
                            }
                    val visibleCategories =
                        visibleMonthGroups.flatMap { group ->
                            group.categories.map { category -> SelectorItem(category.id, category.name) }
                        }
                    (cachedCategories + visibleCategories).distinctBy(SelectorItem::id)
                }
            val allCategoryItemsById = allLiveCategoryItems.associateBy(SelectorItem::id)
            val visibleCategoryIds = visibleMonthGroups.flatMap { it.categories }.mapTo(mutableSetOf()) { it.id }
            val visibleCategoryItems =
                if (liveBudgetMonth == null) allLiveCategoryItems else allLiveCategoryItems.filter { it.id in visibleCategoryIds }
            val values =
                if (!isLiveBudget) {
                    listOf("Groceries", "Utilities", "Dining", "Rent", "Transport", "Personal")
                        .map { SelectorItem(it, it) }
                } else {
                    listOf(SelectorItem("", "Uncategorized")) + visibleCategoryItems
                }
            val recentCategoryItems =
                if (!isLiveBudget) {
                    emptyList()
                } else {
                    liveTransactions
                        .mapNotNull { transaction -> transaction.categoryId?.let(allCategoryItemsById::get) }
                        .distinctBy(SelectorItem::id)
                        .take(5)
                }
            val categorySelectorGroups =
                if (!isLiveBudget) {
                    emptyList()
                } else {
                    buildList {
                        if (recentCategoryItems.isNotEmpty()) add(SelectorGroup("Recent", recentCategoryItems))
                        visibleMonthGroups.forEach { group ->
                            val groupItems =
                                group.categories
                                    .mapNotNull { category ->
                                        allCategoryItemsById[category.id] ?: SelectorItem(category.id, category.name)
                                    }.sortedBy { it.label.lowercase() }
                            if (groupItems.isNotEmpty()) add(SelectorGroup(group.name, groupItems))
                        }
                        val groupedIds = flatMap { it.items }.mapTo(mutableSetOf()) { it.id }
                        val ungrouped = visibleCategoryItems.filterNot { it.id in groupedIds }.sortedBy { it.label.lowercase() }
                        if (ungrouped.isNotEmpty()) add(SelectorGroup("Other", ungrouped))
                    }
                }
            val categoryGroups =
                if (!isLiveBudget) {
                    emptyList()
                } else {
                    val visibleGroups = visibleMonthGroups.map { SelectorItem(it.id, it.name) }
                    if (visibleGroups.isNotEmpty()) {
                        visibleGroups
                    } else {
                        mobileSession.categoryGroupRecords.mapNotNull { record ->
                            val body = record.recordBody() ?: return@mapNotNull null
                            val isIncomeGroup = body.booleanValue("is_income") ?: false
                            if (isIncomeGroup != (state.transactionType == "Income") || body.booleanValue("hidden") == true) {
                                return@mapNotNull null
                            }
                            record.recordName()?.let { SelectorItem(record.recordId, it) }
                        }
                    }
                }
            var createGroupId by rememberSaveable(state.selectedBudgetId, state.transactionType) { mutableStateOf("") }
            var createGroupMenuExpanded by rememberSaveable(state.selectedBudgetId, state.transactionType) { mutableStateOf(false) }
            LaunchedEffect(categoryGroups) {
                if (categoryGroups.none { it.id == createGroupId }) {
                    createGroupId = categoryGroups.firstOrNull()?.id.orEmpty()
                }
            }
            SelectorScreen(
                values,
                if (state.selectedBudgetId.isBlank()) state.transactionCategory else state.newTransactionCategoryId,
                live = isLiveBudget,
                groups = categorySelectorGroups,
                searchLabel = "Search categories",
                searchPlaceholder = "Find a category",
                noMatches =
                    if (isLiveBudget) {
                        { query ->
                            val newName = query.trim()
                            if (newName.isBlank()) {
                                EmptyState("Type a category name", modifier = Modifier.padding(TandemSpacing.large))
                            } else {
                                SurfaceCard {
                                    Column(
                                        Modifier.fillMaxWidth().padding(TandemSpacing.large),
                                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                                    ) {
                                        Text(
                                            "Create category",
                                            color = tandemPalette.text,
                                            fontSize = TandemTypography.cardTitle,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (categoryGroups.isEmpty()) {
                                            BodyText("Add a ${state.transactionType.lowercase()} category group in Actual first.")
                                        } else {
                                            Box {
                                                val selectedGroup = categoryGroups.firstOrNull { it.id == createGroupId }
                                                ListRow(
                                                    "Category group",
                                                    leading = RowLeading.Icon(Lucide.Tags),
                                                    trailing = selectedGroup?.label ?: "Choose group",
                                                    onClick = { createGroupMenuExpanded = true },
                                                )
                                                DropdownMenu(
                                                    expanded = createGroupMenuExpanded,
                                                    onDismissRequest = { createGroupMenuExpanded = false },
                                                ) {
                                                    categoryGroups.forEach { group ->
                                                        DropdownMenuItem(
                                                            onClick = {
                                                                createGroupId = group.id
                                                                createGroupMenuExpanded = false
                                                            },
                                                        ) {
                                                            Text(
                                                                group.label,
                                                                color = tandemPalette.text,
                                                                fontSize = TandemTypography.compactBody,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            budgetActionFeedbackMessage(mobileSession)
                                            TandemActionButton(
                                                "Create \"$newName\"",
                                                TandemActionButtonTone.PRIMARY,
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled =
                                                    !state.readOnlyMode &&
                                                        createGroupId.isNotBlank() &&
                                                        newName.length <= 100,
                                            ) {
                                                mobileSession.clearBudgetActionMessage()
                                                scope.launch {
                                                    val categoryId =
                                                        mobileSession.enqueueBudgetCategoryCreate(
                                                            state.selectedBudgetId,
                                                            createGroupId,
                                                            newName,
                                                            visibleMonthGroups
                                                                .firstOrNull { it.id == createGroupId }
                                                                ?.categories
                                                                ?.map { it.id }
                                                                .orEmpty(),
                                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                                        )
                                                    if (categoryId != null) {
                                                        state.transactionCategory = newName
                                                        state.newTransactionCategoryId = categoryId
                                                        navigation.back()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    },
            ) { item ->
                state.transactionCategory = if (item.label == "Uncategorized") "" else item.label
                state.newTransactionCategoryId = if (!isLiveBudget) "" else item.id
                navigation.back()
            }
        }
        AppRoute.SYNC_CENTER ->
            SyncCenterScreen(
                state,
                mobileSession,
                navigation,
                liveTransactions,
                liveBudgetMonth,
                liveSchedules,
                onSyncNow,
            )
        AppRoute.PENDING_CHANGES ->
            PendingChangesScreen(
                state,
                mobileSession,
                navigation,
                liveTransactions,
                liveBudgetMonth,
                liveSchedules,
                onSyncNow,
            )
        AppRoute.CONFLICT_INBOX -> ConflictInboxScreen(state, mobileSession, navigation, liveTransactions, merchantIconMatcher)
        AppRoute.CONFLICT_REVIEW ->
            ConflictReviewScreen(state, mobileSession, navigation, onResolveConflict, liveTransactions, merchantIconMatcher)
        AppRoute.UNCERTAIN_WRITE -> {
            val selectedWrite =
                mobileSession.pendingWrites
                    .firstOrNull { it.operationId == state.selectedConflictOperationId }
            if (selectedWrite?.recordType == "budget-mutation") {
                BudgetMutationReviewScreen(state, mobileSession, navigation, onDiscardWrite)
            } else if (selectedWrite?.status == "rejected") {
                RejectedWriteScreen(state, mobileSession, navigation, onDiscardWrite, liveTransactions)
            } else {
                UncertainWriteScreen(
                    state,
                    mobileSession,
                    navigation,
                    onCheckUncertainWriteStatus,
                    onRetryUncertainWrite,
                    onDiscardWrite,
                    liveTransactions,
                )
            }
        }
        AppRoute.SETTINGS -> SettingsScreen(state, navigation, mobileSession)
        AppRoute.APPEARANCE -> AppearanceScreen(state)
        AppRoute.BUDGET_PREFERENCES -> BudgetPreferencesScreen(state, mobileSession)
        AppRoute.SYNC_PREFERENCES ->
            SyncPreferencesScreen(
                state,
                mobileSession,
                navigation,
                backgroundSyncSupported,
            )
        AppRoute.SERVER_DEVICE ->
            ServerDeviceScreen(state, deviceName, mobileSession, navigation, onForgetDevice, onForgetDeviceLocally)
        AppRoute.SECURITY_PRIVACY -> SecurityScreen(state, mobileSession)
        AppRoute.NOTIFICATIONS ->
            NotificationsScreen(
                state,
                onRequestNotificationPermission,
                onOpenNotificationSettings,
                notificationPermissionEnabled,
                onToggleNotificationPermission,
            )
        AppRoute.LOCAL_DATA -> LocalDataScreen(state, mobileSession, navigation)
        AppRoute.ABOUT -> AboutScreen(merchantIconMatcherLoadError, navigation)
        AppRoute.MERCHANT_ICON_LICENSES -> MerchantIconLicensesScreen(merchantIconCatalog, merchantIconCatalogLoadError)
        AppRoute.CHANGE_HISTORY -> ChangeHistoryScreen(state, mobileSession, deviceName)
        AppRoute.BANK_CONNECTIONS -> BankConnectionsScreen(state, mobileSession, liveAccounts)
        AppRoute.REIMBURSEMENTS -> ReimbursementsScreen(state, mobileSession)
        AppRoute.RETIREMENT -> RetirementScreen(state, navigation, mobileSession, liveAccounts)
        AppRoute.RETIREMENT_SETUP -> RetirementSetupScreen(state, navigation, mobileSession, liveAccounts)
        AppRoute.RETIREMENT_MATCH -> MatchEditorScreen(state, navigation, mobileSession)
        AppRoute.WHAT_IF -> WhatIfScreen(state, mobileSession, liveAccounts, liveBudgetMonth, liveTransactions)
        AppRoute.EXPORT -> ExportScreen(state, mobileSession)
        AppRoute.CASH_FLOW -> CashFlowScreen(state, liveAccounts, liveSchedules)
        AppRoute.MONTHLY_REVIEW -> MonthlyReviewScreen(state, liveBudgetMonth, liveTransactions)
        AppRoute.DATA_HEALTH -> DataHealthScreen(state, navigation, mobileSession, liveTransactions)
        AppRoute.DEBT_PAYOFF -> DebtPayoffScreen(state, mobileSession, liveAccounts, liveBudgetMonth)
        AppRoute.EXCHANGE_RATES -> ExchangeRatesScreen(state, mobileSession, liveAccounts, navigation)
        AppRoute.HOUSEHOLD -> HouseholdScreen(state, mobileSession, deviceName)
        AppRoute.SPENDING_TRENDS -> SpendingTrendsScreen(state, mobileSession)
        AppRoute.SPENDING_FORECAST -> SpendingForecastScreen(state, mobileSession, liveBudgetMonth)
        AppRoute.GOAL_TIMELINE -> GoalTimelineScreen(state, mobileSession, liveAccounts, liveBudgetMonth)
        AppRoute.CUSTOM_REPORTS -> CustomReportsScreen(state, mobileSession, navigation)
        AppRoute.CUSTOM_REPORT -> CustomReportScreen(state, mobileSession, navigation)
    }
}

/** The payees used most recently, in that order, shown above the full list. */
internal fun recentPayeeGroup(
    values: List<SelectorItem>,
    recentPayeeIds: List<String>,
): List<SelectorGroup> {
    val byId = values.associateBy { it.id }
    val recent = recentPayeeIds.mapNotNull { byId[it] }
    return if (recent.isEmpty() || values.size <= 8) emptyList() else listOf(SelectorGroup("Recent", recent))
}
