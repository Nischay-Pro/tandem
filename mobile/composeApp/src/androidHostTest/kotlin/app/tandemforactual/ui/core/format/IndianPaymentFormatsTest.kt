package app.tandemforactual.ui.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IndianPaymentFormatsTest {
    private fun read(name: String): SharedTransactionText {
        val example = BUILT_IN_PAYMENT_FORMATS.single { it.name == name }.examples.first()
        return assertNotNull(parseSharedTransactionText(example), name)
    }

    private fun SharedTransactionText.fields() = listOf(amountMinor, isIncome, payeeHint, accountDigits, dateText, reference)

    @Test
    fun bankMessagesGiveAmountDirectionPayeeAccountDateAndReference() {
        assertEquals(listOf(25_000L, false, "SWIGGY", "1234", "28/11/26", "433212345678"), read("Bank UPI sent").fields())
        assertEquals(listOf(12_000L, false, "ZOMATO", "1234", "28Nov26", "433298765432"), read("Bank UPI transfer").fields())
        assertEquals(listOf(49_900L, false, "AMAZON PAY", "234", "28-Nov-26", "433211112222"), read("Bank account debit").fields())
        assertEquals(listOf(500_000L, true, "RAVI KUMAR", "1234", "28-11-2026", "433200001111"), read("Bank account UPI credit").fields())
    }

    @Test
    fun aCreditOnTheSameWordingIsMoneyIn() {
        val credit = BUILT_IN_PAYMENT_FORMATS.single { it.name == "Bank UPI transfer" }.examples[1]
        val shared = assertNotNull(parseSharedTransactionText(credit))
        assertEquals(listOf(250_000L, true, "RAVI KUMAR"), listOf(shared.amountMinor, shared.isIncome, shared.payeeHint))
    }

    @Test
    fun upiAppNotificationsGiveAmountAndPayee() {
        assertEquals(listOf(14_900L, false, "Swiggy"), read("UPI app payment").fields().take(3))
        assertEquals(listOf(200_000L, true, "Ravi Kumar"), read("UPI app received").fields().take(3))
    }
}
