@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun DebtTermsSheet(
    account: LiveAccountSummary?,
    terms: DebtTerms?,
    settings: BudgetDisplaySettings?,
    onSave: (DebtTerms) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = tandemPalette
    var apr by remember(account) { mutableStateOf(terms?.aprBasisPoints?.let { formatRateBasisPoints(it).removeSuffix("%") }.orEmpty()) }
    var minimum by remember(account) { mutableStateOf(terms?.minimumPaymentMinor?.let { formatAmount(it, settings) }.orEmpty()) }
    var dueDay by remember(account) { mutableStateOf(terms?.dueDay?.toString().orEmpty()) }
    var kind by remember(account) { mutableStateOf(terms?.kind ?: DebtKind.UNSPECIFIED) }
    var paysInFull by remember(account) { mutableStateOf(terms?.paysInFull == true) }
    var keepPrivate by remember(account) { mutableStateOf(terms?.shared == false) }
    var error by remember(account) { mutableStateOf<String?>(null) }
    TandemTaskSheet(visible = account != null, onDismissRequest = onDismiss, onDismissed = {}) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                TandemModalHeader(account?.name.orEmpty(), onClose = onDismiss)
                Text(
                    "Used to estimate payoff. Leave anything you don’t know blank.",
                    color = palette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                DebtKindPills(kind, paysInFull, { kind = it }, { paysInFull = it })
                TextFieldCard("APR (%)", apr, { apr = it.take(6) }, TextFieldOptions(placeholder = "19.99"))
                TextFieldCard("Minimum payment", minimum, { minimum = it }, settings.amountOptions())
                TextFieldCard(
                    "Due day of the month",
                    dueDay,
                    { dueDay = it.filter(Char::isDigit).take(2) },
                    TextFieldOptions(placeholder = "12"),
                )
                ToggleRow("Keep private", "Stays on this device. Others on this budget won’t see it.", keepPrivate) { keepPrivate = it }
                error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
                    val rate = apr.takeIf { it.isNotBlank() }?.let(::parseRateBasisPoints)
                    val payment = minimum.takeIf { it.isNotBlank() }?.let { parseAmountMinorUnits(it, settings) }
                    val day = dueDay.toIntOrNull()
                    error =
                        when {
                            apr.isNotBlank() && rate == null -> "Enter the APR as a percentage, like 19.99."
                            minimum.isNotBlank() && (payment == null || payment < 0) -> "Enter the minimum payment."
                            dueDay.isNotBlank() && (day == null || day !in 1..31) -> "Enter a day between 1 and 31."
                            else -> null
                        }
                    if (error == null && account != null) {
                        onSave(
                            DebtTerms(
                                terms?.id ?: Uuid.random().toString(),
                                account.id,
                                rate,
                                payment,
                                day,
                                kind = kind,
                                paysInFull = kind == DebtKind.CARD && paysInFull,
                                shared = !keepPrivate,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Whether the account is a card, a loan or not a debt, and for cards whether it is paid in full. */
@Composable
private fun DebtKindPills(
    kind: DebtKind,
    paysInFull: Boolean,
    onKindChange: (DebtKind) -> Unit,
    onPaysInFullChange: (Boolean) -> Unit,
) {
    FilterPills(
        listOf(DebtKind.CARD to "Credit card", DebtKind.LOAN to "Loan", DebtKind.NOT_DEBT to "Not a debt"),
        kind,
        onKindChange,
    )
    if (kind == DebtKind.CARD) {
        FilterPills(
            listOf(false to "I carry a balance", true to "I pay it in full each month"),
            paysInFull,
            onPaysInFullChange,
        )
    }
}
