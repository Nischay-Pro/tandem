@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
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
    TransactionFormFrame { layout ->
        NewTransactionFormContent(state, navigation, mobileSession, liveAccounts, pickers, layout, onCreateTransaction)
    }
}

@Composable
private fun NewTransactionFormContent(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
    pickers: TransactionFormPickerState,
    layout: TransactionFormLayout,
    onCreateTransaction: (String?) -> Unit,
) {
    val amountEntry = rememberTransactionAmountEntryState(keypadVisible = !layout.keypadStartsClosed)
    CollapseKeypadWhenShort(layout, amountEntry)
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
    TransactionFormScaffold(
        layout,
        amountEntry.keypadVisible,
        keypad = { TransactionFormKeypad(state, layout, amountEntry.keypadActions(state, editing = false, dismissKeypad), it) },
    ) {
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
            NewTransactionTools(state, mobileSession, liveAccounts.isOffBudget(state.newTransactionAccountId), receiptScan, dismissKeypad)
            val offBudget = liveAccounts.isOffBudget(state.newTransactionAccountId)
            val splitting = state.transactionSplit != null && state.transactionType != "Transfer" && !offBudget
            TransactionFormDetailsCard(state, navigation, pickers, offBudget, dismissKeypad, showCategory = !splitting)
            if (state.transactionType != "Transfer" && !offBudget) {
                TransactionSplitCard(state, navigation, amountMinorUnits, dismissKeypad)
            }
            TransactionNotesField(state) { focused ->
                notesFocused = focused
                if (focused) amountEntry.keypadVisible = false
            }
        }
        NewTransactionActionBar(
            amountEntry.keypadVisible && !layout.keypadBeside,
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
    }
    ReceiptScanOverlay(receiptScan.scanningReceipt, receiptScan.receiptPreview)
    NewTransactionAccountPicker(state, liveAccounts, pickers)
    TransactionFormDatePicker(state, pickers)
    TransactionSaveConfirmationDialogs(confirmations, budgetAmountPreview, onCreateTransaction)
}

/** Saved templates, then split transaction and the receipt scanner side by side, for spending and income. */
@Composable
private fun NewTransactionTools(
    state: AppState,
    mobileSession: TandemMobileSession,
    offBudget: Boolean,
    receiptScan: TransactionReceiptScanState,
    onDismissKeypad: () -> Unit,
) {
    TransactionTemplatesRow(state, mobileSession)
    if (state.transactionType == "Transfer") return
    TransactionToolsRow {
        if (state.transactionSplit == null && !offBudget) TransactionSplitButton(state, onDismissKeypad)
        TransactionReceiptScanButton(receiptScan, onDismissKeypad)
    }
    TransactionReceiptScanMessage(receiptScan)
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
    val split = state.transactionSplit
    return when {
        state.transactionType == "Transfer" -> isValidTransferDestination(state.newTransactionAccountId, transferDestinationAccountId)
        offBudget -> true
        split != null ->
            split.isReady(
                transactionAmountInputMagnitude(state.parseAmountMinorUnits(state.transactionAmount)),
                state::parseAmountMinorUnits,
            )
        else -> state.transactionCategory.isNotBlank()
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
