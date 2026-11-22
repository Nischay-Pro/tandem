@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.global.bank.BankLinkMethod
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BankProviders
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowBadge
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemChoiceDialog
import app.tandemforactual.ui.components.TandemModalHeader
import app.tandemforactual.ui.model.LiveAccountSummary
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Unlink
import com.composables.icons.lucide.WalletCards
import kotlinx.coroutines.launch

private sealed interface ProviderState {
    data object Idle : ProviderState

    data object Loading : ProviderState

    data object NotSetUp : ProviderState

    data class Failed(
        val message: String,
    ) : ProviderState

    data class Ready(
        val accounts: List<BankProviderAccount>,
    ) : ProviderState
}

/** Links budget accounts to the bank providers set up on the Actual server, grouped by where each provider works. */
@Composable
internal fun BankConnectionsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
) {
    val scope = rememberCoroutineScope()
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    val linked = mobileSession.bankLinks
    var reload by remember { mutableIntStateOf(0) }
    val providerStates = remember { mutableStateMapOf<String, ProviderState>() }
    var linking by remember { mutableStateOf<Pair<BankProvider, BankProviderAccount>?>(null) }
    var managing by remember { mutableStateOf<LinkedAccount?>(null) }
    var relinkFor by remember { mutableStateOf<LinkedAccount?>(null) }
    LaunchedEffect(state.selectedBudgetId, reload) {
        runCatching { mobileSession.loadLinkedAccounts(state.selectedBudgetId, password) }
    }

    fun loadAccounts(provider: BankProvider) {
        providerStates[provider.source] = ProviderState.Loading
        scope.launch {
            providerStates[provider.source] =
                runCatching { mobileSession.loadBankProviderAccounts(provider) }.fold(
                    { accounts -> accounts?.let { ProviderState.Ready(it) } ?: ProviderState.NotSetUp },
                    { ProviderState.Failed("Couldn’t reach ${provider.displayName} through your Actual server.") },
                )
        }
    }
    val palette = tandemPalette
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Text(
            "Your Actual server keeps the bank credentials. Link accounts here, then sync them from the device.",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
        SectionCard("Linked accounts", if (linked.isEmpty()) "No accounts are linked yet" else "${linked.size} syncing with a bank") {
            linked.forEachIndexed { index, account ->
                if (index > 0) DividerLine()
                val provider = BankProviders.forSource(account.source)
                val now = mobileSession.nowEpochMillis()
                ListRow(
                    account.name,
                    listOfNotNull(
                        provider.displayName,
                        account.bankName,
                        account.lastSyncMillis.let { last ->
                            when {
                                account.failure != null -> "Last sync failed"
                                last == null -> "Not synced yet"
                                else -> "Synced ${platformFormatDateAtEpochMilliseconds(last)}"
                            }
                        },
                    ).distinct().joinToString(" · "),
                    leading = RowLeading.Icon(Lucide.WalletCards),
                    options = ListRowOptions(badge = bankLinkBadge(account, now)),
                    onClick = { managing = account },
                )
            }
            if (linked.isNotEmpty()) {
                TandemActionButton(
                    "Sync linked accounts",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth().padding(TandemSpacing.medium),
                    icon = Lucide.RefreshCw,
                    enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                    loading = mobileSession.isPerformingBudgetAction,
                ) { scope.launch { mobileSession.runBankSync(state.selectedBudgetId, password) } }
            }
        }
        relinkFor?.let { target ->
            InlineMessage(
                "Choose the bank account ${target.name} should sync from.",
                InlineMessageKind.INFO,
                onDismiss = { relinkFor = null },
            )
        }
        BankProviders.all.groupBy { it.regions.joinToString(" & ") { region -> region.displayName } }.forEach { (regionLabel, providers) ->
            Text(regionLabel.uppercase(), color = palette.muted, fontSize = TandemTypography.caption)
            providers.forEach { provider ->
                ProviderCard(
                    provider,
                    providerStates[provider.source] ?: ProviderState.Idle,
                    linked,
                    liveAccounts,
                    { loadAccounts(provider) },
                    relinking = relinkFor != null,
                ) { providerAccount ->
                    val target = relinkFor
                    if (target == null) {
                        linking = provider to providerAccount
                    } else {
                        relinkFor = null
                        scope.launch {
                            if (mobileSession.linkBankAccount(
                                    state.selectedBudgetId,
                                    target.id,
                                    provider,
                                    providerAccount,
                                    password,
                                )
                            ) {
                                reload++
                            }
                        }
                    }
                }
            }
        }
    }
    val managed = managing
    TandemChoiceDialog(visible = managed != null, onDismissRequest = { managing = null }) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemModalHeader(managed?.name.orEmpty(), onClose = { managing = null }, leadingIcon = Lucide.Landmark)
                managed?.failure?.let {
                    Text(
                        "The bank said: ${it.replace('_', ' ')}. Reconnect it in Actual’s web app, or link a different account.",
                        color = palette.negative,
                        fontSize = TandemTypography.compactBody,
                    )
                }
                ListRow(
                    "Link to a different bank account",
                    "Keeps this budget account and its history",
                    leading = RowLeading.Icon(Lucide.Link),
                    onClick = {
                        val account = managed ?: return@ListRow
                        managing = null
                        relinkFor = account
                        loadAccounts(BankProviders.forSource(account.source))
                    },
                )
                ListRow(
                    "Unlink",
                    "Stops syncing. Transactions already imported stay.",
                    leading = RowLeading.Icon(Lucide.Unlink),
                    options = ListRowOptions(trailingColor = palette.negative),
                    onClick = {
                        val account = managed ?: return@ListRow
                        managing = null
                        scope.launch {
                            if (mobileSession.unlinkBankAccount(state.selectedBudgetId, account.id, password)) reload++
                        }
                    },
                )
            }
        }
    }
    val target = linking
    TandemChoiceDialog(visible = target != null, onDismissRequest = { linking = null }) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemModalHeader("Link ${target?.second?.name.orEmpty()}", onClose = { linking = null }, leadingIcon = Lucide.Landmark)
                Text("Choose the budget account it should sync into.", color = palette.muted, fontSize = TandemTypography.compactBody)
                liveAccounts.filter { account -> !account.closed && linked.none { it.id == account.id } }.forEach { account ->
                    ListRow(
                        account.name,
                        if (account.offBudget) "Off budget" else "On budget",
                        leading =
                            RowLeading.Icon(
                                Lucide.WalletCards,
                            ),
                        onClick = {
                            val (provider, providerAccount) = target ?: return@ListRow
                            linking = null
                            scope.launch {
                                if (mobileSession.linkBankAccount(
                                        state.selectedBudgetId,
                                        account.id,
                                        provider,
                                        providerAccount,
                                        password,
                                    )
                                ) {
                                    reload++
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: BankProvider,
    providerState: ProviderState,
    linked: List<LinkedAccount>,
    liveAccounts: List<LiveAccountSummary>,
    onLoad: () -> Unit,
    relinking: Boolean = false,
    onLink: (BankProviderAccount) -> Unit,
) {
    val browser = provider.linkMethod == BankLinkMethod.BROWSER
    SectionCard(
        provider.displayName,
        when {
            browser -> "Sign in to your bank from Actual’s web app to link these, then sync here."
            providerState is ProviderState.NotSetUp -> "Not set up on your Actual server yet. Add it in Actual’s settings."
            providerState is ProviderState.Failed -> providerState.message
            else -> "Accounts your Actual server can see"
        },
        when {
            browser || providerState is ProviderState.Ready -> null
            providerState is ProviderState.Loading -> "Loading…"
            providerState is ProviderState.Idle -> "Show accounts"
            else -> "Check again"
        },
        onClick = if (browser || providerState is ProviderState.Ready || providerState is ProviderState.Loading) null else onLoad,
    ) {
        if (providerState is ProviderState.Ready) {
            if (providerState.accounts.isEmpty()) {
                ListRow("No accounts found", options = ListRowOptions(showChevron = false), leading = RowLeading.None)
            }
            providerState.accounts.forEachIndexed { index, account ->
                if (index > 0) DividerLine()
                val linkedTo =
                    linked.firstOrNull { it.providerAccountId == account.id }?.let { link ->
                        liveAccounts
                            .firstOrNull {
                                it.id ==
                                    link.id
                            }?.name
                            ?: link.name
                    }
                ListRow(
                    account.name,
                    listOfNotNull(account.institution, account.balance).joinToString(" · ").ifBlank { null },
                    linkedTo?.let { "Linked to $it" } ?: if (relinking) "Use this" else "Link",
                    leading = RowLeading.Icon(if (linkedTo != null) Lucide.Link else Lucide.Unlink),
                    options = ListRowOptions(showChevron = linkedTo == null),
                    onClick = if (linkedTo == null) ({ onLink(account) }) else null,
                )
            }
        }
    }
}

private const val STALE_AFTER_MILLIS = 3L * 24 * 60 * 60 * 1000

/** No mark when unlinked; green when synced lately, amber when it hasn’t synced in days, red when its last sync failed. */
@Composable
internal fun bankLinkBadge(
    link: LinkedAccount?,
    nowMillis: Long,
): RowBadge? {
    link ?: return null
    val palette = tandemPalette
    val last = link.lastSyncMillis
    return when {
        link.failure != null -> RowBadge(null, palette.negative, "Bank sync failed")
        last == null || nowMillis - last > STALE_AFTER_MILLIS ->
            RowBadge(Lucide.Clock, palette.warning, "Bank sync is behind")
        else -> RowBadge(Lucide.Link, palette.positive, "Linked to a bank")
    }
}
