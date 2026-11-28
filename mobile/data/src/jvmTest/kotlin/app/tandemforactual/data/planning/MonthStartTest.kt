package app.tandemforactual.data.planning

import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MonthStartTest {
    private fun tx(date: String?) =
        SearchableBudgetTransaction("id", date, null, null, null, -100, null, null, null, null, null, false, false)

    @Test
    fun aPaydayMonthIsNamedAfterTheMonthItStartsIn() {
        val payday = MonthStart(25, previousWorkingDay = false)
        assertFalse(payday.isCalendar)
        assertEquals("2026-10-25", payday.startOf("2026-10"))
        assertEquals("2026-10", payday.monthOf("2026-11-24"))
        assertEquals("2026-11", payday.monthOf("2026-11-25"))
        assertEquals("2026-11", payday.monthOf("2026-12-24"))
        assertEquals("2026-11-01", payday.periodDate("2026-11-25"))
        assertEquals("2026-11-30", payday.periodDate("2026-12-24"))
    }

    @Test
    fun aStartOnTheWeekendMovesBackToFriday() {
        val payday = MonthStart(25, previousWorkingDay = true)
        assertEquals("2026-10-23", payday.startOf("2026-10"))
        assertEquals("2026-04-24", payday.startOf("2026-04"))
        assertEquals("2026-11-25", payday.startOf("2026-11"))
        val first = MonthStart(1, previousWorkingDay = true)
        assertFalse(first.isCalendar)
        assertEquals("2026-11", first.monthOf("2026-10-31"))
        assertEquals("2026-11-02", first.periodDate("2026-10-31"))
        assertEquals("2026-11-30", first.periodDate("2026-11-29"))
    }

    @Test
    fun theCalendarMonthLeavesDatesAlone() {
        val calendar = MonthStart(1, previousWorkingDay = false)
        assertTrue(calendar.isCalendar)
        assertEquals("2026-11-30", calendar.periodDate("2026-11-30"))
        val transactions = listOf(tx("2026-11-30"))
        assertSame(transactions, calendar.inPeriods(transactions))
        assertFailsWith<IllegalArgumentException> { MonthStart(0, previousWorkingDay = false) }
        assertFailsWith<IllegalArgumentException> { MonthStart(29, previousWorkingDay = false) }
    }

    @Test
    fun transactionsMoveIntoTheirPeriods() {
        val moved = MonthStart(25, previousWorkingDay = false).inPeriods(listOf(tx("2026-11-26T09:00"), tx(null), tx("2026-11")))
        assertEquals(listOf("2026-11-02", null, "2026-11"), moved.map { it.recordDate })
    }
}
