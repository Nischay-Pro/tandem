@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.inputs.transactionAmountInputMagnitude
import app.tandemforactual.ui.design.templates.FloatingTransactionDeleteAction
import app.tandemforactual.ui.design.templates.FloatingTransactionSave
import app.tandemforactual.ui.design.templates.TransactionAmountKeypad
import app.tandemforactual.ui.design.theme.tandemFadeEnter
import app.tandemforactual.ui.design.theme.tandemFadeExit
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.transactions.TransactionAccountPickerDialog
import kotlinx.coroutines.delay

/** The selected transaction as last saved, and whether "Saved" feedback is showing. */
internal class EditTransactionBaseline(
    state: AppState,
) {
    var originalAmountMinor by mutableStateOf(state.selectedTransactionAmountMinor)
    var originalTransactionType by mutableStateOf(
        when {
            state.selectedTransactionIsTransfer -> "Transfer"
            (state.selectedTransactionAmountMinor ?: 0L) < 0 -> "Spending"
            else -> "Income"
        },
    )
    var originalNotes by mutableStateOf(state.selectedTransactionNotes)
    var savedFeedback by mutableStateOf(false)

    fun hasChanges(
        state: AppState,
        parsedAmount: Long?,
    ): Boolean {
        val baselineAmount = originalAmountMinor?.let { kotlin.math.abs(it) }
        return parsedAmount != baselineAmount ||
            state.transactionType != originalTransactionType ||
            state.transactionNotes != originalNotes ||
            state.newTransactionAccountId != state.selectedTransactionAccountId ||
            state.newTransactionPayeeId != state.selectedTransactionPayeeId ||
            state.newTransactionCategoryId != state.selectedTransactionCategoryId ||
            state.transactionDate != state.selectedTransactionDate
    }

    fun markSaved(state: AppState) {
        originalAmountMinor = state.selectedTransactionAmountMinor
        originalTransactionType = state.transactionType
        originalNotes = state.selectedTransactionNotes
        savedFeedback = true
    }
}

/** The form for editing the selected transaction, with the amount keypad, delete and save. */
@Composable
internal fun EditTransactionForm(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
    pickers: TransactionFormPickerState,
    onSaveTransaction: (String?, (Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
) {
    val baseline = remember(state.selectedTransactionRecordId) { EditTransactionBaseline(state) }
    val confirmations = rememberTransactionSaveConfirmationState(state.selectedTransactionRecordId)
    val amountEntry = rememberTransactionAmountEntryState(state.selectedTransactionRecordId, keypadVisible = false)
    var notesFocused by remember { mutableStateOf(false) }
    val parsedAmount = state.parseAmountMinorUnits(state.transactionAmount)
    val amountMinorUnits = transactionAmountInputMagnitude(parsedAmount ?: state.selectedTransactionAmountMinor)
    val budgetAmountPreview = transactionBudgetAmountPreview(state, parsedAmount)
    val compactSave = transactionFormKeyboardHandling(notesFocused, amountEntry.keypadVisible)
    val dismissKeypad = dismissKeypadAction(amountEntry)
    val saveEditTransaction: (String?) -> Unit = { confirmedFutureDate ->
        onSaveTransaction(confirmedFutureDate) { saved -> if (saved) baseline.markSaved(state) }
    }
    val hasChanges = baseline.hasChanges(state, parsedAmount)
    LaunchedEffect(baseline.savedFeedback) {
        if (baseline.savedFeedback) {
            delay(900)
            baseline.savedFeedback = false
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TransactionFormPage(amountEntry.keypadVisible || notesFocused, dismissKeypad) {
            TransactionAmountHeader(
                state,
                mobileSession,
                amountMinorUnits,
                amountEntry.keypadVisible,
                budgetAmountPreview,
                transactionAmountInputErrorMessage(state, parsedAmount) ?: amountEntry.amountEntryMessage,
                onShowKeypad = { amountEntry.keypadVisible = true },
                onDismissKeypad = dismissKeypad,
            )
            TransactionFormDetailsCard(state, navigation, pickers, dismissKeypad)
            TransactionNotesField(state) { focused ->
                notesFocused = focused
                if (focused) amountEntry.keypadVisible = false
            }
        }
        EditTransactionActionBar(
            amountEntry.keypadVisible,
            deleteEnabled = !mobileSession.isSavingTransaction,
            saveVisible = hasChanges || baseline.savedFeedback,
            compactSave = compactSave || baseline.savedFeedback,
            saveEnabled = hasChanges && !mobileSession.isSavingTransaction && parsedAmount != null && parsedAmount > 0,
            saved = baseline.savedFeedback,
            onDelete = {
                dismissKeypad()
                onDeleteTransaction { deleted -> if (deleted) navigation.backToList() }
            },
            onSave = {
                dismissKeypad()
                confirmations.requestSave(state.transactionDate, budgetAmountPreview, saveEditTransaction)
            },
        )
        if (amountEntry.keypadVisible) {
            TransactionAmountKeypad(
                onDigit = { amountEntry.enterEditDigit(state, it) },
                onDelete = { amountEntry.deleteEditDigit(state) },
                onOperator = { amountEntry.chooseEditOperator(state, it) },
                onEquals = { amountEntry.calculateEditAmount(state) },
                onDone = dismissKeypad,
            )
        }
    }
    EditTransactionAccountPicker(state, liveAccounts, pickers)
    TransactionFormDatePicker(state, pickers)
    TransactionSaveConfirmationDialogs(confirmations, budgetAmountPreview, saveEditTransaction)
}

/** Delete on the left; save on the right while there are changes or "Saved" is showing. */
@Composable
private fun EditTransactionActionBar(
    keypadVisible: Boolean,
    deleteEnabled: Boolean,
    saveVisible: Boolean,
    compactSave: Boolean,
    saveEnabled: Boolean,
    saved: Boolean,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    TransactionFormActionBar(keypadVisible) {
        FloatingTransactionDeleteAction(enabled = deleteEnabled, onClick = onDelete)
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(
            visible = saveVisible,
            enter = tandemFadeEnter(tandemReduceMotion),
            exit = tandemFadeExit(tandemReduceMotion),
        ) {
            FloatingTransactionSave(compact = compactSave, enabled = saveEnabled, saved = saved, onClick = onSave)
        }
    }
}

/** Picks the edited transaction's account. */
@Composable
private fun EditTransactionAccountPicker(
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
            pickers.showAccountPicker = false
        },
        onDismiss = { pickers.showAccountPicker = false },
    )
}
