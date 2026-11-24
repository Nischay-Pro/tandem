@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.inputs.isValidTransferDestination
import app.tandemforactual.ui.design.inputs.transactionAmountInputMagnitude
import app.tandemforactual.ui.design.templates.FloatingTransactionSave
import app.tandemforactual.ui.design.templates.TransactionAmountKeypad
import app.tandemforactual.ui.feature.transactions.TransactionAccountPickerDialog
import app.tandemforactual.ui.feature.transactions.receipt.ReceiptScanOverlay

/** The form for adding a transaction, with the amount keypad and receipt scanning. */
@Composable
internal fun NewTransactionForm(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
    pickers: TransactionFormPickerState,
    onCreateTransaction: (String?) -> Unit,
) {
    val amountEntry = rememberTransactionAmountEntryState(keypadVisible = true)
    val confirmations = rememberTransactionSaveConfirmationState()
    var notesFocused by remember { mutableStateOf(false) }
    val compactSave = transactionFormKeyboardHandling(notesFocused, amountEntry.keypadVisible)
    val parsedAmountMinorUnits = state.parseAmountMinorUnits(state.transactionAmount)
    val amountMinorUnits = transactionAmountInputMagnitude(parsedAmountMinorUnits)
    val budgetAmountPreview = transactionBudgetAmountPreview(state, parsedAmountMinorUnits)
    val transactionTargetIsValid =
        newTransactionTargetIsValid(state, mobileSession, liveAccounts.isOffBudget(state.newTransactionAccountId))
    val receiptScan = rememberTransactionReceiptScan(state, mobileSession) { amountEntry.keypadVisible = false }
    val dismissKeypad = dismissKeypadAction(amountEntry)
    Column(Modifier.fillMaxSize().imePadding()) {
        TransactionFormPage(amountEntry.keypadVisible || notesFocused, dismissKeypad) {
            TransactionAmountHeader(
                state,
                mobileSession,
                amountMinorUnits,
                amountEntry.keypadVisible,
                budgetAmountPreview,
                transactionAmountInputErrorMessage(state, parsedAmountMinorUnits) ?: amountEntry.amountEntryMessage,
                onShowKeypad = { amountEntry.keypadVisible = true },
                onDismissKeypad = dismissKeypad,
            )
            if (state.transactionType != "Transfer") TransactionReceiptScanButton(receiptScan, dismissKeypad)
            TransactionFormDetailsCard(state, navigation, pickers, liveAccounts.isOffBudget(state.newTransactionAccountId), dismissKeypad)
            TransactionNotesField(state) { focused ->
                notesFocused = focused
                if (focused) amountEntry.keypadVisible = false
            }
        }
        NewTransactionActionBar(
            amountEntry.keypadVisible,
            compactSave,
            saveEnabled =
                !mobileSession.isSavingTransaction &&
                    state.newTransactionAccountId.isNotBlank() &&
                    state.transactionPayee.isNotBlank() &&
                    transactionTargetIsValid &&
                    parsedAmountMinorUnits != null &&
                    parsedAmountMinorUnits > 0 &&
                    amountMinorUnits > 0,
            onCancel = {
                dismissKeypad()
                navigation.back()
            },
            onSave = {
                dismissKeypad()
                confirmations.requestSave(state.transactionDate, budgetAmountPreview, onCreateTransaction)
            },
        )
        if (amountEntry.keypadVisible) {
            TransactionAmountKeypad(
                onDigit = { amountEntry.enterDigit(state, it) },
                onDelete = { amountEntry.deleteDigit(state) },
                onOperator = { amountEntry.chooseOperator(state, it) },
                onEquals = { amountEntry.calculate(state) },
                onDone = dismissKeypad,
            )
        }
    }
    ReceiptScanOverlay(receiptScan.scanningReceipt, receiptScan.receiptPreview)
    NewTransactionAccountPicker(state, liveAccounts, pickers)
    TransactionFormDatePicker(state, pickers)
    TransactionSaveConfirmationDialogs(confirmations, budgetAmountPreview, onCreateTransaction)
}

/** Cancel on the left, save on the right. */
@Composable
private fun NewTransactionActionBar(
    keypadVisible: Boolean,
    compactSave: Boolean,
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    TransactionFormActionBar(keypadVisible) {
        SecondaryButton("Cancel", Modifier.widthIn(min = 112.dp).width(IntrinsicSize.Max), onClick = onCancel)
        Spacer(Modifier.weight(1f))
        FloatingTransactionSave(compact = compactSave, enabled = saveEnabled, onClick = onSave)
    }
}

@Composable
private fun newTransactionTargetIsValid(
    state: AppState,
    mobileSession: TandemMobileSession,
    offBudget: Boolean,
): Boolean {
    val transferDestinationAccountId =
        remember(state.newTransactionPayeeId, mobileSession.payeeRecords) {
            mobileSession.payeeRecords
                .firstOrNull { it.recordId == state.newTransactionPayeeId }
                ?.recordBody()
                ?.stringValue("transfer_acct")
        }
    return if (state.transactionType == "Transfer") {
        isValidTransferDestination(state.newTransactionAccountId, transferDestinationAccountId)
    } else {
        offBudget || state.transactionCategory.isNotBlank()
    }
}

/** Picks the new transaction's account and remembers it for the next one. */
@Composable
private fun NewTransactionAccountPicker(
    state: AppState,
    liveAccounts: List<LiveAccountSummary>,
    pickers: TransactionFormPickerState,
) {
    TransactionAccountPickerDialog(
        visible = pickers.showAccountPicker,
        liveAccounts,
        state.newTransactionAccountId,
        state.transactionAccount,
        live = true,
        onSelect = { account ->
            state.transactionAccount = account.label
            state.newTransactionAccountId = account.id
            state.lastTransactionAccountId = account.id
            pickers.showAccountPicker = false
        },
        onDismiss = { pickers.showAccountPicker = false },
    )
}
