package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.inputs.MAX_TRANSACTION_AMOUNT_MINOR_UNITS
import app.tandemforactual.ui.design.inputs.amountDigitExceedsLimit
import app.tandemforactual.ui.design.inputs.appendAmountDigit
import app.tandemforactual.ui.design.inputs.appendAmountFractionDigit
import app.tandemforactual.ui.design.inputs.calculateAmountMinorUnits
import app.tandemforactual.ui.design.inputs.removeAmountDigit
import app.tandemforactual.ui.design.inputs.removeAmountFractionDigit
import app.tandemforactual.ui.design.inputs.startAmountFraction
import app.tandemforactual.ui.design.inputs.transactionAmountInputMagnitude
import app.tandemforactual.ui.design.templates.AmountKeypadActions

/** The amount keypad and its calculator, kept across recreation. */
internal class TransactionAmountEntryState(
    keypadVisible: Boolean,
    calculatorAccumulator: Long? = null,
    calculatorOperator: String? = null,
    enterNewOperand: Boolean = false,
    amountEntryMessage: String? = null,
    fractionDigitsTyped: Int? = null,
) {
    var keypadVisible by mutableStateOf(keypadVisible)
    var calculatorAccumulator by mutableStateOf(calculatorAccumulator)
    var calculatorOperator by mutableStateOf(calculatorOperator)
    var enterNewOperand by mutableStateOf(enterNewOperand)
    var amountEntryMessage by mutableStateOf(amountEntryMessage)
    var fractionDigitsTyped by mutableStateOf(fractionDigitsTyped)

    companion object {
        val Saver: Saver<TransactionAmountEntryState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.keypadVisible,
                        it.calculatorAccumulator,
                        it.calculatorOperator,
                        it.enterNewOperand,
                        it.amountEntryMessage,
                        it.fractionDigitsTyped,
                    )
                },
                restore = {
                    TransactionAmountEntryState(
                        keypadVisible = it[0] as Boolean,
                        calculatorAccumulator = it[1] as Long?,
                        calculatorOperator = it[2] as String?,
                        enterNewOperand = it[3] as Boolean,
                        amountEntryMessage = it[4] as String?,
                        fractionDigitsTyped = it[5] as Int?,
                    )
                },
            )
    }
}

@Composable
internal fun rememberTransactionAmountEntryState(
    vararg keys: Any?,
    keypadVisible: Boolean,
): TransactionAmountEntryState =
    rememberSaveable(*keys, saver = TransactionAmountEntryState.Saver) {
        TransactionAmountEntryState(keypadVisible)
    }

/** Hides the keypad, the focused field and the software keyboard. */
@Composable
internal fun dismissKeypadAction(entry: TransactionAmountEntryState): () -> Unit {
    val focusManager = LocalFocusManager.current
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    return {
        entry.keypadVisible = false
        entry.fractionDigitsTyped = null
        focusManager.clearFocus()
        softwareKeyboard?.hide()
    }
}

private fun setAmount(
    state: AppState,
    minorUnits: Long,
) {
    val magnitude = transactionAmountInputMagnitude(minorUnits)
    state.transactionAmount = state.formatAmount(magnitude)
}

private fun AppState.calculatorErrorMessage(
    operator: String,
    currentAmount: Long,
): String =
    if (operator == "÷" && currentAmount == 0L) {
        "Can’t divide by zero."
    } else {
        "Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
    }

internal fun TransactionAmountEntryState.enterDigit(
    state: AppState,
    digit: Int,
) {
    val current =
        if (enterNewOperand) {
            0L
        } else {
            transactionAmountInputMagnitude(state.parseAmountMinorUnits(state.transactionAmount))
        }
    if (enterFractionDigit(state, current, digit)) return
    val exceededLimit = amountDigitExceedsLimit(current, digit)
    setAmount(state, appendAmountDigit(current, digit))
    amountEntryMessage =
        if (exceededLimit) {
            "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
        } else {
            null
        }
    enterNewOperand = false
}

internal fun TransactionAmountEntryState.chooseOperator(
    state: AppState,
    operator: String,
) {
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
            amountEntryMessage = state.calculatorErrorMessage(pending, currentAmount)
            return
        }
        setAmount(state, result)
        calculatorAccumulator = kotlin.math.abs(result)
    } else if (previous == null) {
        calculatorAccumulator = currentAmount
    }
    amountEntryMessage = null
    calculatorOperator = operator
    fractionDigitsTyped = null
    enterNewOperand = true
}

internal fun TransactionAmountEntryState.calculate(state: AppState) {
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
            amountEntryMessage = state.calculatorErrorMessage(operator, currentAmount)
            return
        }
        setAmount(state, result)
    }
    amountEntryMessage = null
    calculatorAccumulator = null
    calculatorOperator = null
    fractionDigitsTyped = null
    enterNewOperand = true
}

internal fun TransactionAmountEntryState.deleteDigit(state: AppState) {
    if (enterNewOperand) {
        calculatorAccumulator = null
        calculatorOperator = null
    }
    val current = transactionAmountInputMagnitude(state.parseAmountMinorUnits(state.transactionAmount))
    setAmount(state, removeDigitOrFraction(state, current))
    amountEntryMessage = null
    enterNewOperand = false
}

private fun AppState.editAmountMagnitude(): Long =
    transactionAmountInputMagnitude(
        parseAmountMinorUnits(transactionAmount) ?: selectedTransactionAmountMinor,
    )

internal fun TransactionAmountEntryState.enterEditDigit(
    state: AppState,
    digit: Int,
) {
    val current = if (enterNewOperand) 0L else state.editAmountMagnitude()
    if (enterFractionDigit(state, current, digit)) return
    setAmount(state, appendAmountDigit(current, digit))
    amountEntryMessage = null
    enterNewOperand = false
}

internal fun TransactionAmountEntryState.chooseEditOperator(
    state: AppState,
    operator: String,
) {
    val previous = calculatorAccumulator
    val pending = calculatorOperator
    val currentAmount = state.editAmountMagnitude()
    if (previous != null && pending != null && !enterNewOperand) {
        val result =
            calculateAmountMinorUnits(
                previous,
                currentAmount,
                pending,
                state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
            ) ?: return
        setAmount(state, result)
        calculatorAccumulator = kotlin.math.abs(result)
    } else if (previous == null) {
        calculatorAccumulator = currentAmount
    }
    calculatorOperator = operator
    fractionDigitsTyped = null
    enterNewOperand = true
}

internal fun TransactionAmountEntryState.calculateEditAmount(state: AppState) {
    val previous = calculatorAccumulator
    val operator = calculatorOperator
    if (previous != null && operator != null) {
        val currentAmount = state.editAmountMagnitude()
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
        if (result != null) setAmount(state, result)
    }
    calculatorAccumulator = null
    calculatorOperator = null
    fractionDigitsTyped = null
    enterNewOperand = true
}

internal fun TransactionAmountEntryState.deleteEditDigit(state: AppState) {
    if (enterNewOperand) {
        calculatorAccumulator = null
        calculatorOperator = null
    }
    setAmount(state, removeDigitOrFraction(state, state.editAmountMagnitude()))
    amountEntryMessage = null
    enterNewOperand = false
}

private val AppState.amountFractionDigits: Int get() = budgetDisplaySettings?.currencyFractionDigits ?: 2

private fun TransactionAmountEntryState.enterFractionDigit(
    state: AppState,
    current: Long,
    digit: Int,
): Boolean {
    val typed = fractionDigitsTyped ?: return false
    if (typed < state.amountFractionDigits) {
        setAmount(state, appendAmountFractionDigit(current, digit, typed, state.amountFractionDigits))
        fractionDigitsTyped = typed + 1
    }
    amountEntryMessage = null
    enterNewOperand = false
    return true
}

private fun TransactionAmountEntryState.removeDigitOrFraction(
    state: AppState,
    current: Long,
): Long {
    val typed = fractionDigitsTyped ?: return removeAmountDigit(current)
    fractionDigitsTyped = (typed - 1).takeIf { it >= 0 }
    return removeAmountFractionDigit(current, typed, state.amountFractionDigits)
}

/** Typed digits become whole units and the next ones fill the fraction, as when typing an amount with a decimal point. */
internal fun TransactionAmountEntryState.enterDecimalSeparator(
    state: AppState,
    editing: Boolean,
) {
    if (state.amountFractionDigits == 0 || fractionDigitsTyped != null) return
    val current =
        when {
            enterNewOperand -> 0L
            editing -> state.editAmountMagnitude()
            else -> transactionAmountInputMagnitude(state.parseAmountMinorUnits(state.transactionAmount))
        }
    val whole = startAmountFraction(current, state.amountFractionDigits)
    if (whole == null) {
        amountEntryMessage = "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
        return
    }
    setAmount(state, whole)
    fractionDigitsTyped = 0
    amountEntryMessage = null
    enterNewOperand = false
}

/** The keypad's keys for a new or an edited transaction's amount. */
internal fun TransactionAmountEntryState.keypadActions(
    state: AppState,
    editing: Boolean,
    onDone: () -> Unit,
): AmountKeypadActions =
    AmountKeypadActions(
        onDigit = { if (editing) enterEditDigit(state, it) else enterDigit(state, it) },
        onDelete = { if (editing) deleteEditDigit(state) else deleteDigit(state) },
        onOperator = { if (editing) chooseEditOperator(state, it) else chooseOperator(state, it) },
        onEquals = { if (editing) calculateEditAmount(state) else calculate(state) },
        onDone = onDone,
        onDecimal = { enterDecimalSeparator(state, editing) },
    )
