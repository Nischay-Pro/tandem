package app.tandemforactual.ui.core.session.transactions

import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionEditSignTest {
    @Test
    fun changingATransactionIntoATransferSendsMoneyToTheChosenAccount() {
        assertEquals(-500L, signedEditAmount("Transfer", 500, originalAmountMinor = 2_000, wasTransfer = false))
        assertEquals(-500L, signedEditAmount("Transfer", 500, originalAmountMinor = -2_000, wasTransfer = false))
    }

    @Test
    fun anExistingTransferKeepsItsDirection() {
        assertEquals(500L, signedEditAmount("Transfer", 500, originalAmountMinor = 2_000, wasTransfer = true))
        assertEquals(-500L, signedEditAmount("Transfer", 500, originalAmountMinor = -2_000, wasTransfer = true))
    }

    @Test
    fun incomeAndSpendingTakeTheirOwnSign() {
        assertEquals(500L, signedEditAmount("Income", 500, originalAmountMinor = -2_000, wasTransfer = false))
        assertEquals(-500L, signedEditAmount("Spending", 500, originalAmountMinor = 2_000, wasTransfer = false))
    }
}
