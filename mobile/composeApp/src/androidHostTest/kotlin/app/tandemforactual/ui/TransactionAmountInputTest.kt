package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionAmountInputTest {
    @Test
    fun digitsShiftTheDecimalPlaceAutomatically() {
        var amount = 0L
        amount = appendAmountDigit(amount, 1)
        assertEquals(1L, amount)
        amount = appendAmountDigit(amount, 1)
        assertEquals(11L, amount)
        amount = appendAmountDigit(amount, 2)
        assertEquals(112L, amount)
        assertEquals(11L, removeAmountDigit(amount))
    }

    @Test
    fun deletingToZeroThenTypingStartsFromZeroAgain() {
        val cleared = removeAmountDigit(1L)

        assertEquals(0L, cleared)
        assertEquals(1L, appendAmountDigit(cleared, 1))
        assertEquals(112L, appendAmountDigit(appendAmountDigit(appendAmountDigit(cleared, 1), 1), 2))
    }

    @Test
    fun transactionEntryAlwaysDisplaysAnUnsignedMagnitude() {
        assertEquals(112L, transactionAmountInputMagnitude(-112L))
        assertEquals(0L, transactionAmountInputMagnitude(null))
        assertEquals(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, transactionAmountInputMagnitude(Long.MIN_VALUE))
    }

    @Test
    fun keypadCalculatorUsesMinorUnitsAndRejectsDivisionByZero() {
        assertEquals(1_125L, calculateAmountMinorUnits(1_000L, 125L, "+"))
        assertEquals(875L, calculateAmountMinorUnits(1_000L, 125L, "−"))
        assertEquals(1_250L, calculateAmountMinorUnits(500L, 250L, "×"))
        assertEquals(200L, calculateAmountMinorUnits(500L, 250L, "÷"))
        assertNull(calculateAmountMinorUnits(500L, 0L, "÷"))
    }

    @Test
    fun keypadCalculatorUsesTheBudgetsCurrencyScale() {
        assertEquals(10L, calculateAmountMinorUnits(5L, 2L, "×", fractionDigits = 0))
        assertEquals(3L, calculateAmountMinorUnits(5L, 2L, "÷", fractionDigits = 0))
        assertEquals(1_000L, calculateAmountMinorUnits(500L, 200L, "×", fractionDigits = 2))
        assertEquals(250L, calculateAmountMinorUnits(500L, 200L, "÷", fractionDigits = 2))
        assertEquals(10_000L, calculateAmountMinorUnits(5_000L, 2_000L, "×", fractionDigits = 3))
        assertEquals(2_500L, calculateAmountMinorUnits(5_000L, 2_000L, "÷", fractionDigits = 3))
        assertNull(calculateAmountMinorUnits(5_000L, 2_000L, "×", fractionDigits = 10))
    }

    @Test
    fun digitEntryStopsAtTheServerAmountLimit() {
        assertEquals(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, appendAmountDigit(100_000_000L, 0))
        assertEquals(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, appendAmountDigit(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, 9))
        assertEquals(-MAX_TRANSACTION_AMOUNT_MINOR_UNITS, appendAmountDigit(-MAX_TRANSACTION_AMOUNT_MINOR_UNITS, 9))
        assertTrue(amountDigitExceedsLimit(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, 0))
        assertNull(calculateAmountMinorUnits(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, 1, "+"))
        assertNull(calculateAmountMinorUnits(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, MAX_TRANSACTION_AMOUNT_MINOR_UNITS, "×"))
        assertEquals(-11L, removeAmountDigit(-112L))
        assertEquals(0L, removeAmountDigit(9L))
    }

    @Test
    fun transferRequiresARealDestinationDifferentFromTheSource() {
        assertTrue(isValidTransferDestination("checking", "savings"))
        assertTrue(!isValidTransferDestination("checking", "checking"))
        assertTrue(!isValidTransferDestination("checking", null))
        assertTrue(!isValidTransferDestination("", "savings"))
    }
}
