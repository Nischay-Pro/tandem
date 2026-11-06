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
    fun keypadCalculatorUsesMinorUnitsAndRejectsDivisionByZero() {
        assertEquals(1_125L, calculateAmountMinorUnits(1_000L, 125L, "+"))
        assertEquals(875L, calculateAmountMinorUnits(1_000L, 125L, "−"))
        assertEquals(1_250L, calculateAmountMinorUnits(500L, 250L, "×"))
        assertEquals(200L, calculateAmountMinorUnits(500L, 250L, "÷"))
        assertNull(calculateAmountMinorUnits(500L, 0L, "÷"))
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
}
