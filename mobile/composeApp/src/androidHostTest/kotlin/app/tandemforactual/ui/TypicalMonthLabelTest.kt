package app.tandemforactual.ui

import app.tandemforactual.data.planning.MonthMoney
import app.tandemforactual.data.planning.TypicalMonth
import app.tandemforactual.data.planning.TypicalMonthSource
import app.tandemforactual.ui.feature.planning.typicalMonthLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class TypicalMonthLabelTest {
    private val money = MonthMoney(100, 50)

    @Test
    fun namesWhereTheFiguresComeFrom() {
        assertEquals("From October 2026", typicalMonthLabel(TypicalMonth(money, TypicalMonthSource.CHOSEN_MONTH, 0), "October 2026"))
        assertEquals("From last month", typicalMonthLabel(TypicalMonth(money, TypicalMonthSource.RECENT_FULL_MONTHS, 1), "November 2026"))
        assertEquals(
            "Average of the last 3 full months",
            typicalMonthLabel(TypicalMonth(money, TypicalMonthSource.RECENT_FULL_MONTHS, 3), "November 2026"),
        )
        assertEquals("November 2026 so far", typicalMonthLabel(TypicalMonth(money, TypicalMonthSource.MONTH_SO_FAR, 0), "November 2026"))
    }
}
