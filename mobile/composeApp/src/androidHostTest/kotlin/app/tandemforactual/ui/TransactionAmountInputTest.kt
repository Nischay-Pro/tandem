package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun longAmountsSaturateAndDigitsAreValidated() {
        assertEquals(Long.MAX_VALUE, appendAmountDigit(Long.MAX_VALUE, 9))
        assertEquals(-11L, removeAmountDigit(-112L))
        assertEquals(0L, removeAmountDigit(9L))
    }
}
