package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals

class CashFlowTest {
    @Test
    fun expandsSchedulesAndFindsTheLowestPoint() {
        val monthly =
            """{"start":"2026-11-01","frequency":"monthly","interval":1,"patterns":[],""" +
                """"skipWeekend":false,"weekendSolveMode":"after","endMode":"never"}"""
        assertEquals(listOf("2026-12-01", "2027-01-01", "2027-02-01"), CashFlow.scheduleDates(monthly, "2026-11-21", "2027-02-19"))
        assertEquals(listOf("2026-12-05"), CashFlow.scheduleDates("\"2026-12-05\"", "2026-11-21", "2027-02-19"))
        assertEquals(emptyList(), CashFlow.scheduleDates("not json", "2026-11-21", "2027-02-19"))
        val projection = CashFlow.project(100_000, "2026-11-21", 30, listOf("2026-11-25" to -150_000L, "2026-12-01" to 300_000L))
        assertEquals(31, projection.daily.size)
        assertEquals(-50_000, projection.lowestMinor)
        assertEquals("2026-11-25", projection.lowestDate)
        assertEquals(250_000, projection.daily.last())
    }
}
