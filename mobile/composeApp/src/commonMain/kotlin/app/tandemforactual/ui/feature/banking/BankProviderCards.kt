@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BankCredentialSource
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.bank.BankLinkMethod
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BankProviders
import app.tandemforactual.data.regions.us.bank.simplefin.SimpleFin
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Unlink

/**
 * Bank providers for the budget's country first, grouped by the regions they serve; providers elsewhere wait under
 * "Other countries" unless an account is already linked through them.
 */
@Composable
internal fun BankProviderSections(
    providerStates: Map<String, BankProviderState>,
    linked: List<LinkedAccount>,
    liveAccounts: List<LiveAccountSummary>,
    homeRegions: Set<Region>,
    relinking: Boolean,
    onLoad: (BankProvider) -> Unit,
    onLink: (BankProvider, BankProviderAccount) -> Unit,
    onSetUpSimpleFin: (BankCredentialSource?) -> Unit = {},
) {
    val (nearby, elsewhere) = splitBankProviders(BankProviders.all, homeRegions, linked.map { it.source }.toSet())
    var showOthers by remember { mutableStateOf(false) }
    val card: @Composable (BankProvider) -> Unit = { provider ->
        ProviderCard(
            provider,
            providerStates[provider.source] ?: BankProviderState.Idle,
            linked,
            liveAccounts,
            { onLoad(provider) },
            relinking = relinking,
            onSetUp = onSetUpSimpleFin.takeIf { provider == SimpleFin },
        ) { providerAccount -> onLink(provider, providerAccount) }
    }
    ProviderGroups(nearby, card)
    if (elsewhere.isEmpty()) return
    SurfaceCard {
        ListRow(
            "Other countries",
            elsewhere.flatMap { it.regions }.distinct().joinToString(", ") { it.displayName },
            if (showOthers) "Hide" else "Show",
            leading = RowLeading.Icon(Lucide.Globe),
            options = ListRowOptions(showChevron = false),
            onClick = { showOthers = !showOthers },
        )
    }
    AnimatedVisibility(showOthers, enter = tandemRevealEnter(tandemReduceMotion), exit = tandemRevealExit(tandemReduceMotion)) {
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) { ProviderGroups(elsewhere, card) }
    }
}

/** Providers under a heading for the regions they serve. */
@Composable
private fun ProviderGroups(
    providers: List<BankProvider>,
    card: @Composable (BankProvider) -> Unit,
) {
    providers.groupBy { it.regions.joinToString(" & ") { region -> region.displayName } }.forEach { (regionLabel, group) ->
        Text(regionLabel.uppercase(), color = tandemPalette.muted, fontSize = TandemTypography.caption)
        group.forEach { card(it) }
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
    onSetUp: ((BankCredentialSource?) -> Unit)? = null,
    onLink: (BankProviderAccount) -> Unit,
) {
    val browser = provider.linkMethod == BankLinkMethod.BROWSER
    val settingUp = onSetUp != null && providerState is BankProviderState.NotSetUp
    val onCardClick: (() -> Unit)? =
        when {
            settingUp -> {
                { onSetUp?.invoke((providerState as? BankProviderState.NotSetUp)?.source) }
            }
            !browser && (providerState is BankProviderState.Ready || providerState is BankProviderState.Loading) -> null
            else -> onLoad
        }
    SectionCard(
        provider.displayName,
        providerDetail(provider, providerState, setsUpHere = onSetUp != null),
        when {
            browser -> if (relinking) "Sign in again" else "Link a bank"
            settingUp -> "Set up"
            providerState is BankProviderState.Ready -> null
            providerState is BankProviderState.Loading -> "Loading…"
            providerState is BankProviderState.Idle -> "Show accounts"
            else -> "Check again"
        },
        onClick = onCardClick,
        actionContent =
            if (!browser && providerState is BankProviderState.Ready) {
                { TandemIconButton(Lucide.RefreshCw, "Refresh ${provider.displayName} accounts", onClick = onLoad) }
            } else {
                null
            },
    ) {
        if (onSetUp != null) CredentialRow(providerState, onSetUp)
        if (providerState is BankProviderState.Ready) ProviderAccountRows(providerState.accounts, linked, liveAccounts, relinking, onLink)
    }
}

/** What the card says under the provider's name, including whose credentials its accounts come from. */
internal fun providerDetail(
    provider: BankProvider,
    providerState: BankProviderState,
    setsUpHere: Boolean,
): String =
    when {
        provider.linkMethod == BankLinkMethod.BROWSER -> "Sign in at your bank in the browser, then choose which accounts to link."
        providerState is BankProviderState.NotSetUp && providerState.source == BankCredentialSource.BUDGET ->
            "This budget’s own ${provider.displayName} credentials aren’t working. Set them up again."
        providerState is BankProviderState.NotSetUp && setsUpHere -> "Not set up for this budget yet. Paste a setup token to start."
        providerState is BankProviderState.NotSetUp -> "Not set up on your Actual server yet. Add it in Actual’s settings."
        providerState is BankProviderState.Failed -> providerState.message
        providerState is BankProviderState.Ready && providerState.source == BankCredentialSource.BUDGET ->
            "Accounts from this budget’s own credentials"
        else -> "Accounts your Actual server can see"
    }

/** Whose SimpleFIN token the budget uses, with a way to change it. */
@Composable
private fun CredentialRow(
    providerState: BankProviderState,
    onSetUp: (BankCredentialSource?) -> Unit,
) {
    val source =
        when (providerState) {
            is BankProviderState.Ready -> providerState.source
            is BankProviderState.NotSetUp -> providerState.source ?: return
            is BankProviderState.Failed -> null
            else -> return
        }
    ListRow(
        "Setup token",
        when (source) {
            BankCredentialSource.BUDGET -> "This budget’s own"
            BankCredentialSource.SERVER -> "Your Actual server’s, shared by every budget"
            null -> "Paste a new one if SimpleFIN stopped working"
        },
        "Change",
        leading = RowLeading.Icon(Lucide.KeyRound),
        onClick = { onSetUp(source) },
    )
    if (providerState is BankProviderState.Ready) DividerLine()
}

@Composable
private fun ProviderAccountRows(
    accounts: List<BankProviderAccount>,
    linked: List<LinkedAccount>,
    liveAccounts: List<LiveAccountSummary>,
    relinking: Boolean,
    onLink: (BankProviderAccount) -> Unit,
) {
    if (accounts.isEmpty()) {
        ListRow("No accounts found", options = ListRowOptions(showChevron = false), leading = RowLeading.None)
    }
    accounts.forEachIndexed { index, account ->
        if (index > 0) DividerLine()
        val linkedTo =
            linked.firstOrNull { it.providerAccountId == account.id }?.let { link ->
                liveAccounts.firstOrNull { it.id == link.id }?.name ?: link.name
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
