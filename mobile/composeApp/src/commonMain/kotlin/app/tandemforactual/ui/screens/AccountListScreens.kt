@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemChoiceDialog
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemModalHeader
import app.tandemforactual.ui.components.TandemTaskSheet
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.liveAccountGroupLabel
import app.tandemforactual.ui.model.recordName
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
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
            { it.sortOrder ?: Long.MAX_VALUE },
            { it.name.lowercase() },
        ),
    )

@Composable
internal fun AccountSummaryCard(
    account: AccountListItemUi,
    merchantArtwork: DrawableResource?,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val stackBalance = LocalDensity.current.fontScale >= 1.5f
    val balanceColor =
        when {
            account.closed || account.balanceMinor == null || account.balanceMinor == 0L -> palette.muted
            account.balanceMinor > 0 -> palette.positive
            else -> palette.negative
        }
    SurfaceCard(onClick = onClick, onClickLabel = "Open ${account.name}") {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = TandemLayout.listRowMinHeight)
                    .padding(
                        horizontal = TandemLayout.listRowHorizontalInset,
                        vertical = TandemLayout.listRowVerticalInset,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
        ) {
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
                if (stackBalance) {
                    AccountBalanceText(
                        account = account,
                        color = balanceColor,
                        modifier = Modifier.fillMaxWidth(),
                        wrap = true,
                    )
                }
            }
            if (!stackBalance) AccountBalanceText(account, balanceColor)
        }
    }
}

@Composable
private fun AccountBalanceText(
    account: AccountListItemUi,
    color: Color,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
) {
    Text(
        account.balance,
        modifier =
            modifier.semantics {
                contentDescription =
                    accountBalanceAccessibilityDescription(
                        account.balanceMinor,
                        account.balance,
                    )
            },
        color = if (account.closed) tandemPalette.muted else color,
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        maxLines = if (wrap) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun AccountsScreen(
    state: AppState,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    merchantIconMatcher: MerchantIconMatcherPack?,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    var addAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showBankConnectionsDialog by rememberSaveable { mutableStateOf(false) }
    var newAccountName by rememberSaveable { mutableStateOf("") }
    var newAccountOffBudget by rememberSaveable { mutableStateOf(false) }
    var newAccountGroupId by rememberSaveable { mutableStateOf("") }
    var accountGroupMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val accountGroupOptions =
        remember(mobileSession.accountGroupRecords) {
            mobileSession.accountGroupRecords
                .mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                .sortedBy { it.second.lowercase() }
        }
    val isRefreshing = mobileSession.isSyncing || mobileSession.isPerformingBudgetAction
    val runBankSync: () -> Unit = {
        if (!isRefreshing && !state.readOnlyMode && state.selectedBudgetId.isNotBlank()) {
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
            liveAccounts,
            state.budgetDisplaySettings,
            mobileSession.localSyncState?.lastSyncedAtEpochMs,
        ) {
            if (isLiveBudget) {
                val groupedAccounts =
                    sortAccountsForList(liveAccounts)
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
                                state.formatCurrency(accounts.sumOf { it.balanceMinor ?: 0L }),
                                accounts.map { account ->
                                    AccountListItemUi(
                                        account.id,
                                        account.name,
                                        accountListTypeLabel(account.type),
                                        accountListSubtitle(account, mobileSession.localSyncState?.lastSyncedAtEpochMs),
                                        account.balanceMinor?.let(state::formatCurrency) ?: "Unavailable",
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

    PageLazyColumn(
        verticalSpacing = TandemSpacing.small,
        isRefreshing = isRefreshing,
        onRefresh = runBankSync.takeIf { isLiveBudget && !state.readOnlyMode },
    ) {
        mobileSession.budgetActionMessage?.let {
            item(key = "account-action-message") { budgetActionFeedbackMessage(mobileSession) }
        }
        if (model.groups.isEmpty()) {
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
        } else {
            model.groups.forEach { group ->
                item(key = "account-group:${group.name}") {
                    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
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
                            Text(group.total, color = tandemPalette.muted, fontSize = TandemTypography.caption, maxLines = 1)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                            group.accounts.forEach { account ->
                                AccountSummaryCard(
                                    account = account,
                                    merchantArtwork = merchantIconMatcher?.accountIconFor(account.name),
                                    onClick = {
                                        state.selectedAccountId = account.id
                                        navigation.open(AppRoute.ACCOUNT_DETAIL)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isLiveBudget) {
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
                    ) { showBankConnectionsDialog = true }
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
    TandemChoiceDialog(
        visible = showBankConnectionsDialog,
        onDismissRequest = { if (!isRefreshing) showBankConnectionsDialog = false },
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                TandemModalHeader(
                    title = "Bank connections",
                    leadingIcon = Lucide.Landmark,
                    enabled = !isRefreshing,
                    onClose = { if (!isRefreshing) showBankConnectionsDialog = false },
                )
                InfoBubble("Review, add, or remove bank links in Actual Budget. Tandem syncs the connections configured there.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Done", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f)) {
                        showBankConnectionsDialog = false
                    }
                    TandemActionButton(
                        "Sync accounts",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.RefreshCw,
                        enabled = !isRefreshing && !state.readOnlyMode,
                        loading = isRefreshing,
                    ) {
                        showBankConnectionsDialog = false
                        runBankSync()
                    }
                }
            }
        }
    }
}
