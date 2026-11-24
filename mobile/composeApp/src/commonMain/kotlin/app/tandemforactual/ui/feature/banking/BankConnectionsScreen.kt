@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.ui.core.format.bankFailureLabel
import app.tandemforactual.ui.core.format.keepTogether
import app.tandemforactual.ui.core.format.platformFormatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.ui.bankLinkBadge
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.IntroCard
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Unlink
import com.composables.icons.lucide.WalletCards

/** Links budget accounts to the bank providers set up on the Actual server, grouped by where each provider works. */
@Composable
internal fun BankConnectionsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
) {
    val scope = rememberCoroutineScope()
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    val linked = mobileSession.banking.bankLinks
    val ui = remember { BankConnectionsState() }
    LaunchedEffect(state.selectedBudgetId, ui.reload) {
        runCatching { mobileSession.banking.loadLinkedAccounts(state.selectedBudgetId, password) }
    }
    val actions = BankConnectionsActions(ui, scope, state, mobileSession, password)
    val palette = tandemPalette
    PageColumn(verticalSpacing = TandemSpacing.large) {
        IntroCard(
            "Your Actual server keeps the bank credentials. Link accounts here, then sync them from the device.",
        )
        LinkedAccountsSection(state, mobileSession, linked, onManage = { ui.managing = it }, onSync = actions::syncLinkedAccounts)
        ui.relinkFor?.let { target ->
            InlineMessage(
                "Choose the bank account ${target.name} should sync from.",
                InlineMessageKind.INFO,
                onDismiss = { ui.relinkFor = null },
            )
        }
        BankProviderSections(
            ui.providerStates,
            linked,
            liveAccounts,
            relinking = ui.relinkFor != null,
            onLoad = actions::loadAccounts,
            onLink = actions::pickProviderAccount,
        )
    }
    ManageBankLinkDialog(ui.managing, onDismiss = { ui.managing = null }, onRelink = actions::relink, onUnlink = actions::unlink)
    BankBrowserLinkSheet(
        ui.browserProvider,
        mobileSession,
        onDismiss = {
            ui.browserProvider = null
            ui.relinkFor = null
        },
    ) { provider, providerAccount ->
        ui.browserProvider = null
        actions.pickProviderAccount(provider, providerAccount)
    }
    LinkBankAccountDialog(
        ui.linking,
        liveAccounts,
        linked,
        onDismiss = { ui.linking = null },
        onPick = actions::linkInto,
        onCreate = actions::linkIntoNew,
    )
}

/** The budget accounts already linked to a bank, with a button to sync them. */
@Composable
private fun LinkedAccountsSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    linked: List<LinkedAccount>,
    onManage: (LinkedAccount) -> Unit,
    onSync: () -> Unit,
) {
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
                            else -> "Synced ${keepTogether(platformFormatDateAtEpochMilliseconds(last))}"
                        }
                    },
                ).distinct().joinToString(" · "),
                leading = RowLeading.Icon(Lucide.WalletCards),
                options = ListRowOptions(badge = bankLinkBadge(account, now)),
                onClick = { onManage(account) },
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
            ) { onSync() }
        }
    }
}

/** Each bank provider, grouped under the regions it serves. */
@Composable
private fun BankProviderSections(
    providerStates: Map<String, BankProviderState>,
    linked: List<LinkedAccount>,
    liveAccounts: List<LiveAccountSummary>,
    relinking: Boolean,
    onLoad: (BankProvider) -> Unit,
    onLink: (BankProvider, BankProviderAccount) -> Unit,
) {
    val palette = tandemPalette
    BankProviders.all.groupBy { it.regions.joinToString(" & ") { region -> region.displayName } }.forEach { (regionLabel, providers) ->
        Text(regionLabel.uppercase(), color = palette.muted, fontSize = TandemTypography.caption)
        providers.forEach { provider ->
            ProviderCard(
                provider,
                providerStates[provider.source] ?: BankProviderState.Idle,
                linked,
                liveAccounts,
                { onLoad(provider) },
                relinking = relinking,
            ) { providerAccount -> onLink(provider, providerAccount) }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: BankProvider,
    providerState: BankProviderState,
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
            browser -> "Sign in at your bank in the browser, then choose which accounts to link."
            providerState is BankProviderState.NotSetUp -> "Not set up on your Actual server yet. Add it in Actual’s settings."
            providerState is BankProviderState.Failed -> providerState.message
            else -> "Accounts your Actual server can see"
        },
        when {
            browser -> if (relinking) "Sign in again" else "Link a bank"
            providerState is BankProviderState.Ready -> null
            providerState is BankProviderState.Loading -> "Loading…"
            providerState is BankProviderState.Idle -> "Show accounts"
            else -> "Check again"
        },
        onClick =
            if (!browser &&
                (providerState is BankProviderState.Ready || providerState is BankProviderState.Loading)
            ) {
                null
            } else {
                onLoad
            },
    ) {
        if (providerState is BankProviderState.Ready) {
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

/** Options for one linked account: relink it to another bank account or unlink it. */
@Composable
private fun ManageBankLinkDialog(
    managed: LinkedAccount?,
    onDismiss: () -> Unit,
    onRelink: (LinkedAccount) -> Unit,
    onUnlink: (LinkedAccount) -> Unit,
) {
    val palette = tandemPalette
    TandemChoiceDialog(visible = managed != null, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemModalHeader(managed?.name.orEmpty(), onClose = onDismiss, leadingIcon = Lucide.Landmark)
                managed?.failure?.let {
                    val browser = BankProviders.forSource(managed?.source) is BrowserBankProvider
                    Text(
                        "Last sync failed because ${bankFailureLabel(it)}. " +
                            if (browser) {
                                "Sign in at the bank again to reconnect it."
                            } else {
                                "Reconnect it in Actual’s web app, or link a different account."
                            },
                        color = palette.negative,
                        fontSize = TandemTypography.compactBody,
                    )
                }
                ListRow(
                    if (managed != null && BankProviders.forSource(managed.source) is BrowserBankProvider) {
                        "Sign in at the bank again"
                    } else {
                        "Link to a different bank account"
                    },
                    "Keeps this budget account and its history",
                    leading = RowLeading.Icon(Lucide.Link),
                    onClick = {
                        val account = managed ?: return@ListRow
                        onRelink(account)
                    },
                )
                ListRow(
                    "Unlink",
                    "Stops syncing. Transactions already imported stay.",
                    leading = RowLeading.Icon(Lucide.Unlink),
                    options = ListRowOptions(trailingColor = palette.negative),
                    onClick = {
                        val account = managed ?: return@ListRow
                        onUnlink(account)
                    },
                )
            }
        }
    }
}
