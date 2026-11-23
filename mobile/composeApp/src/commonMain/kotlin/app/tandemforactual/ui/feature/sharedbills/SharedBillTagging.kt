package app.tandemforactual.ui.feature.sharedbills

import app.tandemforactual.data.planning.PaymentCandidate
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SharedBillMember
import app.tandemforactual.data.planning.SharedBills
import app.tandemforactual.data.planning.TaggedPayment
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.createTransaction
import app.tandemforactual.ui.core.session.transactions.updateTransaction
import app.tandemforactual.ui.core.state.AppState

/** Rewrites an incoming payment's notes, then syncs so the budget file has the tags too. */
internal suspend fun TandemMobileSession.retagPayment(
    state: AppState,
    paymentId: String,
    amountMinor: Long,
    notes: String,
    onSaved: () -> Unit = {},
): Boolean {
    val saved =
        transactions.updateTransaction(
            budgetId = state.selectedBudgetId,
            transactionId = paymentId,
            amountText = formatAmount(amountMinor, state.budgetDisplaySettings),
            notes = notes,
            originalAmountMinor = amountMinor,
            transactionType = "Income",
            displaySettings = state.budgetDisplaySettings,
        )
    if (saved) onSaved()
    if (saved) synchronize(state.selectedBudgetId, state.encryptedPassword.takeIf { it.isNotBlank() })
    return saved
}

internal suspend fun TandemMobileSession.tagPayment(
    state: AppState,
    bill: SharedBill,
    member: SharedBillMember,
    payment: PaymentCandidate,
): Boolean {
    val notes = SharedBills.tagNotes(payment.notes, bill, member)
    return retagPayment(state, payment.id, payment.amountMinor, notes) {
        PendingBillTags.added += TaggedPayment(payment.id, payment.date, payment.amountMinor, notes)
    }
}

/** Takes [bill]'s tags off a payment read from the budget file; [localId] is the device's id for it. */
internal suspend fun TandemMobileSession.untagPayment(
    state: AppState,
    bill: SharedBill,
    payment: TaggedPayment,
    localId: String,
): Boolean =
    retagPayment(state, localId, payment.amountMinor, SharedBills.untagNotes(payment.notes, bill)) {
        PendingBillTags.removed += payment.id
        PendingBillTags.added.removeAll { it.id == localId }
    }

/** Adds money in that never reached a bank, like cash, with the bill's and the person's tags. */
internal suspend fun TandemMobileSession.recordPayment(
    state: AppState,
    bill: SharedBill,
    member: SharedBillMember,
    accountId: String,
    amountText: String,
    date: String,
    note: String,
): Boolean {
    val notes = SharedBills.tagNotes("$note from ${member.name}", bill, member)
    val saved =
        transactions.createTransaction(
            budgetId = state.selectedBudgetId,
            accountId = accountId,
            payeeId = null,
            categoryId = null,
            amountText = amountText,
            dateText = date,
            notes = notes,
            encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
            displaySettings = state.budgetDisplaySettings,
        )
    val amount = parseAmountMinorUnits(amountText, state.budgetDisplaySettings)
    if (saved && amount != null) PendingBillTags.added += TaggedPayment("new:$date:$amount", date, amount, notes)
    return saved
}
