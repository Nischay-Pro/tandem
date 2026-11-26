@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.currency
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.ConvertedTotal
import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.data.planning.rateBetween
import app.tandemforactual.data.planning.rateHistory
import app.tandemforactual.ui.core.format.budgetCurrencyCode
import app.tandemforactual.ui.core.format.convertedBalance
import app.tandemforactual.ui.core.format.formatAccountMoney
import app.tandemforactual.ui.core.format.formatConverted
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.ui.accountTypeIcon
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
import app.tandemforactual.ui.design.rows.RowBadge
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

internal val COMMON_CURRENCIES =
    listOf(
        "USD",
        "EUR",
        "GBP",
        "CAD",
        "AUD",
        "NZD",
        "INR",
        "JPY",
        "CHF",
        "SEK",
        "NOK",
        "DKK",
        "PLN",
        "CZK",
        "HUF",
        "BRL",
        "MXN",
        "SGD",
        "HKD",
        "CNY",
        "KRW",
        "ZAR",
        "AED",
        "ILS",
        "TRY",
    )

/**
 * Accounts kept in other currencies and the rates to report them in the budget's currency. Actual has one currency per
 * budget, so this is a reporting layer only: Actual's balances are never rewritten.
 */
@Composable
internal fun ExchangeRatesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
    navigation: AppNavigation,
) {
    val scope = rememberCoroutineScope()
    val palette = tandemPalette
    val budgetCurrency = state.budgetCurrencyCode
    val open = liveAccounts.filter { !it.closed }
    val currencies = mobileSession.planning.accountCurrencies
    val foreign =
        open
            .mapNotNull { currencies[it.id] }
            .filter { it != budgetCurrency }
            .distinct()
            .sorted()
    var picking by remember { mutableStateOf<LiveAccountSummary?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        IntroCard(
            "Actual keeps each budget in one currency. Mark accounts that hold another currency and add a rate, and " +
                "Tandem shows their balances in their own currency and an approximate total in $budgetCurrency. " +
                "Actual’s balances never change.",
        )
        if (foreign.isNotEmpty()) {
            ForeignCurrencySections(state, mobileSession, open, foreign, budgetCurrency) { editing = it }
        }
        AccountCurrenciesSection(state, open, currencies, budgetCurrency) { picking = it }
    }
    AccountCurrencyDialog(picking, budgetCurrency, currencies, onDismiss = { picking = null }) { account, code ->
        picking = null
        scope.launch {
            mobileSession.planning.saveAccountCurrency(
                state.selectedBudgetId,
                account.id,
                if (code == budgetCurrency) "" else code,
            )
        }
        if (code != budgetCurrency &&
            rateBetween(code, budgetCurrency, mobileSession.planning.exchangeRates) == null
        ) {
            editing = code
        }
    }
    ExchangeRateDialog(editing, budgetCurrency, mobileSession.planning.exchangeRates, onDismiss = { editing = null }) { rate, delete ->
        editing = null
        scope.launch {
            mobileSession.planning.exchangeRates
                .filter { it.asOf == rate.asOf && it.id != rate.id.takeIf { !delete } }
                .filter { (it.from == rate.from && it.to == rate.to) || (it.from == rate.to && it.to == rate.from) }
                .forEach { mobileSession.planning.deleteExchangeRate(state.selectedBudgetId, it) }
            if (!delete) mobileSession.planning.saveExchangeRate(state.selectedBudgetId, rate)
        }
    }
}

/** Net worth across currencies and the rate for each foreign currency in use. */
@Composable
private fun ForeignCurrencySections(
    state: AppState,
    mobileSession: TandemMobileSession,
    open: List<LiveAccountSummary>,
    foreign: List<String>,
    budgetCurrency: String,
    onEditRate: (String) -> Unit,
) {
    val palette = tandemPalette
    val total = mobileSession.convertedBalance(state, open.map { it.id to it.balanceMinor })
    SectionCard("Net worth", netWorthDetail(total, budgetCurrency)) {
        ListRow(
            "All open accounts",
            "Converted values are estimates. Rates move.",
            state.formatConverted(total),
            leading = RowLeading.Icon(Lucide.Coins),
            options = ListRowOptions(showChevron = false),
        )
    }
    if (total.missingRates.isNotEmpty()) {
        InlineMessage(
            "Add a rate for ${total.missingRates.sorted().joinToString()} to include those accounts in the total.",
            InlineMessageKind.WARNING,
        )
    }
    SectionCard("Exchange rates", "Entered by you, with the date you checked them") {
        foreign.forEachIndexed { index, code ->
            if (index > 0) DividerLine()
            val rate = rateBetween(code, budgetCurrency, mobileSession.planning.exchangeRates)
            ListRow(
                "1 $code = ${rate?.let { "${formatRate(it.value!!)} $budgetCurrency" } ?: "?"}",
                rate?.let { "As of ${formatLiveDate(it.asOf)}" } ?: "No rate yet · Tap to add one",
                leading = RowLeading.Icon(Lucide.ArrowLeftRight),
                options =
                    ListRowOptions(
                        badge = if (rate == null) RowBadge(null, palette.warning, "Needs a rate") else null,
                    ),
                onClick = { onEditRate(code) },
            )
        }
    }
}

/** Every open account with the currency it is kept in. */
@Composable
private fun AccountCurrenciesSection(
    state: AppState,
    open: List<LiveAccountSummary>,
    currencies: Map<String, String>,
    budgetCurrency: String,
    onPick: (LiveAccountSummary) -> Unit,
) {
    val palette = tandemPalette
    SectionCard("Account currencies", "Tap an account to change its currency") {
        open.forEachIndexed { index, account ->
            if (index > 0) DividerLine()
            val code = currencies[account.id]
            ListRow(
                account.name,
                account.balanceMinor?.let { state.formatAccountMoney(it, code) },
                code ?: "$budgetCurrency · Budget",
                leading = RowLeading.Icon(accountTypeIcon(account.name, account.type)),
                options = ListRowOptions(dense = true, trailingColor = if (code == null) palette.muted else palette.text),
                onClick = { onPick(account) }.takeIf { !state.readOnlyMode },
            )
        }
    }
}

@Composable
private fun ExchangeRateDialog(
    currency: String?,
    budgetCurrency: String,
    rates: List<ExchangeRate>,
    onDismiss: () -> Unit,
    onSave: (ExchangeRate, Boolean) -> Unit,
) {
    val history = currency?.let { rateHistory(it, budgetCurrency, rates) }.orEmpty()
    val existing = history.lastOrNull()
    var rate by remember(currency) { mutableStateOf(existing?.value?.let(::formatRate).orEmpty()) }
    var date by remember(currency) { mutableStateOf(platformTodayIsoDate()) }
    var error by remember(currency) { mutableStateOf<String?>(null) }
    TandemChoiceDialog(visible = currency != null, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("$currency to $budgetCurrency", onClose = onDismiss, leadingIcon = Lucide.ArrowLeftRight)
                Text(
                    "How many $budgetCurrency one $currency is worth, from your bank or a rate site, and the day you checked.",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                TextFieldCard("1 $currency in $budgetCurrency", rate, { rate = it.take(14) }, TextFieldOptions(placeholder = "1.08"))
                TextFieldCard("As of", date, { date = it.take(10) }, TextFieldOptions(placeholder = "2026-11-21"))
                if (history.size > 1) {
                    Text(
                        "Earlier rates\n" +
                            history.dropLast(1).takeLast(5).asReversed().joinToString("\n") {
                                "${formatRate(it.value!!)} $budgetCurrency · ${formatLiveDate(it.asOf)}"
                            },
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.compactBody,
                    )
                }
                error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                TandemActionButton("Save rate", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
                    val parsed =
                        rate
                            .trim()
                            .replace(',', '.')
                            .toDoubleOrNull()
                            ?.takeIf { it > 0 && it.isFinite() }
                    error =
                        when {
                            parsed == null -> "Enter a rate above zero, like 1.08."
                            !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date.trim()) -> "Use a date like 2026-11-21."
                            else -> null
                        }
                    if (error == null && parsed != null && currency != null) {
                        onSave(ExchangeRate(currency, budgetCurrency, parsed.toString(), date.trim()), false)
                    }
                }
                if (existing != null && currency != null) {
                    TandemActionButton("Remove rate", TandemActionButtonTone.DESTRUCTIVE, Modifier.fillMaxWidth()) {
                        onSave(existing, true)
                    }
                }
            }
        }
    }
}

private fun netWorthDetail(
    total: ConvertedTotal,
    budgetCurrency: String,
): String = total.oldestRateDate?.let { "Approximate, in $budgetCurrency with rates from ${formatLiveDate(it)}" } ?: "In $budgetCurrency"

/** Up to six significant decimals, without trailing zeros. */
internal fun formatRate(value: Double): String {
    val text = ((value * 1_000_000).let { kotlin.math.round(it) } / 1_000_000).toString()
    return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
}
