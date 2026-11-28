package app.tandemforactual.android

import app.tandemforactual.data.store.DueSchedule
import app.tandemforactual.data.store.UpcomingBillsWidgetSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpcomingBillsWidgetTest {
    private val rent = DueSchedule("rent@2026-11-28", "rent", "Rent", "2026-11-28", -150_000)

    @Test
    fun eachBillShowsWhenItsDueThenWhatItIs() {
        assertEquals("28 Nov · Rent", billLabel(rent))
        assertEquals("soon · Rent", billLabel(rent.copy(date = "soon")))
    }

    @Test
    fun theWidgetSaysWhyItListsNoBills() {
        val open = UpcomingBillsWidgetSummary("Home", listOf(rent), amountsHidden = false, displaySettings = null)
        assertNull(billsMessage(open))
        assertEquals("Nothing due in the next two weeks.", billsMessage(open.copy(bills = emptyList())))
        assertEquals("Open Tandem to see your bills.", billsMessage(open.copy(amountsHidden = true)))
        assertEquals(LOCKED_WIDGET_TEXT, billsMessage(open.copy(amountsHidden = true, locked = true)))
        assertEquals("Open Tandem to choose a budget.", billsMessage(null))
    }
}
