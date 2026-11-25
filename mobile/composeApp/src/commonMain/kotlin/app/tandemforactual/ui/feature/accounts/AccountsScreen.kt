@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.budgetCurrencyCode
import app.tandemforactual.ui.core.format.convertedBalance
import app.tandemforactual.ui.core.format.formatAccountMoney
import app.tandemforactual.ui.core.format.formatConverted
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budget.clearBudgetReorderMessage
import app.tandemforactual.ui.core.session.budget.moveBudgetAccount
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.state.AccountSort
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.itemTargetIdAfterMove
import app.tandemforactual.ui.core.state.liveAccountGroupLabel
import app.tandemforactual.ui.core.state.reorderItemToIndex
import app.tandemforactual.ui.core.ui.bankLinkBadge
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun AccountsScreen(
    state: AppState,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    merchantIconMatcher: MerchantIconMatcherPack?,
    mobileSession: TandemMobileSession,
    detailInPane: Boolean = false,
    closedOnly: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val addAccountSheet = rememberAddAccountSheetState()
    var accountSearchQuery by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    LaunchedEffect(state.accountsSearchExpanded) {
        if (!state.accountsSearchExpanded) accountSearchQuery = ""
    }
    var selectedAccountFilter by rememberSaveable(state.selectedBudgetId) { mutableStateOf("All") }
    AccountsScreenEffects(state, navigation, mobileSession, addAccountSheet)
    val isRefreshing = mobileSession.isSyncing || mobileSession.isPerformingBudgetAction
    val reorder = rememberAccountReorder(state, mobileSession, liveAccounts)
    val accountOrderIds = reorder.drag.displayOrder ?: reorder.drag.currentOrder(reorder.queuedOrder)
    val orderedLiveAccounts = remember(liveAccounts, accountOrderIds) { orderAccountsForList(liveAccounts, accountOrderIds) }
    AccountSortEffect(state, reorder, orderedLiveAccounts)
    val runBankSync: () -> Unit = {
        if (!isRefreshing && mobileSession.isAuthenticated && !state.readOnlyMode && state.selectedBudgetId.isNotBlank()) {
            scope.launch {
                mobileSession.banking.runBankSync(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val model = rememberAccountListModel(state, mobileSession, isLiveBudget, orderedLiveAccounts)
    val closedAccountCount = model.groups.filter { it.name == CLOSED_ACCOUNT_GROUP }.sumOf { it.accounts.size }
    val visibleGroups = visibleAccountGroups(state, mobileSession, model, closedOnly, selectedAccountFilter, accountSearchQuery)

    PageLazyColumn(
        verticalSpacing = TandemSpacing.small,
        isRefreshing = isRefreshing,
        onRefresh = runBankSync.takeIf { isLiveBudget && mobileSession.isAuthenticated && !state.readOnlyMode },
    ) {
        mobileSession.budgetActionMessage?.let {
            item(key = "account-action-message") { budgetActionFeedbackMessage(mobileSession) }
        }
        if (state.accountsSearchExpanded && !closedOnly) {
            item(key = "account-search-field") {
                AccountSearchField(
                    value = accountSearchQuery,
                    onValueChange = { accountSearchQuery = it },
                    onClear = { accountSearchQuery = "" },
                )
            }
        }
        if (!closedOnly) {
            netWorthItem(state, navigation, mobileSession, orderedLiveAccounts)
            item(key = "account-type-filters") {
                AccountTypeFilters(selectedAccountFilter) { selectedAccountFilter = it }
            }
        }
        if (closedOnly && visibleGroups.isEmpty()) {
            closedAccountsEmptyItem()
        } else if (model.groups.isEmpty()) {
            accountsEmptyItem(isLiveBudget, navigation)
        } else if (visibleGroups.isEmpty() && closedAccountCount == 0) {
            accountsFilterEmptyItem()
        } else {
            accountGroupItems(visibleGroups, closedOnly, reorder, state, navigation, mobileSession, merchantIconMatcher, detailInPane)
        }
        if (!closedOnly && closedAccountCount > 0 && accountSearchQuery.isBlank()) {
            closedAccountsEntryItem(closedAccountCount, navigation)
        }
        if (isLiveBudget && !closedOnly) {
            accountBottomActionsItem(isRefreshing, state.readOnlyMode, navigation) { addAccountSheet.open(mobileSession) }
        }
    }
    AddAccountSheet(addAccountSheet, state, mobileSession)
}

@Composable
private fun rememberAccountListModel(
    state: AppState,
    mobileSession: TandemMobileSession,
    isLiveBudget: Boolean,
    orderedLiveAccounts: List<LiveAccountSummary>,
): AccountListUi =
    remember(
        isLiveBudget,
        orderedLiveAccounts,
        state.budgetDisplaySettings,
        mobileSession.localSyncState?.lastSyncedAtEpochMs,
        mobileSession.planning.accountCurrencies,
        mobileSession.planning.exchangeRates,
    ) {
        if (isLiveBudget) {
            val groupedAccounts =
                orderedLiveAccounts
                    .groupBy(::liveAccountGroupLabel)
                    .entries
                    .sortedWith(
                        compareBy(
                            { accountGroupOrder(it.key) },
                            { it.key.lowercase() },
                        ),
                    ).map { (groupName, accounts) ->
                        AccountListGroupUi(
                            groupName,
                            state.formatConverted(mobileSession.convertedBalance(state, accounts.map { it.id to it.balanceMinor })),
                            accounts.map { account ->
                                AccountListItemUi(
                                    account.id,
                                    account.name,
                                    accountListTypeLabel(account.type),
                                    accountListSubtitle(account, mobileSession.localSyncState?.lastSyncedAtEpochMs),
                                    account.balanceMinor?.let {
                                        state.formatAccountMoney(
                                            it,
                                            mobileSession.planning.accountCurrencies[account.id],
                                        )
                                    }
                                        ?: "Unavailable",
                                    account.offBudget,
                                    account.closed,
                                    account.type ?: account.accountGroupName,
                                    account.balanceMinor,
                                )
                            },
                        )
                    }
            AccountListUi(
                groupedAccounts,
            )
        } else {
            AccountListUi(
                groups = emptyList(),
            )
        }
    }

private fun visibleAccountGroups(
    state: AppState,
    mobileSession: TandemMobileSession,
    model: AccountListUi,
    closedOnly: Boolean,
    selectedAccountFilter: String,
    accountSearchQuery: String,
): List<AccountListGroupUi> =
    model.groups.filter { (it.name == CLOSED_ACCOUNT_GROUP) == closedOnly }.mapNotNull { group ->
        val accounts =
            group.accounts.filter { account ->
                accountMatchesFilter(account, group.name, selectedAccountFilter, accountSearchQuery)
            }
        if (accounts.isEmpty()) {
            null
        } else {
            group.copy(
                total = state.formatConverted(mobileSession.convertedBalance(state, accounts.map { it.id to it.balanceMinor })),
                accounts = accounts,
            )
        }
    }

/** Approximate net worth across currencies, shown when any open account is in a foreign currency. */
private fun LazyListScope.netWorthItem(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    orderedLiveAccounts: List<LiveAccountSummary>,
) {
    val hasForeignAccounts =
        orderedLiveAccounts.any {
            !it.closed &&
                mobileSession.planning.accountCurrencies[it.id]?.let { code -> code != state.budgetCurrencyCode } == true
        }
    if (!hasForeignAccounts) return
    item(key = "account-net-worth") {
        val total =
            mobileSession.convertedBalance(
                state,
                orderedLiveAccounts.filter { !it.closed }.map { it.id to it.balanceMinor },
            )
        SurfaceCard {
            ListRow(
                "Net worth",
                if (total.missingRates.isEmpty()) {
                    "Approximate, in ${state.budgetCurrencyCode} with your exchange rates"
                } else {
                    "Leaves out ${total.missingRates.sorted().joinToString()} until you add a rate"
                },
                state.formatConverted(total),
                leading = RowLeading.Icon(Lucide.Coins),
                onClick = { navigation.open(AppRoute.EXCHANGE_RATES) },
            )
        }
    }
}

/** Group headers and account cards for each visible account group. */
private fun LazyListScope.accountGroupItems(
    visibleGroups: List<AccountListGroupUi>,
    closedOnly: Boolean,
    reorder: AccountReorder,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    merchantIconMatcher: MerchantIconMatcherPack?,
    detailInPane: Boolean,
) {
    visibleGroups.forEach { group ->
        if (!closedOnly) {
            item(key = "account-group:${group.name}") { AccountGroupHeader(group, state) }
        }
        val movableAccountIds = group.accounts.map(AccountListItemUi::id)
        group.accounts.forEach { account ->
            item(key = "account:${account.id}") {
                val link = mobileSession.banking.bankLinks.firstOrNull { it.id == account.id }
                AccountListCard(
                    account = account,
                    movableAccountIds = movableAccountIds,
                    reorder = reorder,
                    bankBadge = bankLinkBadge(link, mobileSession.nowEpochMillis()),
                    bankId = link?.bankId,
                    state = state,
                    navigation = navigation,
                    merchantIconMatcher = merchantIconMatcher,
                    detailInPane = detailInPane,
                )
            }
        }
    }
}

@Composable
private fun rememberAccountReorder(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
): AccountReorder {
    val scope = rememberCoroutineScope()
    val serverAccountOrder = remember(liveAccounts) { sortAccountsForList(liveAccounts).map { it.id } }
    val accountOrderStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, "accounts")
    val queuedAccountMoves =
        mobileSession
            .budgetQueue
            .budgetReorderMovesFor(state.selectedBudgetId, "accounts")
            .filter { it.kind == BudgetReorderKind.MOVE_ACCOUNT }
    val queuedAccountOrder = budgetOrderForScreen(serverAccountOrder, accountOrderStatus, queuedAccountMoves)
    val selectedAccountOrderId = rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    val dragReorder = remember(state.selectedBudgetId) { DragReorderState() }
    return AccountReorder(state, mobileSession, scope, serverAccountOrder, queuedAccountOrder, dragReorder, selectedAccountOrderId)
}

/** Account ordering for the list: queued moves, drag state and the reorder selection. */
internal class AccountReorder(
    private val state: AppState,
    private val mobileSession: TandemMobileSession,
    private val scope: CoroutineScope,
    private val serverOrder: List<String>,
    val queuedOrder: List<String>,
    val drag: DragReorderState,
    selectedAccountOrderId: MutableState<String?>,
) {
    var selectedAccountOrderId by selectedAccountOrderId

    private fun latestOrder(): List<String> =
        drag.displayOrder
            ?: drag.currentOrder(
                budgetOrderForScreen(
                    serverOrder,
                    mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, "accounts"),
                    mobileSession
                        .budgetQueue
                        .budgetReorderMovesFor(state.selectedBudgetId, "accounts")
                        .filter { it.kind == BudgetReorderKind.MOVE_ACCOUNT },
                ),
            )

    fun enqueueMove(
        accountId: String,
        direction: Int,
        movableAccountIds: List<String>,
    ) {
        val currentOrder = latestOrder()
        val currentGroupOrder = currentOrder.filter { it in movableAccountIds }
        val nextGroupOrder = reorderItemToIndex(currentGroupOrder, accountId, currentGroupOrder.indexOf(accountId) + direction)
        if (nextGroupOrder == currentGroupOrder) return
        val nextFullOrder = reorderAccountsWithinSlots(currentOrder, movableAccountIds, nextGroupOrder)
        scope.launch {
            mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, "accounts")
            mobileSession.budgetEdits.moveBudgetAccount(
                state.selectedBudgetId,
                accountId,
                itemTargetIdAfterMove(nextGroupOrder, accountId),
                state.encryptedPassword.takeIf(String::isNotBlank),
                nextFullOrder,
                baseOrder = serverOrder,
            )
        }
    }

    /** Saves [sort] as the budget's account order, so Actual and other devices show it too. */
    fun applySort(
        sort: AccountSort,
        ordered: List<LiveAccountSummary>,
    ) {
        val next = sortAccountsForBudget(ordered, sort).map { it.id }
        if (next.isEmpty() || next == ordered.map { it.id }) return
        scope.launch {
            mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, "accounts")
            mobileSession.budgetEdits.moveBudgetAccount(
                state.selectedBudgetId,
                next.first(),
                next.getOrNull(1),
                state.encryptedPassword.takeIf(String::isNotBlank),
                next,
                baseOrder = serverOrder,
            )
        }
    }

    fun startDrag(
        accountId: String,
        movableAccountIds: List<String>,
    ) {
        selectedAccountOrderId = accountId
        mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, "accounts")
        mobileSession.feedback.clearBudgetActionMessage()
        val fullOrder = latestOrder()
        drag.start(
            itemOrder = fullOrder.filter { it in movableAccountIds },
            itemId = accountId,
            fullDisplayOrder = fullOrder,
        )
    }

    fun dragBy(
        accountId: String,
        movableAccountIds: List<String>,
        delta: Float,
    ) {
        if (drag.draggedItemId == accountId) {
            drag.dragBy(delta) { groupOrder, fullOrder ->
                reorderAccountsWithinSlots(fullOrder, movableAccountIds, groupOrder)
            }
        }
    }

    fun dropDrag(accountId: String) {
        drag.finish()?.let { result ->
            if (result.itemId == accountId && result.finalOrder != result.startingOrder) {
                scope.launch {
                    val saved =
                        mobileSession.budgetEdits.moveBudgetAccount(
                            state.selectedBudgetId,
                            result.itemId,
                            itemTargetIdAfterMove(result.finalOrder, result.itemId),
                            state.encryptedPassword.takeIf(String::isNotBlank),
                            result.finalDisplayOrder,
                            baseOrder = serverOrder,
                        )
                    if (!saved) drag.releaseHeldDrop()
                }
            }
        }
    }
}

/** Runs a sort chosen from the accounts menu once the screen has the current order. */
@Composable
private fun AccountSortEffect(
    state: AppState,
    reorder: AccountReorder,
    ordered: List<LiveAccountSummary>,
) {
    val requested =
        state.accountsRequestedAction?.removePrefix("sort:")?.takeIf {
            state.accountsRequestedAction?.startsWith("sort:") ==
                true
        }
    LaunchedEffect(ordered) { state.accountsActiveSort = activeAccountSort(ordered) }
    LaunchedEffect(requested) {
        val sort = AccountSort.entries.firstOrNull { it.name == requested } ?: return@LaunchedEffect
        state.accountsRequestedAction = null
        reorder.applySort(sort, ordered)
    }
}
