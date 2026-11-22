@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budget.clearBudgetReorderMessage
import app.tandemforactual.ui.core.session.budget.createBudgetAccount
import app.tandemforactual.ui.core.session.budget.moveBudgetAccount
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.session.reorderModeConsumesItemTap
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.liveAccountGroupLabel
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.CheckBoxRow
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.banking.bankLinkBadge
import app.tandemforactual.ui.feature.budget.itemTargetIdAfterMove
import app.tandemforactual.ui.feature.budget.reorderItemToIndex
import app.tandemforactual.ui.feature.currency.budgetCurrencyCode
import app.tandemforactual.ui.feature.currency.convertedBalance
import app.tandemforactual.ui.feature.currency.formatAccountMoney
import app.tandemforactual.ui.feature.currency.formatConverted
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.flow.first
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
    var addAccountDialog by rememberSaveable { mutableStateOf(false) }
    var newAccountName by rememberSaveable { mutableStateOf("") }
    var newAccountOffBudget by rememberSaveable { mutableStateOf(false) }
    var newAccountGroupId by rememberSaveable { mutableStateOf("") }
    var accountGroupMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var accountSearchQuery by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    LaunchedEffect(state.accountsSearchExpanded) {
        if (!state.accountsSearchExpanded) accountSearchQuery = ""
    }
    var selectedAccountFilter by rememberSaveable(state.selectedBudgetId) { mutableStateOf("All") }
    val accountGroupOptions =
        remember(mobileSession.accountGroupRecords) {
            mobileSession.accountGroupRecords
                .mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                .sortedBy { it.second.lowercase() }
        }
    LaunchedEffect(state.selectedBudgetId) {
        if (state.selectedBudgetId.isNotBlank()) {
            runCatching {
                mobileSession.banking.loadLinkedAccounts(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    LaunchedEffect(state.accountsRequestedAction) {
        when (state.accountsRequestedAction) {
            "add" -> {
                newAccountName = ""
                newAccountOffBudget = false
                newAccountGroupId = ""
                mobileSession.feedback.clearBudgetActionMessage()
                addAccountDialog = true
            }
            "banks" -> navigation.open(AppRoute.BANK_CONNECTIONS)
        }
        state.accountsRequestedAction = null
    }
    val isRefreshing = mobileSession.isSyncing || mobileSession.isPerformingBudgetAction
    val serverAccountOrder = remember(liveAccounts) { sortAccountsForList(liveAccounts).map { it.id } }
    val accountOrderStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, "accounts")
    val queuedAccountMoves =
        mobileSession
            .budgetQueue
            .budgetReorderMovesFor(state.selectedBudgetId, "accounts")
            .filter { it.kind == BudgetReorderKind.MOVE_ACCOUNT }
    val queuedAccountOrder = budgetOrderForScreen(serverAccountOrder, accountOrderStatus, queuedAccountMoves)
    var selectedAccountOrderId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    val dragReorder = remember(state.selectedBudgetId) { DragReorderState() }
    val accountOrderIds = dragReorder.displayOrder ?: dragReorder.currentOrder(queuedAccountOrder)
    val orderedLiveAccounts =
        remember(liveAccounts, accountOrderIds) {
            orderAccountsForList(liveAccounts, accountOrderIds)
        }

    fun latestAccountOrder(): List<String> =
        dragReorder.displayOrder
            ?: dragReorder.currentOrder(
                budgetOrderForScreen(
                    serverAccountOrder,
                    mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, "accounts"),
                    mobileSession
                        .budgetQueue
                        .budgetReorderMovesFor(state.selectedBudgetId, "accounts")
                        .filter { it.kind == BudgetReorderKind.MOVE_ACCOUNT },
                ),
            )

    fun enqueueAccountMove(
        accountId: String,
        direction: Int,
        movableAccountIds: List<String>,
    ) {
        val currentOrder = latestAccountOrder()
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
                baseOrder = serverAccountOrder,
            )
        }
    }
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
    val model =
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
    val closedAccountCount = model.groups.filter { it.name == CLOSED_ACCOUNT_GROUP }.sumOf { it.accounts.size }
    val visibleGroups =
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
        val hasForeignAccounts =
            orderedLiveAccounts.any {
                !it.closed &&
                    mobileSession.planning.accountCurrencies[it.id]?.let { code -> code != state.budgetCurrencyCode } == true
            }
        if (!closedOnly && hasForeignAccounts) {
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
        if (!closedOnly) {
            item(key = "account-type-filters") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    listOf("All", "Bank", "Credit", "Other").forEach { filter ->
                        AccountFilterChip(filter, selected = selectedAccountFilter == filter) {
                            selectedAccountFilter = filter
                        }
                    }
                }
            }
        }
        if (closedOnly && visibleGroups.isEmpty()) {
            item(key = "closed-accounts-empty") {
                SurfaceCard {
                    EmptyState(
                        "No closed accounts",
                        "Accounts you close in Tandem or Actual appear here.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else if (model.groups.isEmpty()) {
            item(key = "accounts-empty") {
                SurfaceCard {
                    EmptyState(
                        if (isLiveBudget) "No accounts yet" else "Choose a budget",
                        if (isLiveBudget) {
                            "Sync this budget to load its account list and balances."
                        } else {
                            "Open an Actual budget to view its accounts."
                        },
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                        actionLabel = "Choose budget".takeIf { !isLiveBudget },
                        onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) }.takeIf { !isLiveBudget },
                    )
                }
            }
        } else if (visibleGroups.isEmpty() && closedAccountCount == 0) {
            item(key = "accounts-filter-empty") {
                SurfaceCard {
                    EmptyState(
                        "No matching accounts",
                        "Try a different search or account type.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            visibleGroups.forEach { group ->
                if (!closedOnly) {
                    item(key = "account-group:${group.name}") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                group.name,
                                color = tandemPalette.text,
                                fontSize = TandemTypography.label,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            PrivacyAmountText(
                                group.total,
                                privacyModeEnabled = state.privacyModeEnabled,
                                fractionDigits = state.currencyFractionDigitsForDisplay,
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                            )
                        }
                    }
                }
                val movableAccountIds = group.accounts.map(AccountListItemUi::id)
                group.accounts.forEach { account ->
                    item(key = "account:${account.id}") {
                        val isReordering = selectedAccountOrderId == account.id
                        val currentFullOrder = dragReorder.displayOrder ?: dragReorder.currentOrder(queuedAccountOrder)
                        val currentGroupOrder = currentFullOrder.filter { it in movableAccountIds }
                        val currentIndex = currentGroupOrder.indexOf(account.id)
                        val canMoveUp = currentIndex > 0
                        val canMoveDown = currentIndex >= 0 && currentIndex < currentGroupOrder.lastIndex

                        fun startDrag() {
                            selectedAccountOrderId = account.id
                            mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, "accounts")
                            mobileSession.feedback.clearBudgetActionMessage()
                            val fullOrder = latestAccountOrder()
                            dragReorder.start(
                                itemOrder = fullOrder.filter { it in movableAccountIds },
                                itemId = account.id,
                                fullDisplayOrder = fullOrder,
                            )
                        }

                        fun dragBy(delta: Float) {
                            if (dragReorder.draggedItemId == account.id) {
                                dragReorder.dragBy(delta) { groupOrder, fullOrder ->
                                    reorderAccountsWithinSlots(fullOrder, movableAccountIds, groupOrder)
                                }
                            }
                        }

                        fun dropDrag() {
                            dragReorder.finish()?.let { result ->
                                if (result.itemId == account.id && result.finalOrder != result.startingOrder) {
                                    scope.launch {
                                        val saved =
                                            mobileSession.budgetEdits.moveBudgetAccount(
                                                state.selectedBudgetId,
                                                result.itemId,
                                                itemTargetIdAfterMove(result.finalOrder, result.itemId),
                                                state.encryptedPassword.takeIf(String::isNotBlank),
                                                result.finalDisplayOrder,
                                                baseOrder = serverAccountOrder,
                                            )
                                        if (!saved) dragReorder.releaseHeldDrop()
                                    }
                                }
                            }
                        }
                        val accountCardModifier =
                            dragReorderItemModifier(
                                itemId = account.id,
                                dragState = dragReorder,
                                reduceMotion = tandemReduceMotion,
                                dragEnabled = !state.readOnlyMode && movableAccountIds.size > 1,
                                onDragStarted = ::startDrag,
                                onDragBy = ::dragBy,
                                onDragStopped = ::dropDrag,
                            )
                        AccountSummaryCard(
                            bankBadge =
                                bankLinkBadge(
                                    mobileSession.banking.bankLinks.firstOrNull { it.id == account.id },
                                    mobileSession.nowEpochMillis(),
                                ),
                            account = account,
                            merchantArtwork = merchantIconMatcher?.accountIconFor(account.name),
                            privacyModeEnabled = state.privacyModeEnabled,
                            fractionDigits = state.currencyFractionDigitsForDisplay,
                            modifier = accountCardModifier,
                            isReordering = isReordering,
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            reorderEnabled = !state.readOnlyMode,
                            highlighted = detailInPane && account.id == state.selectedAccountId,
                            onClick = {
                                if (reorderModeConsumesItemTap(selectedAccountOrderId)) {
                                    selectedAccountOrderId = null
                                } else {
                                    state.selectedAccountId = account.id
                                    if (!detailInPane) navigation.open(AppRoute.ACCOUNT_DETAIL)
                                }
                            },
                            onLongClick = {
                                if (!state.readOnlyMode &&
                                    (selectedAccountOrderId != null || movableAccountIds.size > 1)
                                ) {
                                    selectedAccountOrderId = account.id
                                }
                            },
                            onEdit = {
                                selectedAccountOrderId = null
                                state.selectedAccountId = account.id
                                navigation.open(AppRoute.ACCOUNT_OPTIONS)
                            },
                            onMoveUp = { enqueueAccountMove(account.id, -1, movableAccountIds) },
                            onMoveDown = { enqueueAccountMove(account.id, 1, movableAccountIds) },
                            onDragStarted = ::startDrag,
                            onDragBy = ::dragBy,
                            onDragStopped = ::dropDrag,
                        )
                    }
                }
            }
        }
        if (!closedOnly && closedAccountCount > 0 && accountSearchQuery.isBlank()) {
            item(key = "closed-accounts-entry") {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Text(
                        CLOSED_ACCOUNT_GROUP,
                        modifier = Modifier.padding(horizontal = TandemSpacing.small),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.label,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SurfaceCard {
                        ListRow(
                            if (closedAccountCount == 1) "1 closed account" else "$closedAccountCount closed accounts",
                            leading = RowLeading.Icon(Lucide.Archive),
                            onClick = { navigation.open(AppRoute.CLOSED_ACCOUNTS) },
                        )
                    }
                }
            }
        }
        if (isLiveBudget && !closedOnly) {
            item(key = "account-bottom-actions") {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton(
                        "Add account",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.fillMaxWidth(),
                        icon = Lucide.Plus,
                        enabled = !isRefreshing && !state.readOnlyMode,
                    ) {
                        newAccountName = ""
                        newAccountOffBudget = false
                        newAccountGroupId = ""
                        mobileSession.feedback.clearBudgetActionMessage()
                        addAccountDialog = true
                    }
                    TandemActionButton(
                        "Manage bank connections",
                        TandemActionButtonTone.NEUTRAL,
                        Modifier.fillMaxWidth(),
                        icon = Lucide.Landmark,
                        enabled = !isRefreshing,
                    ) { navigation.open(AppRoute.BANK_CONNECTIONS) }
                }
            }
        }
    }
    TandemTaskSheet(
        visible = addAccountDialog,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) addAccountDialog = false },
        onDismissed = {},
    ) {
        SurfaceCard(
            Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.accentSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Lucide.WalletCards,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = tandemPalette.accent,
                        )
                    }
                    Text(
                        "Add account",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.sectionTitle,
                        fontFamily = tandemAlloFamily(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    TandemIconButton(
                        Lucide.X,
                        "Close",
                        sizeDp = 40,
                        iconSizeDp = 18,
                        containerColor = tandemPalette.raised,
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { addAccountDialog = false }
                }
                TextFieldCard("Name", newAccountName, { newAccountName = it }, TextFieldOptions(placeholder = "Checking"))
                Box {
                    ListRow(
                        "Account group",
                        trailing = accountGroupOptions.firstOrNull { it.first == newAccountGroupId }?.second ?: "No group",
                        leading = RowLeading.Icon(Lucide.FolderKey),
                        onClick = { accountGroupMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = accountGroupMenuExpanded,
                        onDismissRequest = { accountGroupMenuExpanded = false },
                    ) {
                        DropdownMenuItem(onClick = {
                            newAccountGroupId = ""
                            accountGroupMenuExpanded = false
                        }) {
                            Text("No group", color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                        }
                        accountGroupOptions.forEach { (groupId, groupName) ->
                            DropdownMenuItem(onClick = {
                                newAccountGroupId = groupId
                                accountGroupMenuExpanded = false
                            }) {
                                Text(groupName, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                            }
                        }
                    }
                }
                SurfaceCard {
                    CheckBoxRow(
                        "Off budget",
                        "Excluded from the budget",
                        checked = newAccountOffBudget,
                        enabled = !mobileSession.isPerformingBudgetAction,
                        onChange = { newAccountOffBudget = it },
                    )
                }
                budgetActionFeedbackMessage(mobileSession)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        Modifier.weight(1f),
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { addAccountDialog = false }
                    TandemActionButton(
                        if (mobileSession.isPerformingBudgetAction) "Adding…" else "Add account",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Plus,
                        enabled = !mobileSession.isPerformingBudgetAction && newAccountName.isNotBlank(),
                        loading = mobileSession.isPerformingBudgetAction,
                    ) {
                        scope.launch {
                            val created =
                                mobileSession.budgetEdits.createBudgetAccount(
                                    state.selectedBudgetId,
                                    newAccountName,
                                    newAccountOffBudget,
                                    newAccountGroupId.ifBlank { null },
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (created) addAccountDialog = false
                        }
                    }
                }
            }
        }
    }
}

private fun accountMatchesFilter(
    account: AccountListItemUi,
    groupName: String,
    filter: String,
    query: String,
): Boolean {
    val matchesType =
        when (filter) {
            "Bank" -> account.typeLabel in setOf("Checking", "Savings")
            "Credit" -> account.typeLabel == "Credit"
            "Other" -> account.typeLabel !in setOf("Checking", "Savings", "Credit")
            else -> true
        }
    val matchesSearch =
        query.isBlank() ||
            account.name.contains(query, ignoreCase = true) ||
            account.typeLabel.contains(query, ignoreCase = true) ||
            account.status.contains(query, ignoreCase = true) ||
            groupName.contains(query, ignoreCase = true)
    return matchesType && matchesSearch
}

@Composable
private fun AccountSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val palette = tandemPalette
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = palette.text, fontSize = TandemTypography.body),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text("Search accounts", color = palette.muted, fontSize = TandemTypography.body)
                        }
                        innerTextField()
                    }
                },
            )
            TandemIconButton(
                Lucide.X,
                "Clear account search",
                sizeDp = 36,
                iconSizeDp = 16,
                enabled = value.isNotEmpty(),
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun AccountFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val shape = CircleShape
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) palette.accent else palette.surface)
            .border(1.dp, if (selected) palette.accent else palette.border, shape)
            .clickable(role = Role.Button, onClickLabel = "$label accounts", onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) palette.accentContent else palette.text,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.Medium,
        )
    }
}

private const val CLOSED_ACCOUNT_GROUP = "Closed"
