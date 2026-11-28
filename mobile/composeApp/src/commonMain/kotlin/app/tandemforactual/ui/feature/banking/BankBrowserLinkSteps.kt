@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.regions.global.bank.BankInstitution
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.data.regions.global.bank.BrowserLink
import app.tandemforactual.data.regions.global.bank.BrowserLinkStatus
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Search

@Composable
private fun StepColumn(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(TandemSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        content = content,
    )
}

@Composable
internal fun CountryStep(
    provider: BrowserBankProvider,
    onPick: (String) -> Unit,
) {
    StepColumn {
        item {
            Text(
                "Where is your bank? You’ll sign in at the bank itself, and your Actual server keeps the connection.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
        item {
            SurfaceCard {
                provider.countries.sortedBy(::countryName).forEachIndexed { index, code ->
                    if (index > 0) DividerLine()
                    ListRow(
                        countryName(code),
                        leading = RowLeading.Icon(Lucide.MapPin),
                        options = ListRowOptions(dense = true),
                        onClick = { onPick(code) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun BanksStep(
    provider: BrowserBankProvider,
    country: String,
    mobileSession: TandemMobileSession,
    onBack: () -> Unit,
    onPick: (BankInstitution) -> Unit,
) {
    var banks by remember(country) { mutableStateOf<List<BankInstitution>?>(null) }
    var failure by remember(country) { mutableStateOf<String?>(null) }
    var query by remember(country) { mutableStateOf("") }
    LaunchedEffect(provider, country) {
        try {
            banks = mobileSession.banking.loadBankInstitutions(provider, country)
        } catch (error: TandemApiException) {
            failure = bankLinkFailureMessage(provider, error.errorCode)
        }
    }
    val list = banks
    when {
        failure != null -> {
            EmptyStateCard(
                "Couldn’t load banks",
                failure,
                Modifier.fillMaxWidth().padding(TandemSpacing.screen),
                "Choose another country",
                onBack,
            )
        }

        list == null -> {
            CenteredProgress("Loading banks in ${countryName(country)}…")
        }

        list.isEmpty() -> {
            EmptyStateCard(
                "No banks found",
                "${provider.displayName} didn’t list any banks in ${countryName(country)}.",
                Modifier.fillMaxWidth().padding(TandemSpacing.screen),
                "Choose another country",
                onBack,
            )
        }

        else -> {
            val shown = list.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            StepColumn { bankListItems(shown, query, { query = it.take(60) }, onPick) }
        }
    }
}

/** Search field, matching banks and the overflow note of the bank step. */
private fun LazyListScope.bankListItems(
    shown: List<BankInstitution>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (BankInstitution) -> Unit,
) {
    item {
        TextFieldCard(
            "Find your bank",
            query,
            onQueryChange,
            TextFieldOptions(placeholder = "Bank name", leadingIcon = Lucide.Search),
        )
    }
    item {
        SurfaceCard {
            if (shown.isEmpty()) {
                ListRow(
                    "No banks match “${query.trim()}”",
                    leading = RowLeading.None,
                    options = ListRowOptions(showChevron = false),
                )
            }
            shown.take(MAX_BANKS_SHOWN).forEachIndexed { index, bank ->
                if (index > 0) DividerLine()
                ListRow(
                    bank.name,
                    leading = RowLeading.Letter(bank.name.take(1)),
                    options = ListRowOptions(dense = true),
                    onClick = { onPick(bank) },
                )
            }
        }
    }
    if (shown.size > MAX_BANKS_SHOWN) {
        item {
            Text(
                "Showing ${MAX_BANKS_SHOWN} of ${shown.size}. Type more of the name to narrow it down.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.caption,
            )
        }
    }
}

@Composable
internal fun WaitingStep(
    provider: BrowserBankProvider,
    step: LinkStep.Waiting,
    mobileSession: TandemMobileSession,
    onStarted: (BrowserLink) -> Unit,
    onLinked: (List<BankProviderAccount>) -> Unit,
    onFailed: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(step.institution) {
        if (step.link != null) return@LaunchedEffect
        val link =
            try {
                mobileSession.banking.startBankLink(provider, step.institution)
            } catch (error: TandemApiException) {
                onFailed(bankLinkFailureMessage(provider, error.errorCode))
                return@LaunchedEffect
            }
        onStarted(link)
        runCatching { uriHandler.openUri(link.url) }
    }
    LaunchedEffect(step.link?.handle) {
        val link = step.link ?: return@LaunchedEffect
        when (val status = mobileSession.banking.awaitBankLink(provider, link.handle)) {
            is BrowserLinkStatus.Linked -> {
                onLinked(status.accounts)
            }

            is BrowserLinkStatus.Failed -> {
                onFailed(
                    if (status.reason == "timeout") {
                        "The bank sign-in took too long. Try again."
                    } else {
                        "${step.institution.name} didn’t finish linking. Try again."
                    },
                )
            }

            BrowserLinkStatus.Pending -> {
                Unit
            }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(TandemSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.large, Alignment.CenterVertically),
    ) {
        TandemLoadingIndicator(Modifier.size(40.dp), tandemPalette.accent, 3.dp)
        Text(
            if (step.link == null) "Opening ${step.institution.name}…" else "Finish signing in at ${step.institution.name}",
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "When the bank says you’re done, come back here. Tandem picks up the accounts it shares.",
            color = tandemPalette.muted,
            fontSize = TandemTypography.compactBody,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        step.link?.let { link ->
            TandemActionButton(
                "Open the bank page again",
                TandemActionButtonTone.NEUTRAL,
                Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                icon = Lucide.ExternalLink,
            ) { runCatching { uriHandler.openUri(link.url) } }
        }
    }
}

@Composable
internal fun AccountsStep(
    step: LinkStep.Accounts,
    onPick: (BankProviderAccount) -> Unit,
) {
    StepColumn {
        item {
            Text(
                if (step.accounts.isEmpty()) {
                    "${step.institution.name} didn’t share any accounts. Try again and choose at least one account at the bank."
                } else {
                    "${step.institution.name} is connected. Choose an account to link to your budget."
                },
                color = tandemPalette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
        if (step.accounts.isNotEmpty()) {
            item {
                SurfaceCard {
                    step.accounts.forEachIndexed { index, account ->
                        if (index > 0) DividerLine()
                        ListRow(
                            account.name,
                            listOfNotNull(account.institution, account.balance).joinToString(" · ").ifBlank { null },
                            "Link",
                            leading = RowLeading.Account(account.institution ?: account.name),
                            onClick = { onPick(account) },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(TandemSpacing.large)) }
    }
}

@Composable
private fun CenteredProgress(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            TandemLoadingIndicator(Modifier.size(32.dp), tandemPalette.accent, 3.dp)
            Text(label, color = tandemPalette.muted, fontSize = TandemTypography.compactBody)
        }
    }
}

private const val MAX_BANKS_SHOWN = 60

private fun bankLinkFailureMessage(
    provider: BrowserBankProvider,
    code: String,
): String =
    when {
        code.contains("not_configured") -> "${provider.displayName} isn’t set up on your Actual server yet. Add it in Actual’s settings."
        code == "address_unreachable" -> "Couldn’t reach your Actual server. Check your connection and try again."
        else -> "Couldn’t reach ${provider.displayName} through your Actual server. Try again."
    }
