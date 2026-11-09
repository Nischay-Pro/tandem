@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemChoiceDialog
import app.tandemforactual.ui.components.TandemIconButton
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

internal data class AccountListItemUi(
    val id: String,
    val name: String,
    val description: String,
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
    val isSample: Boolean,
    val showAccountOptions: Boolean,
)

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
    val type = name.substringAfter("·", "").trim()
    val typeOrder =
        when (type) {
            "checking" -> 0
            "savings" -> 1
            "credit" -> 2
            "loan" -> 3
            "mortgage" -> 4
            "debt" -> 5
            "investment" -> 6
            "other" -> 7
            else -> 8
        }
    return when {
        name == "closed" -> 2_000
        name.startsWith("on budget") -> typeOrder
        name.startsWith("off budget") -> 1_000 + typeOrder
        else -> 3_000
    }
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
        remember(isLiveBudget, liveAccounts, state.budgetDisplaySettings) {
            if (isLiveBudget) {
                val groupedAccounts =
                    liveAccounts
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
                                accounts.sortedWith(compareBy({ it.sortOrder ?: Long.MAX_VALUE }, { it.name.lowercase() })).map { account ->
                                    AccountListItemUi(
                                        account.id,
                                        account.name,
                                        when {
                                            account.closed -> "Closed"
                                            account.accountGroupName != null -> account.accountGroupName
                                            account.offBudget -> "Off budget"
                                            else -> "On budget"
                                        },
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
                    isSample = false,
                    showAccountOptions = false,
                )
            } else {
                val groupedAccounts =
                    previewAccounts.groupBy { it.group }.map { (groupName, accounts) ->
                        AccountListGroupUi(
                            groupName,
                            state.formatCurrency(accounts.sumOf { it.balanceMinor }),
                            accounts.map { account ->
                                AccountListItemUi(
                                    account.id,
                                    account.name,
                                    account.description,
                                    state.formatCurrency(account.balanceMinor),
                                    account.offBudget,
                                    accountType = account.group,
                                    balanceMinor = account.balanceMinor,
                                )
                            },
                        )
                    }
                AccountListUi(
                    groupedAccounts,
                    isSample = true,
                    showAccountOptions = true,
                )
            }
        }

    PageLazyColumn(
        isRefreshing = isRefreshing,
        onRefresh = runBankSync.takeIf { isLiveBudget && !state.readOnlyMode },
    ) {
        if (model.isSample) item(key = "accounts-preview-banner") { SampleBanner() }
        mobileSession.budgetActionMessage?.let { message ->
            item(key = "account-action-message") { InlineMessage(message, InlineMessageKind.ERROR) }
        }
        if (model.groups.isEmpty()) {
            item(key = "accounts-empty") {
                SurfaceCard {
                    EmptyState(
                        "No accounts yet",
                        if (model.isSample) null else "Sync this budget to load its account list and balances.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
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
                        SurfaceCard {
                            Column {
                                group.accounts.forEachIndexed { index, account ->
                                    ListRow(
                                        account.name,
                                        trailing = account.balance,
                                        leading =
                                            RowLeading.Merchant(
                                                merchantIconMatcher?.accountIconFor(account.name),
                                                fallback = accountTypeIcon(account.name, account.accountType),
                                            ),
                                        options =
                                            ListRowOptions(
                                                dense = true,
                                                trailingColor =
                                                    when {
                                                        account.closed || account.balanceMinor == null || account.balanceMinor == 0L ->
                                                            tandemPalette.muted
                                                        account.balanceMinor > 0 -> tandemPalette.positive
                                                        else -> tandemPalette.negative
                                                    },
                                            ),
                                        onClick = {
                                            state.selectedAccountId = account.id
                                            navigation.open(AppRoute.ACCOUNT_DETAIL)
                                        },
                                    )
                                    if (index < group.accounts.lastIndex) DividerLine()
                                }
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
        if (model.showAccountOptions) {
            item(key = "account-options") {
                SecondaryButton("Account options") { navigation.open(AppRoute.ACCOUNT_OPTIONS) }
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
                mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Icon(Lucide.Landmark, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                    Text(
                        "Bank connections",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.sectionTitle,
                        fontWeight = FontWeight.Bold,
                    )
                    TandemIconButton(Lucide.X, "Close", sizeDp = 40, iconSizeDp = 18) { showBankConnectionsDialog = false }
                }
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
