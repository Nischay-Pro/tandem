package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.inputs.MAX_TRANSACTION_AMOUNT_MINOR_UNITS
import app.tandemforactual.ui.design.inputs.amountDigitExceedsLimit
import app.tandemforactual.ui.design.inputs.appendAmountDigit
import app.tandemforactual.ui.design.inputs.calculateAmountMinorUnits
import app.tandemforactual.ui.design.inputs.removeAmountDigit
import app.tandemforactual.ui.feature.budget.suggestedHoldAmount

/** Visibility and calculator state of the hold-for-next-month sheet. */
@Stable
internal class HoldSheetState(
    showDialog: MutableState<Boolean>,
    mounted: MutableState<Boolean>,
    amountMinor: MutableState<Long>,
    keypadVisible: MutableState<Boolean>,
    accumulator: MutableState<Long?>,
    operator: MutableState<String?>,
    enterNewOperand: MutableState<Boolean>,
    message: MutableState<String?>,
) {
    var showDialog by showDialog
    var mounted by mounted
    var amountMinor by amountMinor
    var keypadVisible by keypadVisible
    var accumulator by accumulator
    var operator by operator
    var enterNewOperand by enterNewOperand
    var message by message

    fun open(readyToAssignMinor: Long) {
        amountMinor = suggestedHoldAmount(readyToAssignMinor)
        accumulator = null
        operator = null
        enterNewOperand = false
        message = null
        keypadVisible = true
        showDialog = true
        mounted = true
    }

    private fun setAmount(value: Long) {
        amountMinor =
            (if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value))
                .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
    }

    fun enterDigit(
        state: AppState,
        digit: Int,
    ) {
        val current = if (enterNewOperand) 0L else amountMinor
        val exceededLimit = amountDigitExceedsLimit(current, digit)
        setAmount(appendAmountDigit(current, digit))
        message =
            if (exceededLimit) {
                "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
            } else {
                null
            }
        enterNewOperand = false
    }

    fun deleteDigit() {
        val current = if (enterNewOperand) 0L else amountMinor
        setAmount(removeAmountDigit(current))
        message = null
        enterNewOperand = false
    }

    fun chooseOperator(
        state: AppState,
        operator: String,
    ) {
        val accumulator = this.accumulator
        val pending = this.operator
        if (accumulator != null && pending != null && !enterNewOperand) {
            val result =
                calculateAmountMinorUnits(
                    accumulator,
                    amountMinor,
                    pending,
                    state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                )
            if (result == null) {
                message =
                    if (pending == "÷" && amountMinor == 0L) "Can’t divide by zero." else "Amount is too large."
                return
            }
            setAmount(result)
            this.accumulator = kotlin.math.abs(result)
        } else if (accumulator == null) {
            this.accumulator = amountMinor
        }
        message = null
        this.operator = operator
        enterNewOperand = true
    }

    fun calculate(state: AppState) {
        val accumulator = this.accumulator
        val operator = this.operator
        if (accumulator != null && operator != null) {
            val result =
                if (enterNewOperand) {
                    accumulator
                } else {
                    calculateAmountMinorUnits(
                        accumulator,
                        amountMinor,
                        operator,
                        state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                    )
                }
            if (result == null) {
                message =
                    if (operator == "÷" && amountMinor == 0L) "Can’t divide by zero." else "Amount is too large."
                return
            }
            setAmount(result)
        }
        message = null
        this.accumulator = null
        this.operator = null
        enterNewOperand = true
    }
}

@Composable
internal fun rememberHoldSheetState(
    budgetId: String,
    monthId: String,
    readyToAssignMinor: Long,
): HoldSheetState {
    val showDialog = rememberSaveable(budgetId, monthId) { mutableStateOf(false) }
    val mounted = rememberSaveable(budgetId, monthId) { mutableStateOf(false) }
    val amountMinor = rememberSaveable(budgetId, monthId) { mutableStateOf(readyToAssignMinor.coerceAtLeast(0)) }
    val keypadVisible = rememberSaveable(budgetId, monthId) { mutableStateOf(false) }
    val accumulator = rememberSaveable(budgetId, monthId) { mutableStateOf<Long?>(null) }
    val operator = rememberSaveable(budgetId, monthId) { mutableStateOf<String?>(null) }
    val enterNewOperand = rememberSaveable(budgetId, monthId) { mutableStateOf(false) }
    val message = rememberSaveable(budgetId, monthId) { mutableStateOf<String?>(null) }
    return remember(showDialog, mounted, amountMinor, keypadVisible, accumulator, operator, enterNewOperand, message) {
        HoldSheetState(showDialog, mounted, amountMinor, keypadVisible, accumulator, operator, enterNewOperand, message)
    }
}
