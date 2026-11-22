@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.session.reorderAccessibilityAction
import app.tandemforactual.ui.core.session.reorderModeConsumesItemTap
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.liveAccountGroupLabel
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.ReorderDragHandle
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowBadge
import app.tandemforactual.ui.design.rows.RowBadgeMark
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.CheckBoxRow
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
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
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal data class AccountListItemUi(
    val id: String,
    val name: String,
    val typeLabel: String,
    val status: String,
    val balance: String,
    val offBudget: Boolean,
    val closed: Boolean = false,
    val accountType: String? = null,
    val balanceMinor: Long? = null,
)

internal data class AccountListGroupUi(
    val name: String,
    val total: String,
    val accounts: List<AccountListItemUi>,
)

internal data class AccountListUi(
    val groups: List<AccountListGroupUi>,
)

internal fun accountListSubtitle(
    account: LiveAccountSummary,
    lastSyncedAtEpochMs: Long?,
): String =
    when {
        account.closed -> "Closed"
        else ->
            buildList {
                if (account.offBudget) add("Off budget")
                val syncState =
                    lastSyncedAtEpochMs?.let { "Last synced ${formatDateAtEpochMilliseconds(it)}" }
                        ?: "Not synced yet"
                add(syncState)
            }.joinToString(" · ")
    }

internal fun accountListTypeLabel(accountType: String?): String =
    when (accountType?.trim()?.lowercase()) {
        "checking", "chequing" -> "Checking"
        "savings", "saving" -> "Savings"
        "credit", "credit-card", "credit card" -> "Credit"
        "loan" -> "Loan"
        "mortgage" -> "Mortgage"
        "debt" -> "Debt"
        "investment" -> "Investment"
        "cash" -> "Cash"
        "other" -> "Other"
        else -> "Other"
    }

internal fun accountBalanceAccessibilityDescription(
    balanceMinor: Long?,
    formattedBalance: String,
): String =
    when {
        balanceMinor == null -> "Balance unavailable"
        balanceMinor < 0L -> "Negative balance, $formattedBalance"
        else -> "Balance, $formattedBalance"
    }

internal fun accountTypeIcon(
    accountName: String,
    accountType: String? = null,
): ImageVector {
    val name = accountName.lowercase()
    val type = accountType.orEmpty().lowercase()
    return when {
        "savings" in name || "saving" in name -> Lucide.PiggyBank
        "checking" in name || "chequing" in name -> Lucide.Landmark
        listOf("credit", "card", "visa", "mastercard", "amex", "discover").any { it in name } -> Lucide.CreditCard
        listOf("loan", "mortgage", "debt").any { it in name } -> Lucide.HandCoins
        listOf("investment", "brokerage", "401k", "403b", "ira", "retirement", "stocks").any { it in name } ->
            Lucide.ChartNoAxesCombined
        "checking" in type || "chequing" in type -> Lucide.Landmark
        "savings" in type || "saving" in type -> Lucide.PiggyBank
        listOf("credit", "card").any { it in type } -> Lucide.CreditCard
        listOf("loan", "debt").any { it in type } -> Lucide.HandCoins
        listOf("tracking", "investment").any { it in type } -> Lucide.ChartNoAxesCombined
        else -> Lucide.WalletCards
    }
}

internal fun accountGroupOrder(groupName: String): Int {
    val name = groupName.trim().lowercase()
    val type = name.substringAfter("·", name).trim()
    val typeOrder =
        when (type) {
            "cash" -> 0
            "checking" -> 1
            "savings" -> 2
            "credit" -> 3
            "loan" -> 4
            "mortgage" -> 5
            "debt" -> 6
            "investment" -> 7
            "other" -> 8
            else -> 9
        }
    return when {
        name == "closed" -> Int.MAX_VALUE
        else -> typeOrder
    }
}

internal fun sortAccountsForList(accounts: List<LiveAccountSummary>): List<LiveAccountSummary> =
    accounts.sortedWith(
        compareBy(
            { accountGroupOrder(liveAccountGroupLabel(it)) },
            { liveAccountGroupLabel(it).lowercase() },
            { it.sortOrder ?: Double.MAX_VALUE },
            { it.name.lowercase() },
        ),
    )

internal fun orderAccountsForList(
    accounts: List<LiveAccountSummary>,
    orderedIds: List<String>,
): List<LiveAccountSummary> {
    val positions = orderedIds.withIndex().associate { it.value to it.index }
    return sortAccountsForList(accounts).sortedWith(
        compareBy(
            { accountGroupOrder(liveAccountGroupLabel(it)) },
            { liveAccountGroupLabel(it).lowercase() },
            { positions[it.id] ?: Int.MAX_VALUE },
            { it.name.lowercase() },
        ),
    )
}

internal fun reorderAccountsWithinSlots(
    accountIds: List<String>,
    movableIds: List<String>,
    reorderedMovableIds: List<String>,
): List<String> {
    val movableSet = movableIds.toSet()
    val movablePositions = accountIds.indices.filter { accountIds[it] in movableSet }
    if (movablePositions.size != reorderedMovableIds.size) return accountIds
    val reordered = accountIds.toMutableList()
    movablePositions.zip(reorderedMovableIds).forEach { (position, accountId) -> reordered[position] = accountId }
    return reordered
}

@Composable
internal fun AccountSummaryCard(
    account: AccountListItemUi,
    merchantArtwork: DrawableResource?,
    privacyModeEnabled: Boolean = false,
    fractionDigits: Int = 2,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    reorderEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDragStarted: (() -> Unit)? = null,
    onDragBy: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
    highlighted: Boolean = false,
    bankBadge: RowBadge? = null,
) {
    val palette = tandemPalette
    val stackBalance = LocalDensity.current.fontScale >= 1.5f
    val balanceColor =
        when {
            account.closed || account.balanceMinor == null || account.balanceMinor == 0L -> palette.muted
            account.balanceMinor > 0 -> palette.positive
            else -> palette.negative
        }
    SurfaceCard(modifier = modifier, highlighted = highlighted) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = if (isReordering) "Finish reordering" else "Open ${account.name}",
                        onClick = onClick,
                    ).then(
                        if (onLongClick != null) {
                            Modifier.reorderAccessibilityAction(account.name, onLongClick)
                        } else {
                            Modifier
                        },
                    ).defaultMinSize(minHeight = TandemLayout.listRowMinHeight)
                    .padding(
                        horizontal = TandemLayout.listRowHorizontalInset,
                        vertical = TandemLayout.listRowVerticalInset,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
        ) {
            Box {
                Box(
                    Modifier
                        .size(TandemLayout.listRowLeadingSize)
                        .clip(RoundedCornerShape(TandemShapes.medium))
                        .background(if (account.closed) palette.surface else palette.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (merchantArtwork != null) {
                        Image(
                            painterResource(merchantArtwork),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(TandemLayout.listRowLogoSize)
                                    .padding(TandemSpacing.xxSmall)
                                    .alpha(if (account.closed) 0.48f else 1f),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            accountTypeIcon(account.name, account.accountType),
                            contentDescription = null,
                            modifier = Modifier.size(TandemLayout.listRowIconSize),
                            tint = if (account.closed) palette.muted else palette.accent,
                        )
                    }
                }
                bankBadge?.let { RowBadgeMark(it) }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                Text(
                    account.name,
                    color = if (account.closed) palette.muted else palette.text,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${account.typeLabel} · ${account.status}",
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stackBalance && !isReordering) {
                    AccountBalanceText(
                        account = account,
                        color = balanceColor,
                        privacyModeEnabled = privacyModeEnabled,
                        fractionDigits = fractionDigits,
                        modifier = Modifier.fillMaxWidth(),
                        wrap = true,
                    )
                }
            }
            if (isReordering) {
                onEdit?.let { edit ->
                    TandemIconButton(
                        Lucide.Pencil,
                        "Edit ${account.name}",
                        sizeDp = 40,
                        iconSizeDp = 18,
                        enabled = reorderEnabled,
                        onClick = edit,
                    )
                }
                ReorderDragHandle(
                    itemName = account.name,
                    enabled = reorderEnabled && (canMoveUp || canMoveDown),
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDragStarted = onDragStarted,
                    onDragBy = onDragBy,
                    onDragStopped = onDragStopped,
                )
            } else if (!stackBalance) {
                AccountBalanceText(account, balanceColor, privacyModeEnabled, fractionDigits)
            }
        }
    }
}

@Composable
private fun AccountBalanceText(
    account: AccountListItemUi,
    color: Color,
    privacyModeEnabled: Boolean,
    fractionDigits: Int = 2,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
) {
    PrivacyAmountText(
        account.balance,
        privacyModeEnabled = privacyModeEnabled,
        fractionDigits = fractionDigits,
        modifier =
            if (privacyModeEnabled) {
                modifier
            } else {
                modifier.semantics {
                    contentDescription = accountBalanceAccessibilityDescription(account.balanceMinor, account.balance)
                }
            },
        color = if (account.closed) tandemPalette.muted else color,
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        maxLines = if (wrap) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
    )
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
            runCatching { mobileSession.loadLinkedAccounts(state.selectedBudgetId, state.encryptedPassword.takeIf { it.isNotBlank() }) }
        }
    }
    LaunchedEffect(state.accountsRequestedAction) {
        when (state.accountsRequestedAction) {
            "add" -> {
                newAccountName = ""
                newAccountOffBudget = false
                newAccountGroupId = ""
                mobileSession.clearBudgetActionMessage()
                addAccountDialog = true
            }
            "banks" -> navigation.open(AppRoute.BANK_CONNECTIONS)
        }
        state.accountsRequestedAction = null
    }
    val isRefreshing = mobileSession.isSyncing || mobileSession.isPerformingBudgetAction
    val serverAccountOrder = remember(liveAccounts) { sortAccountsForList(liveAccounts).map { it.id } }
    val accountOrderStatus = mobileSession.budgetReorderStatus(state.selectedBudgetId, "accounts")
    val queuedAccountMoves =
        mobileSession
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
                    mobileSession.budgetReorderStatus(state.selectedBudgetId, "accounts"),
                    mobileSession
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
            mobileSession.clearBudgetReorderMessage(state.selectedBudgetId, "accounts")
            mobileSession.moveBudgetAccount(
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
                mobileSession.runBankSync(
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
            mobileSession.accountCurrencies,
            mobileSession.exchangeRates,
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
                                                mobileSession.accountCurrencies[account.id],
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
                    mobileSession.accountCurrencies[it.id]?.let { code -> code != state.budgetCurrencyCode } == true
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
                            mobileSession.clearBudgetReorderMessage(state.selectedBudgetId, "accounts")
                            mobileSession.clearBudgetActionMessage()
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
                                            mobileSession.moveBudgetAccount(
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
                                    mobileSession.bankLinks.firstOrNull { it.id == account.id },
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
                        mobileSession.clearBudgetActionMessage()
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
                                mobileSession.createBudgetAccount(
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

private const val CLOSED_ACCOUNT_GROUP = "Closed"
