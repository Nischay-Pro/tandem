@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.Compounding
import app.tandemforactual.data.planning.DepositTerms
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.ui.amountOptions
import app.tandemforactual.ui.core.ui.formatRateBasisPoints
import app.tandemforactual.ui.core.ui.parseRateBasisPoints
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import kotlin.uuid.Uuid

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** The deposit the form describes, or the first thing to fix. */
internal fun depositTermsFrom(
    id: String,
    accountId: String,
    rate: String,
    compounding: Compounding,
    contribution: String,
    maturesOn: String,
    settings: BudgetDisplaySettings?,
): Result<DepositTerms> {
    val rateBasisPoints = parseRateBasisPoints(rate)
    val monthly = contribution.takeIf { it.isNotBlank() }?.let { parseAmountMinorUnits(it, settings) } ?: 0L
    val date = maturesOn.trim().takeIf { it.isNotEmpty() }
    val problem =
        when {
            rateBasisPoints == null || rateBasisPoints < 0 -> "Enter the yearly rate as a percentage, like 7.25."
            contribution.isNotBlank() && (parseAmountMinorUnits(contribution, settings) ?: -1) < 0 -> "Enter the monthly amount."
            date != null && !ISO_DATE.matches(date) -> "Enter the maturity date as YYYY-MM-DD."
            else -> null
        }
    if (problem != null || rateBasisPoints == null) return Result.failure(IllegalArgumentException(problem))
    return Result.success(DepositTerms(id, accountId, rateBasisPoints, compounding, monthly, date))
}

@Composable
internal fun DepositTermsSheet(
    visible: Boolean,
    account: LiveAccountSummary,
    terms: DepositTerms?,
    state: AppState,
    onDismiss: () -> Unit,
    onSave: (DepositTerms?) -> Unit,
) {
    val settings = state.budgetDisplaySettings
    var rate by remember(terms, visible) {
        mutableStateOf(terms?.rateBasisPoints?.let { formatRateBasisPoints(it).removeSuffix("%") }.orEmpty())
    }
    var compounding by remember(terms, visible) { mutableStateOf(terms?.compounding ?: Compounding.QUARTERLY) }
    var contribution by remember(terms, visible) {
        val monthly = terms?.monthlyContributionMinor?.takeIf { it > 0 }
        mutableStateOf(monthly?.let { formatAmount(it, settings) }.orEmpty())
    }
    var maturesOn by remember(terms, visible) { mutableStateOf(terms?.maturesOn.orEmpty()) }
    var error by remember(terms, visible) { mutableStateOf<String?>(null) }
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        SheetCard(
            header = { TandemModalHeader(account.name, onClose = onDismiss) },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    if (terms != null) {
                        TandemActionButton("Remove", TandemActionButtonTone.DESTRUCTIVE, Modifier.weight(1f)) { onSave(null) }
                    }
                    TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.weight(1f)) {
                        val id = terms?.id ?: Uuid.random().toString()
                        val result = depositTermsFrom(id, account.id, rate, compounding, contribution, maturesOn, settings)
                        error = result.exceptionOrNull()?.message
                        result.getOrNull()?.let(onSave)
                    }
                }
            },
        ) {
            TextFieldCard("Yearly rate (%)", rate, { rate = it.take(6) }, TextFieldOptions(placeholder = "7.25"))
            FilterPills(compoundingLabels, compounding, { compounding = it })
            TextFieldCard("Added each month", contribution, { contribution = it }, settings.amountOptions())
            TextFieldCard("Matures on", maturesOn, { maturesOn = it.take(10) }, TextFieldOptions(placeholder = "2028-06-30"))
            error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
        }
    }
}
