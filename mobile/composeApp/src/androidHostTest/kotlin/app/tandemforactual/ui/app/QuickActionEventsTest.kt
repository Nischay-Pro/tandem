package app.tandemforactual.ui.app

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
