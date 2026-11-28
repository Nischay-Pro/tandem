@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.createTransaction
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.MoneyAmountInput
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.launch

/** This month's loan payment: the part that reduces the loan, the interest, and any escrow paid with it. */
internal data class LoanPayment(
    val principalMinor: Long,
    val interestMinor: Long,
    val escrowMinor: Long,
) {
    val totalMinor: Long get() = principalMinor + interestMinor + escrowMinor
}

/** The next payment on a loan owing [owedMinor], split as its schedule's first month, plus [escrowMinor]. */
internal fun loanPayment(
    owedMinor: Long,
    aprBasisPoints: Int,
    paymentMinor: Long,
    escrowMinor: Long,
): LoanPayment? =
    PlanningMath
        .amortisation(owedMinor, aprBasisPoints, paymentMinor)
        ?.firstOrNull()
        ?.let { LoanPayment(it.principalMinor, it.interestMinor, escrowMinor.coerceAtLeast(0)) }

/** The payee that moves money into [accountId], which a transfer to it is written against. */
internal fun transferPayeeFor(
    payees: List<BudgetRecordEntity>,
    accountId: String,
): String? = payees.firstOrNull { !it.localDeleted && it.recordBody()?.stringValue("transfer_acct") == accountId }?.recordId

/** Open on-budget accounts a loan payment can come from, as (id, name). */
internal fun paymentSources(
    accounts: List<BudgetRecordEntity>,
    loanId: String,
): List<Pair<String, String>> =
    accounts.mapNotNull { record ->
        val body = record.recordBody() ?: return@mapNotNull null
        val usable = record.recordId != loanId && !record.localDeleted && body.booleanValue("closed") != true
        val onBudget = body.booleanValue("offbudget") != true && body.booleanValue("offBudget") != true
        record.recordName()?.takeIf { usable && onBudget }?.let { record.recordId to it }
    }

/**
 * Records a loan payment: the whole amount moves from the chosen account into the loan, then the interest and escrow are
 * charged inside the loan, so its balance falls by the principal only.
 */
@Composable
internal fun LoanPaymentSheet(
    visible: Boolean,
    loan: LiveAccountSummary,
    split: LoanPayment,
    state: AppState,
    mobileSession: TandemMobileSession,
    onDismiss: () -> Unit,
) {
    val sources = remember(mobileSession.accountRecords, loan.id) { paymentSources(mobileSession.accountRecords, loan.id) }
    var sourceId by remember(loan.id, visible) { mutableStateOf(sources.firstOrNull()?.first.orEmpty()) }
    var escrowText by remember(loan.id, visible) { mutableStateOf("") }
    val payment = split.copy(escrowMinor = state.parseAmountMinorUnits(escrowText)?.coerceAtLeast(0) ?: 0L)
    val scope = rememberCoroutineScope()
    val transferPayee = transferPayeeFor(mobileSession.payeeRecords, loan.id)
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        SheetCard(
            header = { TandemModalHeader("Record a loan payment", onClose = onDismiss) },
            actions = {
                TandemActionButton(
                    "Record ${state.formatCurrency(payment.totalMinor)}",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    enabled = sourceId.isNotBlank() && transferPayee != null && !state.readOnlyMode && !mobileSession.isSavingTransaction,
                ) {
                    scope.launch {
                        if (recordLoanPayment(state, mobileSession, sourceId, loan.id, transferPayee.orEmpty(), payment)) onDismiss()
                    }
                }
            },
        ) {
            SourcePicker(sources, sourceId) { sourceId = it }
            PaymentRow("Principal", state.formatCurrency(payment.principalMinor))
            PaymentRow("Interest", state.formatCurrency(payment.interestMinor))
            MoneyAmountInput("Escrow (optional)", escrowText, state.formatCurrency(0), { escrowText = it })
            BodyText("The whole amount moves into ${loan.name}; the interest and escrow are then charged there.")
            mobileSession.transactionWriteMessage?.let { BodyText(it) }
        }
    }
}

private suspend fun recordLoanPayment(
    state: AppState,
    mobileSession: TandemMobileSession,
    sourceId: String,
    loanId: String,
    transferPayee: String,
    payment: LoanPayment,
): Boolean {
    val budgetId = state.selectedBudgetId
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    val today = platformTodayIsoDate()
    val settings = state.budgetDisplaySettings
    val transactions = mobileSession.transactions
    val moved =
        transactions.createTransaction(
            budgetId,
            sourceId,
            transferPayee,
            null,
            state.formatAmount(-payment.totalMinor),
            today,
            "Loan payment",
            password,
            settings,
            isTransfer = true,
        )
    if (!moved) return false
    val charges = listOf("Loan interest" to payment.interestMinor, "Loan escrow" to payment.escrowMinor).filter { it.second > 0 }
    return charges.all { (note, amount) ->
        transactions.createTransaction(budgetId, loanId, null, null, state.formatAmount(-amount), today, note, password, settings)
    }
}

@Composable
private fun PaymentRow(
    title: String,
    amount: String,
) {
    ListRow(title, trailing = amount, options = ListRowOptions(showChevron = false))
}

@Composable
private fun SourcePicker(
    sources: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val name = sources.firstOrNull { it.first == selected }?.second ?: "Choose an account"
        ListRow("Paid from", trailing = name, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sources.forEach { (id, name) ->
                DropdownMenuItem(onClick = {
                    onSelect(id)
                    expanded = false
                }) {
                    Text(name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
