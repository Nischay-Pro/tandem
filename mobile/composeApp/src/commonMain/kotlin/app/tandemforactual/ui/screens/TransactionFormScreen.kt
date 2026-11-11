@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.AmountConfirmationDialog
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DestructiveConfirmationDialog
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.FutureTransactionDateConfirmationDialog
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.model.stringValue
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
    liveAccounts: List<LiveAccountSummary>,
    onSaveTransaction: (String?, (Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    onCreateTransaction: (String?) -> Unit,
) {
    if (state.readOnlyMode) {
        PageColumn {
            Heading("Read-only mode")
            BodyText("Turn it off in Device protection to edit this transaction.")
        }
        return
    }
    val isNew = route == AppRoute.NEW_TRANSACTION
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    var showAccountPicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    if (state.selectedBudgetId.isNotBlank()) {
        if (isNew) {
            var keypadVisible by rememberSaveable { mutableStateOf(true) }
            var calculatorAccumulator by rememberSaveable { mutableStateOf<Long?>(null) }
            var calculatorOperator by rememberSaveable { mutableStateOf<String?>(null) }
            var enterNewOperand by rememberSaveable { mutableStateOf(false) }
            var amountEntryMessage by rememberSaveable { mutableStateOf<String?>(null) }
            var confirmAmount by rememberSaveable { mutableStateOf(false) }
            var confirmFutureDate by rememberSaveable { mutableStateOf(false) }
            var futureDateConfirmationTarget by rememberSaveable { mutableStateOf("") }
            var confirmedFutureDateForAmount by rememberSaveable { mutableStateOf("") }
            var notesFocused by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            val routeScrollState = LocalRouteScrollState.current
            val compactSave by remember(routeScrollState) {
                derivedStateOf { (routeScrollState?.value ?: 0) > 72 }
            }

            val parsedAmountMinorUnits = state.parseAmountMinorUnits(state.transactionAmount)
            val amountMinorUnits = transactionAmountInputMagnitude(parsedAmountMinorUnits)
            val amountInputErrorMessage =
                when {
                    state.transactionAmount.isBlank() -> null
                    parsedAmountMinorUnits == null -> transactionAmountInputError(state.transactionAmount, state.budgetDisplaySettings)
                    parsedAmountMinorUnits < 0 -> "Enter a positive amount."
                    else -> null
                }
            val budgetAmountPreview =
                parsedAmountMinorUnits
                    ?.takeIf {
                        it > 0 &&
                            transactionAmountNeedsFormatConfirmation(
                                state.transactionAmount,
                                it,
                                state.budgetDisplaySettings,
                            )
                    }?.let(state::formatCurrency)
            val transferDestinationAccountId =
                remember(state.newTransactionPayeeId, mobileSession.payeeRecords) {
                    mobileSession.payeeRecords
                        .firstOrNull { it.recordId == state.newTransactionPayeeId }
                        ?.recordBody()
                        ?.stringValue("transfer_acct")
                }
            val transactionTargetIsValid =
                if (state.transactionType == "Transfer") {
                    isValidTransferDestination(state.newTransactionAccountId, transferDestinationAccountId)
                } else {
                    state.transactionCategory.isNotBlank()
                }

            fun setAmount(minorUnits: Long) {
                val magnitude = transactionAmountInputMagnitude(minorUnits)
                state.transactionAmount = state.formatAmount(magnitude)
            }

            fun enterDigit(digit: Int) {
                val current =
                    if (enterNewOperand) {
                        0L
                    } else {
                        transactionAmountInputMagnitude(state.parseAmountMinorUnits(state.transactionAmount))
                    }
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
                val currentAmount =
                    transactionAmountInputMagnitude(state.parseAmountMinorUnits(state.transactionAmount))
                if (previous != null && pending != null && !enterNewOperand) {
                    val result =
                        calculateAmountMinorUnits(
                            previous,
                            currentAmount,
                            pending,
                            state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                        )
                    if (result == null) {
                        amountEntryMessage =
                            if (pending == "÷" && currentAmount == 0L) {
                                "Can’t divide by zero."
                            } else {
                                "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
                            }
                        return
                    }
                    setAmount(result)
                    calculatorAccumulator = kotlin.math.abs(result)
                } else if (previous == null) {
                    calculatorAccumulator = currentAmount
                }
                amountEntryMessage = null
                calculatorOperator = operator
                enterNewOperand = true
            }

            fun calculate() {
                val previous = calculatorAccumulator
                val operator = calculatorOperator
                if (previous != null && operator != null) {
                    val currentAmount =
                        transactionAmountInputMagnitude(state.parseAmountMinorUnits(state.transactionAmount))
                    val result =
                        if (enterNewOperand) {
                            previous
                        } else {
                            calculateAmountMinorUnits(
                                previous,
                                currentAmount,
                                operator,
                                state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                            )
                        }
                    if (result == null) {
                        amountEntryMessage =
                            if (operator == "÷" && currentAmount == 0L) {
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

            fun dismissKeypad() {
                keypadVisible = false
                focusManager.clearFocus()
                softwareKeyboard?.hide()
            }

            fun continueCreateTransaction(confirmedFutureDate: String?) {
                confirmedFutureDateForAmount = confirmedFutureDate.orEmpty()
                if (budgetAmountPreview != null) {
                    confirmAmount = true
                } else {
                    onCreateTransaction(confirmedFutureDate)
                }
            }

            fun requestCreateTransaction() {
                dismissKeypad()
                val dateValidation = validateTransactionDate(state.transactionDate, platformTodayIsoDate())
                when {
                    dateValidation.status == TransactionDateValidationStatus.CONFIRMATION_REQUIRED -> {
                        futureDateConfirmationTarget = dateValidation.date.orEmpty()
                        confirmFutureDate = true
                    }
                    dateValidation.status != TransactionDateValidationStatus.VALID -> onCreateTransaction(null)
                    else -> continueCreateTransaction(null)
                }
            }

            LaunchedEffect(keypadVisible) {
                if (keypadVisible) {
                    softwareKeyboard?.hide()
                }
            }

            Box(Modifier.fillMaxSize()) {
                PageColumn(
                    verticalSpacing = 14.dp,
                    modifier =
                        Modifier
                            .imePadding()
                            .clickable(
                                enabled = keypadVisible || notesFocused,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = ::dismissKeypad,
                            ),
                ) {
                    TransactionAmountField(
                        amount = state.formatAmount(amountMinorUnits),
                        amountMinorUnits = amountMinorUnits,
                        transactionType = state.transactionType,
                        showCursor = keypadVisible,
                        onShowKeypad = { keypadVisible = true },
                    )
                    budgetAmountPreview?.let { BodyText("Budget format: $it") }
                    val transactionErrorMessage = amountInputErrorMessage ?: amountEntryMessage ?: mobileSession.transactionWriteMessage
                    AnimatedReveal(visible = transactionErrorMessage != null) {
                        InlineMessage(transactionErrorMessage.orEmpty(), InlineMessageKind.ERROR)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TransactionTypePicker(
                            state.transactionType,
                            allowTransfer = true,
                            onBeforeOpen = ::dismissKeypad,
                            onSelect = { type ->
                                if (type == "Transfer") {
                                    state.transactionPayee = ""
                                    state.newTransactionPayeeId = ""
                                    state.transactionCategory = ""
                                    state.newTransactionCategoryId = ""
                                } else if (state.transactionType == "Transfer") {
                                    state.transactionPayee = ""
                                    state.newTransactionPayeeId = ""
                                }
                                state.transactionType = type
                            },
                        )
                    }
                    SectionCard(title = "") {
                        if (state.transactionType == "Transfer") {
                            TransactionPickerRow(state.transactionAccount, "Choose source account", Lucide.WalletCards) {
                                dismissKeypad()
                                showAccountPicker = true
                            }
                            DividerLine()
                            TransactionPickerRow(state.transactionPayee, "Choose destination account", Lucide.WalletCards) {
                                dismissKeypad()
                                navigation.open(AppRoute.CHOOSE_PAYEE)
                            }
                        } else {
                            TransactionPickerRow(state.transactionPayee, "Choose payee", Lucide.Receipt) {
                                dismissKeypad()
                                navigation.open(AppRoute.CHOOSE_PAYEE)
                            }
                            DividerLine()
                            TransactionPickerRow(state.transactionCategory, "Choose category", Lucide.Tags) {
                                dismissKeypad()
                                navigation.open(AppRoute.CHOOSE_CATEGORY)
                            }
                            DividerLine()
                            TransactionPickerRow(state.transactionAccount, "Choose account", Lucide.WalletCards) {
                                dismissKeypad()
                                showAccountPicker = true
                            }
                        }
                        DividerLine()
                        TransactionPickerRow(
                            displayTransactionDate(state.transactionDate),
                            "Choose date",
                            Lucide.CalendarDays,
                        ) {
                            dismissKeypad()
                            showDatePicker = true
                        }
                    }
                    TextFieldCard(
                        "Notes",
                        state.transactionNotes,
                        { state.transactionNotes = it },
                        TextFieldOptions(
                            placeholder = "Add a note",
                            singleLine = false,
                            onFocusedChange = { focused ->
                                notesFocused = focused
                                if (focused) keypadVisible = false
                            },
                        ),
                    )
                    Spacer(Modifier.height(if (keypadVisible) AMOUNT_KEYPAD_HEIGHT + 68.dp else 76.dp))
                }
                if (keypadVisible) {
                    TransactionAmountKeypad(
                        onDigit = ::enterDigit,
                        onDelete = {
                            val current =
                                if (enterNewOperand) {
                                    0L
                                } else {
                                    transactionAmountInputMagnitude(state.parseAmountMinorUnits(state.transactionAmount))
                                }
                            setAmount(removeAmountDigit(current))
                            amountEntryMessage = null
                            enterNewOperand = false
                        },
                        onOperator = ::chooseOperator,
                        onEquals = ::calculate,
                        onDone = {
                            dismissKeypad()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .padding(bottom = if (keypadVisible) AMOUNT_KEYPAD_HEIGHT else 12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(tandemPalette.surface).padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecondaryButton("Cancel", Modifier.width(112.dp)) {
                            dismissKeypad()
                            navigation.back()
                        }
                        Spacer(Modifier.weight(1f))
                        FloatingTransactionSave(
                            compact = compactSave,
                            enabled =
                                !mobileSession.isSavingTransaction &&
                                    state.newTransactionAccountId.isNotBlank() &&
                                    state.transactionPayee.isNotBlank() &&
                                    transactionTargetIsValid &&
                                    parsedAmountMinorUnits != null &&
                                    parsedAmountMinorUnits > 0 &&
                                    amountMinorUnits > 0,
                            onClick = ::requestCreateTransaction,
                        )
                    }
                }
            }
            TransactionAccountPickerDialog(
                visible = showAccountPicker,
                liveAccounts,
                state.newTransactionAccountId,
                state.transactionAccount,
                live = true,
                onSelect = { account ->
                    state.transactionAccount = account.label
                    state.newTransactionAccountId = account.id
                    showAccountPicker = false
                },
                onDismiss = { showAccountPicker = false },
            )
            TransactionDatePickerDialog(
                visible = showDatePicker,
                selectedDate = state.transactionDate,
                onSelect = {
                    state.transactionDate = it
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false },
            )
            AmountConfirmationDialog(
                visible = confirmAmount,
                formattedAmount = budgetAmountPreview.orEmpty(),
                onDismissRequest = {
                    confirmAmount = false
                    confirmedFutureDateForAmount = ""
                },
                onConfirm = {
                    confirmAmount = false
                    val confirmedDate = confirmedFutureDateForAmount.takeIf(String::isNotBlank)
                    confirmedFutureDateForAmount = ""
                    onCreateTransaction(confirmedDate)
                },
            )
            FutureTransactionDateConfirmationDialog(
                visible = confirmFutureDate,
                formattedDate = displayTransactionDate(futureDateConfirmationTarget),
                onDismissRequest = {
                    confirmFutureDate = false
                    futureDateConfirmationTarget = ""
                },
                onConfirm = {
                    val confirmedDate = futureDateConfirmationTarget
                    confirmFutureDate = false
                    futureDateConfirmationTarget = ""
                    continueCreateTransaction(confirmedDate)
                },
            )
            return
        }
        var originalAmountMinor by remember(state.selectedTransactionRecordId) { mutableStateOf(state.selectedTransactionAmountMinor) }
        var originalTransactionType by
            remember(state.selectedTransactionRecordId) {
                mutableStateOf(if ((state.selectedTransactionAmountMinor ?: 0L) < 0) "Spending" else "Income")
            }
        var originalNotes by remember(state.selectedTransactionRecordId) { mutableStateOf(state.selectedTransactionNotes) }
        var savedFeedback by remember(state.selectedTransactionRecordId) { mutableStateOf(false) }
        var confirmDelete by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf(false) }
        var confirmAmount by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf(false) }
        var confirmFutureDate by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf(false) }
        var futureDateConfirmationTarget by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf("") }
        var confirmedFutureDateForAmount by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf("") }
        var keypadVisible by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf(false) }
        var notesFocused by remember { mutableStateOf(false) }
        var calculatorAccumulator by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf<Long?>(null) }
        var calculatorOperator by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf<String?>(null) }
        var enterNewOperand by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf(false) }
        var amountEntryMessage by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf<String?>(null) }
        val parsedAmount = state.parseAmountMinorUnits(state.transactionAmount)
        val amountMinorUnits =
            transactionAmountInputMagnitude(
                parsedAmount ?: state.selectedTransactionAmountMinor,
            )
        val amountInputErrorMessage =
            when {
                state.transactionAmount.isBlank() -> null
                parsedAmount == null -> transactionAmountInputError(state.transactionAmount, state.budgetDisplaySettings)
                parsedAmount < 0 -> "Enter a positive amount."
                else -> null
            }
        val budgetAmountPreview =
            parsedAmount
                ?.takeIf {
                    it > 0 &&
                        transactionAmountNeedsFormatConfirmation(
                            state.transactionAmount,
                            it,
                            state.budgetDisplaySettings,
                        )
                }?.let(state::formatCurrency)
        val focusManager = LocalFocusManager.current
        val routeScrollState = LocalRouteScrollState.current
        val compactSave by remember(routeScrollState) {
            derivedStateOf { (routeScrollState?.value ?: 0) > 72 }
        }

        fun setEditAmount(minorUnits: Long) {
            val magnitude = transactionAmountInputMagnitude(minorUnits)
            state.transactionAmount = state.formatAmount(magnitude)
        }

        fun enterEditDigit(digit: Int) {
            val current =
                if (enterNewOperand) {
                    0L
                } else {
                    transactionAmountInputMagnitude(
                        state.parseAmountMinorUnits(state.transactionAmount) ?: state.selectedTransactionAmountMinor,
                    )
                }
            setEditAmount(appendAmountDigit(current, digit))
            amountEntryMessage = null
            enterNewOperand = false
        }

        fun chooseEditOperator(operator: String) {
            val previous = calculatorAccumulator
            val pending = calculatorOperator
            val currentAmount =
                transactionAmountInputMagnitude(
                    state.parseAmountMinorUnits(state.transactionAmount) ?: state.selectedTransactionAmountMinor,
                )
            if (previous != null && pending != null && !enterNewOperand) {
                val result =
                    calculateAmountMinorUnits(
                        previous,
                        currentAmount,
                        pending,
                        state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                    ) ?: return
                setEditAmount(result)
                calculatorAccumulator = kotlin.math.abs(result)
            } else if (previous == null) {
                calculatorAccumulator = currentAmount
            }
            calculatorOperator = operator
            enterNewOperand = true
        }

        fun calculateEditAmount() {
            val previous = calculatorAccumulator
            val operator = calculatorOperator
            if (previous != null && operator != null) {
                val currentAmount =
                    transactionAmountInputMagnitude(
                        state.parseAmountMinorUnits(state.transactionAmount) ?: state.selectedTransactionAmountMinor,
                    )
                val result =
                    if (enterNewOperand) {
                        previous
                    } else {
                        calculateAmountMinorUnits(
                            previous,
                            currentAmount,
                            operator,
                            state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                        )
                    }
                if (result != null) setEditAmount(result)
            }
            calculatorAccumulator = null
            calculatorOperator = null
            enterNewOperand = true
        }

        fun dismissEditKeypad() {
            keypadVisible = false
            focusManager.clearFocus()
            softwareKeyboard?.hide()
        }

        fun saveEditTransaction(confirmedFutureDate: String? = null) {
            onSaveTransaction(confirmedFutureDate) { saved ->
                if (saved) {
                    originalAmountMinor = state.selectedTransactionAmountMinor
                    originalTransactionType = state.transactionType
                    originalNotes = state.selectedTransactionNotes
                    savedFeedback = true
                }
            }
        }

        fun continueEditTransactionSave(confirmedFutureDate: String?) {
            confirmedFutureDateForAmount = confirmedFutureDate.orEmpty()
            if (budgetAmountPreview != null) {
                confirmAmount = true
            } else {
                saveEditTransaction(confirmedFutureDate)
            }
        }

        fun requestEditSave() {
            dismissEditKeypad()
            val dateValidation = validateTransactionDate(state.transactionDate, platformTodayIsoDate())
            when {
                dateValidation.status == TransactionDateValidationStatus.CONFIRMATION_REQUIRED -> {
                    futureDateConfirmationTarget = dateValidation.date.orEmpty()
                    confirmFutureDate = true
                }
                dateValidation.status != TransactionDateValidationStatus.VALID -> saveEditTransaction()
                else -> continueEditTransactionSave(null)
            }
        }

        val baselineAmount = originalAmountMinor?.let { kotlin.math.abs(it) }
        val hasChanges =
            parsedAmount != baselineAmount ||
                state.transactionType != originalTransactionType ||
                state.transactionNotes != originalNotes ||
                state.newTransactionAccountId != state.selectedTransactionAccountId ||
                state.newTransactionPayeeId != state.selectedTransactionPayeeId ||
                state.newTransactionCategoryId != state.selectedTransactionCategoryId ||
                state.transactionDate != state.selectedTransactionDate
        LaunchedEffect(keypadVisible) {
            if (keypadVisible) {
                softwareKeyboard?.hide()
            }
        }
        LaunchedEffect(savedFeedback) {
            if (savedFeedback) {
                delay(900)
                savedFeedback = false
            }
        }
        Box(Modifier.fillMaxSize()) {
            PageColumn(
                verticalSpacing = 14.dp,
                modifier =
                    Modifier
                        .imePadding()
                        .clickable(
                            enabled = keypadVisible || notesFocused,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::dismissEditKeypad,
                        ),
            ) {
                TransactionAmountField(
                    amount = state.formatAmount(amountMinorUnits),
                    amountMinorUnits = amountMinorUnits,
                    transactionType = state.transactionType,
                    showCursor = keypadVisible,
                    onShowKeypad = { keypadVisible = true },
                )
                budgetAmountPreview?.let { BodyText("Budget format: $it") }
                val transactionErrorMessage = amountInputErrorMessage ?: amountEntryMessage ?: mobileSession.transactionWriteMessage
                AnimatedReveal(visible = transactionErrorMessage != null) {
                    InlineMessage(transactionErrorMessage.orEmpty(), InlineMessageKind.ERROR)
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TransactionTypePicker(
                        state.transactionType,
                        onBeforeOpen = ::dismissEditKeypad,
                        onSelect = { state.transactionType = it },
                    )
                }
                SectionCard(title = "") {
                    TransactionPickerRow(state.transactionPayee, "Choose payee", Lucide.Receipt) {
                        dismissEditKeypad()
                        navigation.open(AppRoute.CHOOSE_PAYEE)
                    }
                    DividerLine()
                    TransactionPickerRow(state.transactionCategory, "Choose category", Lucide.Tags) {
                        dismissEditKeypad()
                        navigation.open(AppRoute.CHOOSE_CATEGORY)
                    }
                    DividerLine()
                    TransactionPickerRow(state.transactionAccount, "Choose account", Lucide.WalletCards) {
                        dismissEditKeypad()
                        showAccountPicker = true
                    }
                    DividerLine()
                    TransactionPickerRow(displayTransactionDate(state.transactionDate), "Choose date", Lucide.CalendarDays) {
                        dismissEditKeypad()
                        showDatePicker = true
                    }
                }
                TextFieldCard(
                    "Notes",
                    state.transactionNotes,
                    { state.transactionNotes = it },
                    TextFieldOptions(
                        placeholder = "Add a note",
                        singleLine = false,
                        onFocusedChange = { focused ->
                            notesFocused = focused
                            if (focused) keypadVisible = false
                        },
                    ),
                )
                Spacer(Modifier.height(if (keypadVisible) AMOUNT_KEYPAD_HEIGHT + 68.dp else 76.dp))
            }
            if (keypadVisible) {
                TransactionAmountKeypad(
                    onDigit = ::enterEditDigit,
                    onDelete = {
                        val current =
                            if (enterNewOperand) {
                                0L
                            } else {
                                transactionAmountInputMagnitude(
                                    state.parseAmountMinorUnits(state.transactionAmount)
                                        ?: state.selectedTransactionAmountMinor,
                                )
                            }
                        setEditAmount(removeAmountDigit(current))
                        amountEntryMessage = null
                        enterNewOperand = false
                    },
                    onOperator = ::chooseEditOperator,
                    onEquals = ::calculateEditAmount,
                    onDone = ::dismissEditKeypad,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .padding(bottom = if (keypadVisible) AMOUNT_KEYPAD_HEIGHT else 12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().background(tandemPalette.surface).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatingTransactionDeleteAction(
                        enabled = !mobileSession.isSavingTransaction,
                        onClick = {
                            dismissEditKeypad()
                            confirmDelete = true
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(
                        visible = hasChanges || savedFeedback,
                        enter = tandemFadeEnter(tandemReduceMotion),
                        exit = tandemFadeExit(tandemReduceMotion),
                    ) {
                        FloatingTransactionSave(
                            compact = compactSave || savedFeedback,
                            enabled = hasChanges && !mobileSession.isSavingTransaction && parsedAmount != null && parsedAmount > 0,
                            saved = savedFeedback,
                            onClick = ::requestEditSave,
                        )
                    }
                }
            }
        }
        TransactionAccountPickerDialog(
            visible = showAccountPicker,
            liveAccounts,
            state.newTransactionAccountId,
            state.transactionAccount,
            live = true,
            onSelect = { account ->
                state.transactionAccount = account.label
                state.newTransactionAccountId = account.id
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false },
        )
        TransactionDatePickerDialog(
            visible = showDatePicker,
            selectedDate = state.transactionDate,
            onSelect = {
                state.transactionDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
        AmountConfirmationDialog(
            visible = confirmAmount,
            formattedAmount = budgetAmountPreview.orEmpty(),
            onDismissRequest = {
                confirmAmount = false
                confirmedFutureDateForAmount = ""
            },
            onConfirm = {
                confirmAmount = false
                val confirmedDate = confirmedFutureDateForAmount.takeIf(String::isNotBlank)
                confirmedFutureDateForAmount = ""
                saveEditTransaction(confirmedDate)
            },
        )
        FutureTransactionDateConfirmationDialog(
            visible = confirmFutureDate,
            formattedDate = displayTransactionDate(futureDateConfirmationTarget),
            onDismissRequest = {
                confirmFutureDate = false
                futureDateConfirmationTarget = ""
            },
            onConfirm = {
                val confirmedDate = futureDateConfirmationTarget
                confirmFutureDate = false
                futureDateConfirmationTarget = ""
                continueEditTransactionSave(confirmedDate)
            },
        )
        if (confirmDelete) {
            DestructiveConfirmationDialog(
                title = "Delete transaction?",
                message = "It’ll be removed from this device. The removal will sync later.",
                confirmLabel = "Delete",
                dismissLabel = "Keep",
                enabled = !mobileSession.isSavingTransaction,
                onDismissRequest = { confirmDelete = false },
                onConfirm = {
                    onDeleteTransaction { deleted ->
                        confirmDelete = false
                        if (deleted) navigation.backToList()
                    }
                },
            )
        }
        return
    }
    PageColumn {
        EmptyState(
            "Choose a budget",
            "Open an Actual budget before adding or editing a transaction.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
    }
}
