package app.tandemforactual.ui
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.feature.transactions.addAgainDraft
import app.tandemforactual.ui.feature.transactions.applyAddAgain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddAgainTest {
    private fun viewed() =
        AppState().apply {
            selectedTransactionRecordId = "t1"
            selectedTransactionAmountMinor = -4_250
            selectedTransactionAccountId = "checking"
            selectedTransactionPayeeId = "cafe"
            selectedTransactionPayeeName = "Corner Cafe"
            selectedTransactionCategoryId = "dining"
            selectedTransactionCategoryName = "Dining out"
            selectedTransactionNotes = "Lunch"
        }

    @Test
    fun copiesTheTransactionIntoTheForm() {
        val state = viewed()
        val draft = state.addAgainDraft()!!
        state.transactionPayee = ""
        state.newTransactionPayeeId = ""
        state.applyAddAgain(draft)
        assertEquals("checking", draft.accountId)
        assertEquals("Spending", state.transactionType)
        assertEquals("cafe", state.newTransactionPayeeId)
        assertEquals("Corner Cafe", state.transactionPayee)
        assertEquals("dining", state.newTransactionCategoryId)
        assertEquals("Lunch", state.transactionNotes)
        assertEquals(state.formatAmount(4_250), state.transactionAmount)
    }

    @Test
    fun transfersAndUnsavedTransactionsAreNotOffered() {
        assertNull(viewed().apply { selectedTransactionIsTransfer = true }.addAgainDraft())
        assertNull(viewed().apply { selectedTransactionRecordId = "" }.addAgainDraft())
        assertNull(viewed().apply { selectedTransactionAmountMinor = null }.addAgainDraft())
        assertEquals(
            "Income",
            viewed().apply { selectedTransactionAmountMinor = 100 }.let {
                it.applyAddAgain(it.addAgainDraft()!!)
                it.transactionType
            },
        )
    }
}
