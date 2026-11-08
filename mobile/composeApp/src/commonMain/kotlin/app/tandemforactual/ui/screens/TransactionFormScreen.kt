@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards
import kotlinx.coroutines.delay

@Composable
internal fun TransactionFormScreen(
    route: AppRoute,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    onSaveTransaction: ((Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    onCreateTransaction: () -> Unit,
) {
    if (state.readOnlyMode) {
        PageColumn {
            Heading("Read-only mode")
            BodyText("Turn it off in Device protection to edit this transaction.")
        }
        return
    }
    val isNew = route == AppRoute.NEW_TRANSACTION
    if (state.selectedBudgetId.isNotBlank()) {
        if (isNew) {
            var keypadVisible by rememberSaveable { mutableStateOf(true) }
            var calculatorAccumulator by rememberSaveable { mutableStateOf<Long?>(null) }
            var calculatorOperator by rememberSaveable { mutableStateOf<String?>(null) }
            var enterNewOperand by rememberSaveable { mutableStateOf(false) }
            var amountEntryMessage by rememberSaveable { mutableStateOf<String?>(null) }
            val amountFocusRequester = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current
            val softwareKeyboard = LocalSoftwareKeyboardController.current
            val routeScrollState = LocalRouteScrollState.current
            val compactSave by remember(routeScrollState) {
                derivedStateOf { (routeScrollState?.value ?: 0) > 72 }
            }
            var amountMinorUnits by
                remember(state.transactionAmount) {
                    mutableStateOf(
                        state
                            .parseAmountMinorUnits(state.transactionAmount)
                            ?.let {
                                (if (it == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(it))
                                    .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
                            }
                            ?: 0L,
                    )
                }

            fun setAmount(minorUnits: Long) {
                val magnitude =
                    (if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minorUnits))
                        .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
                amountMinorUnits = magnitude
                state.transactionAmount = state.formatAmount(magnitude)
            }

            fun enterDigit(digit: Int) {
                val current = if (enterNewOperand) 0L else amountMinorUnits
                val exceededLimit = amountDigitExceedsLimit(current, digit)
                setAmount(appendAmountDigit(current, digit))
                amountEntryMessage =
                    if (exceededLimit) {
                        "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
                    } else {
                        null
                    }
                enterNewOperand = false
            }

            fun chooseOperator(operator: String) {
                val previous = calculatorAccumulator
                val pending = calculatorOperator
                if (previous != null && pending != null && !enterNewOperand) {
                    val result = calculateAmountMinorUnits(previous, amountMinorUnits, pending)
                    if (result == null) {
                        amountEntryMessage =
                            if (pending == "÷" && amountMinorUnits == 0L) {
                                "Can’t divide by zero."
                            } else {
                                "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
                            }
                        return
                    }
                    setAmount(result)
                    calculatorAccumulator = kotlin.math.abs(result)
                } else if (previous == null) {
                    calculatorAccumulator = amountMinorUnits
                }
                amountEntryMessage = null
                calculatorOperator = operator
                enterNewOperand = true
            }

            fun calculate() {
                val previous = calculatorAccumulator
                val operator = calculatorOperator
                if (previous != null && operator != null) {
                    val result =
                        if (enterNewOperand) previous else calculateAmountMinorUnits(previous, amountMinorUnits, operator)
                    if (result == null) {
                        amountEntryMessage =
                            if (operator == "÷" && amountMinorUnits == 0L) {
                                "Can’t divide by zero."
                            } else {
                                "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
                            }
                        return
                    }
                    setAmount(result)
                }
                amountEntryMessage = null
                calculatorAccumulator = null
                calculatorOperator = null
                enterNewOperand = true
            }

            LaunchedEffect(keypadVisible) {
                if (keypadVisible) {
                    softwareKeyboard?.hide()
                    amountFocusRequester.requestFocus()
                }
            }

            Box(Modifier.fillMaxSize()) {
                PageColumn(
                    verticalSpacing = 14.dp,
                    modifier = Modifier.imePadding().padding(bottom = if (keypadVisible) AMOUNT_KEYPAD_HEIGHT + 68.dp else 76.dp),
                ) {
                    TransactionAmountField(
                        amount = state.transactionAmount.ifBlank { state.formatAmount(0) },
                        amountMinorUnits = amountMinorUnits,
                        transactionType = state.transactionType,
                        focusRequester = amountFocusRequester,
                        onShowKeypad = { keypadVisible = true },
                    )
                    AnimatedReveal(visible = amountEntryMessage != null) {
                        InlineMessage(amountEntryMessage.orEmpty(), InlineMessageKind.ERROR)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TransactionTypeChip(state.transactionType) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_TRANSACTION_TYPE)
                        }
                    }
                    SectionCard(title = "") {
                        TransactionPickerRow(
                            state.transactionPayee,
                            "Choose payee",
                            Lucide.Receipt,
                        ) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_PAYEE)
                        }
                        DividerLine()
                        TransactionPickerRow(
                            state.transactionCategory,
                            "Choose category",
                            Lucide.Tags,
                        ) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_CATEGORY)
                        }
                        DividerLine()
                        TransactionPickerRow(
                            state.transactionAccount,
                            "Choose account",
                            Lucide.WalletCards,
                        ) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_ACCOUNT)
                        }
                        DividerLine()
                        TransactionPickerRow(
                            displayTransactionDate(state.transactionDate),
                            "Choose date",
                            Lucide.CalendarDays,
                        ) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_DATE)
                        }
                    }
                    TextFieldCard(
                        "Notes",
                        state.transactionNotes,
                        { state.transactionNotes = it },
                        TextFieldOptions(
                            placeholder = "Add a note",
                            singleLine = false,
                            onFocusedChange = { focused -> if (focused) keypadVisible = false },
                        ),
                    )
                    AnimatedReveal(visible = mobileSession.transactionWriteMessage != null) {
                        SurfaceCard {
                            Column(Modifier.padding(TandemSpacing.large)) { BodyText(mobileSession.transactionWriteMessage.orEmpty()) }
                        }
                    }
                }
                if (keypadVisible) {
                    TransactionAmountKeypad(
                        onDigit = ::enterDigit,
                        onDelete = {
                            val current = if (enterNewOperand) 0L else amountMinorUnits
                            setAmount(removeAmountDigit(current))
                            amountEntryMessage = null
                            enterNewOperand = false
                        },
                        onOperator = ::chooseOperator,
                        onEquals = ::calculate,
                        onDone = {
                            keypadVisible = false
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                FloatingTransactionSave(
                    compact = compactSave,
                    enabled =
                        !mobileSession.isSavingTransaction &&
                            state.newTransactionAccountId.isNotBlank() &&
                            state.transactionPayee.isNotBlank() &&
                            state.transactionCategory.isNotBlank() &&
                            amountMinorUnits > 0,
                    onClick = onCreateTransaction,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .imePadding()
                            .padding(
                                end = 18.dp,
                                bottom = if (keypadVisible) AMOUNT_KEYPAD_HEIGHT + 12.dp else 12.dp,
                            ),
                )
            }
            return
        }
        var originalAmountMinor by remember(state.selectedTransactionRecordId) { mutableStateOf(state.selectedTransactionAmountMinor) }
        var originalNotes by remember(state.selectedTransactionRecordId) { mutableStateOf(state.selectedTransactionNotes) }
        var savedFeedback by remember(state.selectedTransactionRecordId) { mutableStateOf(false) }
        var confirmDelete by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf(false) }
        val parsedAmount = state.parseAmountMinorUnits(state.transactionAmount)
        val baselineAmount = originalAmountMinor?.let { kotlin.math.abs(it) }
        val hasChanges =
            parsedAmount != baselineAmount ||
                state.transactionNotes != originalNotes ||
                state.newTransactionAccountId != state.selectedTransactionAccountId ||
                state.newTransactionPayeeId != state.selectedTransactionPayeeId ||
                state.newTransactionCategoryId != state.selectedTransactionCategoryId ||
                state.transactionDate != state.selectedTransactionDate
        val routeScrollState = LocalRouteScrollState.current
        val compactSave by remember(routeScrollState) {
            derivedStateOf { (routeScrollState?.value ?: 0) > 72 }
        }
        LaunchedEffect(savedFeedback) {
            if (savedFeedback) {
                delay(900)
                savedFeedback = false
            }
        }
        Box(Modifier.fillMaxSize()) {
            PageColumn(modifier = Modifier.padding(bottom = 76.dp)) {
                Heading("Edit transaction")
                TextFieldCard(
                    "Amount",
                    state.transactionAmount,
                    { state.transactionAmount = it },
                    TextFieldOptions(placeholder = state.formatAmount(4_218)),
                )
                TextFieldCard(
                    "Notes",
                    state.transactionNotes,
                    { state.transactionNotes = it },
                    TextFieldOptions(placeholder = "Add a note", singleLine = false),
                )
                SectionCard(title = "") {
                    TransactionPickerRow(state.transactionAccount, "Choose account", Lucide.WalletCards) {
                        navigation.open(AppRoute.CHOOSE_ACCOUNT)
                    }
                    DividerLine()
                    TransactionPickerRow(state.transactionPayee, "Choose payee", Lucide.Receipt) {
                        navigation.open(AppRoute.CHOOSE_PAYEE)
                    }
                    DividerLine()
                    TransactionPickerRow(state.transactionCategory, "Choose category", Lucide.Tags) {
                        navigation.open(AppRoute.CHOOSE_CATEGORY)
                    }
                    DividerLine()
                    TransactionPickerRow(displayTransactionDate(state.transactionDate), "Choose date", Lucide.CalendarDays) {
                        navigation.open(AppRoute.CHOOSE_DATE)
                    }
                }
                mobileSession.transactionWriteMessage?.let { message ->
                    SurfaceCard {
                        Column(Modifier.padding(TandemSpacing.large)) { BodyText(message) }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).imePadding().padding(end = 18.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = hasChanges || savedFeedback, enter = fadeIn(), exit = fadeOut()) {
                    FloatingTransactionSave(
                        compact = compactSave || savedFeedback,
                        enabled = hasChanges && !mobileSession.isSavingTransaction && parsedAmount != null,
                        saved = savedFeedback,
                        onClick = {
                            onSaveTransaction { saved ->
                                if (saved) {
                                    originalAmountMinor = state.selectedTransactionAmountMinor
                                    originalNotes = state.selectedTransactionNotes
                                    savedFeedback = true
                                }
                            }
                        },
                    )
                }
                FloatingTransactionDeleteAction(
                    enabled = !mobileSession.isSavingTransaction,
                    onClick = { confirmDelete = true },
                )
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { if (!mobileSession.isSavingTransaction) confirmDelete = false },
                title = { Text("Delete transaction?", color = tandemPalette.text) },
                text = { Text("It’ll be removed from this device. The removal will sync later.", color = tandemPalette.muted) },
                confirmButton = {
                    TextButton(
                        enabled = !mobileSession.isSavingTransaction,
                        onClick = {
                            onDeleteTransaction { deleted ->
                                confirmDelete = false
                                if (deleted) navigation.backToList()
                            }
                        },
                    ) {
                        Text("Delete", color = tandemPalette.negative)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !mobileSession.isSavingTransaction, onClick = { confirmDelete = false }) {
                        Text("Keep", color = tandemPalette.muted)
                    }
                },
                backgroundColor = tandemPalette.surface,
            )
        }
        return
    }
    PageColumn {
        SampleBanner()
        Heading("Transaction details", "Record a change to the budget.")
        TextFieldCard(
            "Amount",
            state.transactionAmount,
            { state.transactionAmount = it },
            TextFieldOptions(placeholder = "For example, ${state.formatAmount(1_250)}"),
        )
        ReadOnlyField("Type", state.transactionType, leadingVector = Lucide.Receipt) {
            navigation.open(AppRoute.CHOOSE_TRANSACTION_TYPE)
        }
        ReadOnlyField("Account", state.transactionAccount, "A") { navigation.open(AppRoute.CHOOSE_ACCOUNT) }
        ReadOnlyField("Payee", state.transactionPayee, "P") { navigation.open(AppRoute.CHOOSE_PAYEE) }
        ReadOnlyField("Category", state.transactionCategory, "C") { navigation.open(AppRoute.CHOOSE_CATEGORY) }
        ReadOnlyField("Date", state.transactionDate, "▣") { navigation.open(AppRoute.CHOOSE_DATE) }
        TextFieldCard(
            "Notes",
            state.transactionNotes,
            { state.transactionNotes = it },
            TextFieldOptions(placeholder = "Add a note (optional)", singleLine = false),
        )
        PreviewNote(
            if (isNew) {
                "New transactions aren’t available here yet. This sample form doesn’t save or sync changes."
            } else {
                "This sample form isn’t connected. Changes won’t be saved or synced."
            },
        )
        PrimaryButton("Review save status") { navigation.open(AppRoute.SAVE_RESULT) }
    }
}
