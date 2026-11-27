package app.tandemforactual.ui.feature.budget

import app.tandemforactual.ui.core.format.daysInMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryInsightsTest {
    private val format: (Long) -> String = { "$" + it / 100 }

    @Test
    fun carriedOverMoneyCountsTowardsWhatACategoryCanSpend() {
        val funds = categoryFundsMinor(balanceMinor = 150_00, activityMinor = -250_00)
        assertEquals(400_00L, funds)
        assertEquals(CategoryPaceStatus.ON_TRACK, categoryPace(funds, -250_00, 25, 30)?.status)
        assertEquals(150_00L, categoryPace(funds, -250_00, 25, 30)?.remainingMinor)
        assertEquals(CategoryPaceStatus.OVER, categoryPace(categoryFundsMinor(-10_00, -310_00), -310_00, 25, 30)?.status)
        assertEquals(0.625f, categoryProgress(balanceMinor = 150_00, activityMinor = -250_00))
    }

    @Test
    fun refundsOffsetSpendingInsteadOfAddingToIt() {
        assertEquals(0L, categorySpentMinor(40_00))
        assertEquals(60_00L, categorySpentMinor(-60_00))
        assertEquals(0f, categoryProgress(balanceMinor = 340_00, activityMinor = 40_00))
        assertEquals(1f, categoryProgress(balanceMinor = -20_00, activityMinor = -20_00))
        assertEquals(0f, categoryProgress(balanceMinor = 0, activityMinor = 0))
        assertEquals(1f, categoryProgress(balanceMinor = -50_00, activityMinor = -150_00))
    }

    @Test
    fun paceComparesSpendingWithAnEvenSpread() {
        assertEquals(CategoryPace(CategoryPaceStatus.ON_TRACK, 40_00, 260_00, 15), categoryPace(300_00, -40_00, 15, 30))
        assertEquals(CategoryPaceStatus.ON_TRACK, categoryPace(300_00, -160_00, 15, 30)?.status)
        assertEquals(CategoryPaceStatus.AHEAD, categoryPace(300_00, -170_00, 15, 30)?.status)
        assertEquals(CategoryPaceStatus.OVER, categoryPace(300_00, -301_00, 15, 30)?.status)
        assertEquals(0L, categoryPace(300_00, 25_00, 15, 30)?.spentMinor)
        assertNull(categoryPace(0, -10_00, 15, 30))
        assertNull(categoryPace(300_00, -10_00, 31, 30))
        assertNull(categoryPace(300_00, -10_00, 0, 30))
    }

    @Test
    fun paceDetailSaysWhatIsLeft() {
        val onTrack = CategoryPace(CategoryPaceStatus.ON_TRACK, 40_00, 260_00, 15)
        val lastDay = CategoryPace(CategoryPaceStatus.ON_TRACK, 280_00, 20_00, 0)
        val over = CategoryPace(CategoryPaceStatus.OVER, 305_00, -5_00, 3)
        assertEquals("$260 left · about $16 a day", categoryPaceDetail(onTrack, format))
        assertEquals("$20 left today", categoryPaceDetail(lastDay, format))
        assertEquals("$5 more than assigned", categoryPaceDetail(over, format))
    }

    @Test
    fun spendingIsReadFromTheMonthRecord() {
        val categories = """{"id":"food","spent":-12500},{"id":"rent","activity":-90000},{"id":"pay","spent":300000},{"id":"x"}"""
        val month = """{"categoryGroups":[{"categories":[$categories]}]}"""
        assertEquals(125_00L, categorySpendingInMonth(month, "food"))
        assertEquals(900_00L, categorySpendingInMonth(month, "rent"))
        assertEquals(0L, categorySpendingInMonth(month, "pay"))
        assertNull(categorySpendingInMonth(month, "x"))
        assertNull(categorySpendingInMonth(month, "missing"))
        assertNull(categorySpendingInMonth(null, "food"))
        assertNull(categorySpendingInMonth("oops", "food"))
    }

    @Test
    fun lastMonthComparesOnlyOnceThisMonthIsOver() {
        assertEquals("$100 spent", lastMonthSpendingDetail(100_00, 50_00, thisMonthComplete = false, format))
        assertEquals("$100 spent · $20 more this month", lastMonthSpendingDetail(100_00, 120_00, thisMonthComplete = true, format))
        assertEquals("$100 spent · $30 less this month", lastMonthSpendingDetail(100_00, 70_00, thisMonthComplete = true, format))
        assertEquals("$100 spent · the same this month", lastMonthSpendingDetail(100_00, 100_00, thisMonthComplete = true, format))
    }

    @Test
    fun monthHelpersHandleYearsAndLeapDays() {
        assertEquals("2026-10", previousMonthId("2026-11"))
        assertEquals("2025-12", previousMonthId("2026-01"))
        assertNull(previousMonthId("2026-13"))
        assertNull(previousMonthId("nope"))
        assertEquals(31, daysInMonth("2026-01"))
        assertEquals(30, daysInMonth("2026-11"))
        assertEquals(29, daysInMonth("2024-02"))
        assertEquals(28, daysInMonth("2100-02"))
        assertEquals(29, daysInMonth("2000-02"))
        assertNull(daysInMonth("2026-00"))
        assertNull(daysInMonth("x-02"))
    }
}
