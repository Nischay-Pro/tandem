package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptScanTest {
    @Test
    fun readsTheTotalMerchantAndDateFromAGroceryReceipt() {
        val receipt =
            parseReceiptText(
                listOf(
                    "WELCOME TO",
                    "FRESH MARKET",
                    "123 Main St, Springfield",
                    "Tel 555-0100",
                    "11/14/2026  10:42",
                    "Bananas            1.29",
                    "Milk 2L            3.49",
                    "SUBTOTAL           4.78",
                    "TAX                0.38",
                    "TOTAL              5.16",
                    "VISA ****1234      5.16",
                    "You saved          1.00",
                ),
                today = "2026-11-21",
            )
        assertEquals(ScannedReceipt(516, "Fresh Market", "2026-11-14"), receipt)
    }

    @Test
    fun readsTotalsOnTheNextLineAndEuropeanAmounts() {
        val receipt =
            parseReceiptText(
                listOf("Bäckerei Schmidt", "Datum: 03.11.2026", "Brot 2,50", "Summe", "1.234,50", "Bar 1.250,00", "Rückgeld 15,50"),
                today = "2026-11-21",
            )
        assertEquals(123_450L, receipt.amountMinor)
        assertEquals("Bäckerei Schmidt", receipt.merchant)
        assertEquals("2026-11-03", receipt.date)
    }

    @Test
    fun picksThePastReadingOfAmbiguousDatesAndNamedMonths() {
        assertEquals("2026-11-12", parseReceiptText(listOf("12/11/2026"), "2026-11-21").date)
        assertEquals("2026-11-02", parseReceiptText(listOf("2 Nov 2026"), "2026-11-21").date)
        assertEquals("2026-10-30", parseReceiptText(listOf("October 30, 2026"), "2026-11-21").date)
        assertEquals("2026-01-05", parseReceiptText(listOf("2026-01-05"), "2026-11-21").date)
        assertEquals("2025-02-13", parseReceiptText(listOf("13-02-25"), "2026-11-21").date)
        assertNull(parseReceiptText(listOf("31/02/2026", "13/13/2026", "1 Smarch 2026", "Dec 1, 2026"), "2026-11-21").date)
    }

    @Test
    fun fallsBackToTheLargestAmountAndSkipsNoise() {
        val receipt = parseReceiptText(listOf("  ", "www.shop.com", "#1234", "Coffee 4.50", "Cake 12.00", "Order 55"), "2026-11-21")
        assertEquals(1_200L, receipt.amountMinor)
        assertNull(receipt.merchant)
        assertNull(receipt.date)
        assertTrue(parseReceiptText(emptyList(), "2026-11-21").isEmpty)
        assertEquals(ScannedReceipt(null, "Corner Cafe", null), parseReceiptText(listOf("Corner Cafe"), "2026-11-21"))
    }
}
