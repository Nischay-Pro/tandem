package app.tandemforactual.ui.app

import app.tandemforactual.ui.core.platform.PickedStatementFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickActionEventsTest {
    @AfterTest
    fun reset() {
        QuickActionEvents.consumeAddTransaction()
        QuickActionEvents.consumeReceiptScan()
        QuickActionEvents.consumeDestination()
        QuickActionEvents.consumeSharedText()
        QuickActionEvents.consumeQuickAddDraft()
        QuickActionEvents.consumeSharedReceiptPhoto()
        QuickActionEvents.consumeSharedStatement()
        QuickActionEvents.consumeRequestedAccount()
    }

    @Test
    fun aSharedPhotoOpensTheFormAndReadsThatReceipt() {
        QuickActionEvents.shareReceiptPhoto("content://photos/receipt.jpg")
        assertTrue(QuickActionEvents.consumeAddTransaction())
        assertFalse(QuickActionEvents.consumeReceiptScan())
        assertEquals("content://photos/receipt.jpg", QuickActionEvents.consumeSharedReceiptPhoto())
        assertNull(QuickActionEvents.consumeSharedReceiptPhoto())
    }

    @Test
    fun aSharedStatementOpensAccounts() {
        val file = PickedStatementFile("march.ofx", "<OFX></OFX>")
        QuickActionEvents.shareStatement(file)
        assertEquals(QuickAction.ACCOUNTS, QuickActionEvents.consumeDestination())
        assertEquals(file, QuickActionEvents.consumeSharedStatement())
        assertNull(QuickActionEvents.consumeSharedStatement())
    }

    @Test
    fun addLinksCarryAnAmountAndPayeeOnce() {
        assertTrue(QuickActionEvents.openLink("app.tandemforactual://open/add-transaction?amount=12.5&payee=Caf%C3%A9%20%26%20Bar#x"))
        assertTrue(QuickActionEvents.consumeAddTransaction())
        val draft = QuickActionEvents.consumeQuickAddDraft()
        assertEquals(QuickAddDraft("12.5", "Café & Bar"), draft)
        assertNull(QuickActionEvents.consumeQuickAddDraft())
        assertTrue(QuickActionEvents.openLink(QuickAction.ADD_TRANSACTION.link))
        assertNull(QuickActionEvents.consumeQuickAddDraft())
        QuickActionEvents.openLink("app.tandemforactual://open/add-transaction?payee=Cafe")
        QuickActionEvents.shareText("Paid 4.50")
        assertNull(QuickActionEvents.consumeQuickAddDraft())
        QuickActionEvents.openLink("app.tandemforactual://open/add-transaction?payee=Cafe")
        QuickActionEvents.open(QuickAction.SCAN_RECEIPT)
        assertNull(QuickActionEvents.consumeQuickAddDraft())
    }

    @Test
    fun quickAddAmountsUseTheCurrencysDecimals() {
        assertEquals(1_250L, QuickAddDraft("12.5", null).amountMinor(2))
        assertEquals(1_250L, QuickAddDraft(" -12.50 ", null).amountMinor(2))
        assertEquals(1_235L, QuickAddDraft("12.345", null).amountMinor(2))
        assertEquals(1_234L, QuickAddDraft("12.344", null).amountMinor(2))
        assertEquals(13L, QuickAddDraft("12.5", null).amountMinor(0))
        assertEquals(12_000L, QuickAddDraft("12", null).amountMinor(3))
        assertNull(QuickAddDraft("12,50", null).amountMinor(2))
        assertNull(QuickAddDraft("abc", null).amountMinor(2))
        assertNull(QuickAddDraft(null, "Cafe").amountMinor(2))
    }

    @Test
    fun badlyEncodedOrBlankParametersAreDropped() {
        assertNull(QuickAddDraft.fromLink("app.tandemforactual://open/add-transaction?payee=%E"))
        assertNull(QuickAddDraft.fromLink("app.tandemforactual://open/add-transaction?payee=%zz"))
        assertNull(QuickAddDraft.fromLink("app.tandemforactual://open/add-transaction?amount=&payee=%20"))
        assertNull(QuickAddDraft.fromLink("app.tandemforactual://open/add-transaction"))
        assertEquals(100, QuickAddDraft.fromLink("app.tandemforactual://open/add-transaction?payee=${"a".repeat(150)}")?.payee?.length)
        assertEquals("a+b", percentDecode("a+b"))
    }

    @Test
    fun addCanStartInAChosenAccount() {
        QuickActionEvents.requestAddTransaction("checking")
        assertEquals("checking", QuickActionEvents.consumeRequestedAccount())
        assertNull(QuickActionEvents.consumeRequestedAccount())
        assertTrue(QuickActionEvents.openLink("app.tandemforactual://open/add-transaction?from=widget&account=cash#top"))
        assertEquals("cash", QuickActionEvents.consumeRequestedAccount())
        assertTrue(QuickActionEvents.openLink("app.tandemforactual://open/add-transaction?account="))
        assertNull(QuickActionEvents.consumeRequestedAccount())
        QuickActionEvents.requestAddTransaction(" ")
        assertNull(QuickActionEvents.consumeRequestedAccount())
        QuickActionEvents.requestAddTransaction("checking")
        QuickActionEvents.open(QuickAction.SCAN_RECEIPT)
        assertNull(QuickActionEvents.consumeRequestedAccount())
        QuickActionEvents.requestAddTransaction("checking")
        QuickActionEvents.shareText("Paid 4.50")
        assertNull(QuickActionEvents.consumeRequestedAccount())
    }

    @Test
    fun readsFeatureLinks() {
        QuickAction.entries.forEach { assertEquals(it, QuickAction.fromLink(it.link)) }
        assertEquals(QuickAction.SCAN_RECEIPT, QuickAction.fromLink("app.tandemforactual://open/scan-receipt/?from=assistant"))
        assertEquals(QuickAction.BUDGET_CHECKUP, QuickAction.fromLink("APP.TANDEMFORACTUAL://open/Checkup"))
        assertEquals(QuickAction.ACCOUNTS, QuickAction.fromSlug(" Accounts "))
        assertEquals(QuickAction.ADD_TRANSACTION, QuickAction.fromSlug("new_transaction"))
        assertNull(QuickAction.fromLink("app.tandemforactual://signin/complete?code=1"))
        assertNull(QuickAction.fromLink("app.tandemforactual://open/settings"))
        assertNull(QuickAction.fromLink(null))
        assertNull(QuickAction.fromSlug(null))
    }

    @Test
    fun scanReceiptOpensTheFormWithTheScanner() {
        assertTrue(QuickActionEvents.openLink(QuickAction.SCAN_RECEIPT.link))
        assertTrue(QuickActionEvents.addTransactionRequested.value)
        assertTrue(QuickActionEvents.consumeAddTransaction())
        assertTrue(QuickActionEvents.consumeReceiptScan())
        assertFalse(QuickActionEvents.consumeReceiptScan())
    }

    @Test
    fun plainAddClearsAnEarlierScanRequest() {
        QuickActionEvents.open(QuickAction.SCAN_RECEIPT)
        QuickActionEvents.open(QuickAction.ADD_TRANSACTION)
        assertTrue(QuickActionEvents.consumeAddTransaction())
        assertFalse(QuickActionEvents.consumeReceiptScan())
    }

    @Test
    fun screensWaitUntilConsumedOnce() {
        QuickActionEvents.open(QuickAction.BUDGET_CHECKUP)
        assertEquals(QuickAction.BUDGET_CHECKUP, QuickActionEvents.destinationRequested.value)
        assertEquals(QuickAction.BUDGET_CHECKUP, QuickActionEvents.consumeDestination())
        assertNull(QuickActionEvents.consumeDestination())
        assertTrue(QuickActionEvents.openLink("app.tandemforactual://open/review-transactions"))
        assertEquals(QuickAction.REVIEW_TRANSACTIONS, QuickActionEvents.consumeDestination())
        assertFalse(QuickActionEvents.addTransactionRequested.value)
    }

    @Test
    fun ignoresOtherLinks() {
        assertFalse(QuickActionEvents.openLink("app.tandemforactual://signin/complete?code=1"))
        assertNull(QuickActionEvents.destinationRequested.value)
    }
}
