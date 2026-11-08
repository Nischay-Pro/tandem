@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.reports.ReportKind
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Tags
import kotlinx.coroutines.launch

@Composable
internal fun ScreenContent(
    route: AppRoute,
    state: AppState,
    deviceName: String,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    reportsDataCache: ReportsDataCache,
    onPairDevice: () -> Unit,
    onRetryStoredSession: () -> Unit,
    onSelectBudget: (BudgetCatalogItem) -> Unit,
    onRefreshBudgets: () -> Unit,
    onOpenSelectedBudget: () -> Unit,
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
    onSaveTransaction: ((Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    onCreateTransaction: () -> Unit,
    onResolveConflict: (String, Boolean) -> Unit,
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
    when (route) {
        AppRoute.STARTUP -> StartupScreen()
        AppRoute.WELCOME -> WelcomeScreen(navigation)
        AppRoute.SERVER_ADDRESS -> ServerAddressScreen(state, navigation)
        AppRoute.PAIRING_OPTIONS -> PairingOptionsScreen(navigation)
        AppRoute.PAIRING_CODE -> PairingCodeScreen(state, mobileSession, navigation, onPairDevice, onRetryStoredSession)
        AppRoute.PAIRING_RESULT -> PairingResultScreen(mobileSession, navigation)
        AppRoute.BUDGET_CHOOSER ->
            BudgetChooserScreen(state, mobileSession, navigation, onSelectBudget, onRefreshBudgets, onOpenSelectedBudget)
        AppRoute.UNLOCK_BUDGET -> UnlockBudgetScreen(state, navigation)
        AppRoute.FIRST_SYNC -> FirstSyncScreen(state, mobileSession, navigation, onRetrySync)
        AppRoute.BUDGET -> BudgetScreen(state, navigation, mobileSession, liveTransactions, liveSchedules, liveBudgetMonth, onSyncNow)
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
                mobileSession,
                mobileSession.payeeRecords,
                mobileSession.categoryRecords,
                mobileSession.ruleRecords,
            )
        AppRoute.BUDGET_RULES ->
            BudgetRulesScreen(
                mobileSession.ruleRecords,
                mobileSession.accountRecords,
                mobileSession.categoryRecords,
                mobileSession.categoryGroupRecords,
                mobileSession.payeeRecords,
            )
        AppRoute.ACCOUNTS ->
            AccountsScreen(
                state,
                navigation,
                liveAccounts,
                merchantIconMatcher,
                mobileSession,
            )
        AppRoute.ACCOUNT_DETAIL ->
            AccountDetailScreen(state, navigation, liveAccounts, mobileSession, merchantIconMatcher)
        AppRoute.ACCOUNT_OPTIONS -> AccountOptionsScreen(state, liveAccounts, mobileSession, navigation)
        AppRoute.ACTIVITY ->
            ActivityScreen(
                state,
                mobileSession,
                navigation,
                liveActivityTransactions,
                activitySortCache,
                onActivitySelectionTotalChanged,
                mobileSession.isSyncing,
                onSyncNow,
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
                onOpenActivity = { navigation.selectTab(AppRoute.ACTIVITY) },
                onAddTransaction = { navigation.newTransaction(null) },
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
                onEmptyAction =
                    if (state.selectedBudgetId.isBlank()) {
                        { navigation.selectTab(AppRoute.ACTIVITY) }
                    } else {
                        { navigation.newTransaction(null) }
                    },
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
            val values =
                if (!isLiveBudget) {
                    listOf("Corner Market", "Electric Company", "Acme Corp", "Riverside Cafe", "City Transit")
                        .map { SelectorItem(it, it) }
                } else {
                    listOf(SelectorItem("", "No payee")) +
                        mobileSession.payeeRecords
                            .mapNotNull { record ->
                                record.recordName()?.let { SelectorItem(record.recordId, it) }
                            }.distinctBy { it.id }
                }
            val payeeGroups =
                if (!isLiveBudget) {
                    emptyList()
                } else {
                    buildList {
                        val transfers = values.filter { it.id.isNotBlank() && it.label.startsWith("Transfer:", ignoreCase = true) }
                        val payees = values.filter { it.id.isNotBlank() && !it.label.startsWith("Transfer:", ignoreCase = true) }
                        if (transfers.isNotEmpty()) add(SelectorGroup("Transfers", transfers))
                        if (payees.isNotEmpty()) add(SelectorGroup("Payees", payees))
                    }
                }
            SelectorScreen(
                values,
                if (isLiveBudget) state.newTransactionPayeeId else state.transactionPayee,
                live = isLiveBudget,
                groups = payeeGroups,
                searchLabel = "Search payees",
                searchPlaceholder = "Find a payee",
                topContent =
                    if (isLiveBudget) {
                        {
                            SurfaceCard {
                                ListRow(
                                    "Manage payees",
                                    leading = RowLeading.Icon(Lucide.Tags),
                                ) { navigation.open(AppRoute.MANAGE_PAYEES) }
                            }
                        }
                    } else {
                        null
                    },
                noMatches =
                    if (isLiveBudget) {
                        { query ->
                            val newName = query.trim()
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
                                mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                            }
                        }
                    } else {
                        null
                    },
                onSelect = { item ->
                    state.transactionPayee = if (item.label == "No payee") "" else item.label
                    state.newTransactionPayeeId = if (isLiveBudget) item.id else ""
                    navigation.back()
                },
            )
        }
        AppRoute.CHOOSE_CATEGORY -> {
            val scope = rememberCoroutineScope()
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
                    mobileSession.categoryRecords
                        .mapNotNull { record ->
                            val body = record.recordBody()
                            if (body?.booleanValue("hidden") == true || body?.booleanValue("deleted") == true) {
                                null
                            } else {
                                record.recordName()?.let { SelectorItem(record.recordId, it) }
                            }
                        }.distinctBy(SelectorItem::id)
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
                    mobileSession.categoryGroupRecords.mapNotNull { record ->
                        val body = record.recordBody() ?: return@mapNotNull null
                        val isIncomeGroup = body.booleanValue("is_income") ?: false
                        if (isIncomeGroup != (state.transactionType == "Income") || body.booleanValue("hidden") == true) {
                            return@mapNotNull null
                        }
                        record.recordName()?.let { SelectorItem(record.recordId, it) }
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
                                            mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                                            TandemActionButton(
                                                if (mobileSession.isPerformingBudgetAction) "Creating…" else "Create \"$newName\"",
                                                TandemActionButtonTone.PRIMARY,
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled =
                                                    !state.readOnlyMode &&
                                                        !mobileSession.isPerformingBudgetAction &&
                                                        createGroupId.isNotBlank() &&
                                                        newName.length <= 100,
                                                loading = mobileSession.isPerformingBudgetAction,
                                            ) {
                                                mobileSession.clearBudgetActionMessage()
                                                scope.launch {
                                                    val categoryId =
                                                        mobileSession.createBudgetCategory(
                                                            state.selectedBudgetId,
                                                            newName,
                                                            createGroupId,
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
        AppRoute.SYNC_CENTER -> SyncCenterScreen(state, mobileSession, navigation, liveTransactions)
        AppRoute.CONFLICT_INBOX -> ConflictInboxScreen(state, mobileSession, navigation, liveTransactions)
        AppRoute.CONFLICT_REVIEW -> ConflictReviewScreen(state, mobileSession, onResolveConflict, liveTransactions)
        AppRoute.UNCERTAIN_WRITE ->
            UncertainWriteScreen(
                state,
                mobileSession,
                navigation,
                onCheckUncertainWriteStatus,
                onRetryUncertainWrite,
                onDiscardWrite,
                liveTransactions,
            )
        AppRoute.REJECTED_WRITE -> RejectedWriteScreen(state, mobileSession, navigation, onDiscardWrite, liveTransactions)
        AppRoute.SETTINGS -> SettingsScreen(state, navigation)
        AppRoute.APPEARANCE -> AppearanceScreen(state)
        AppRoute.BUDGET_PREFERENCES -> BudgetPreferencesScreen(state, mobileSession)
        AppRoute.SYNC_PREFERENCES ->
            SyncPreferencesScreen(
                state,
                mobileSession,
                onSyncNow,
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
        AppRoute.LOCAL_DATA -> LocalDataScreen(state, mobileSession, onSyncNow)
        AppRoute.ABOUT -> AboutScreen(merchantIconMatcherLoadError, navigation)
        AppRoute.MERCHANT_ICON_LICENSES -> MerchantIconLicensesScreen(merchantIconCatalog, merchantIconCatalogLoadError)
    }
}
