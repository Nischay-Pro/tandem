package app.tandemforactual.android

import android.content.Intent
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetWidgetMonthTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun nextRefreshIsLocalMidnightOnTheFirstOfNextMonth() {
        val now = ZonedDateTime.of(2026, 11, 30, 23, 59, 0, 0, zone)

        assertEquals(ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, zone), BudgetWidget.nextMonthStart(now))
    }

    @Test
    fun theFirstOfTheMonthWaitsForTheFollowingMonth() {
        val now = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, zone)

        assertEquals(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone), BudgetWidget.nextMonthStart(now))
    }

    @Test
    fun monthTimeAndTimezoneChangesRefreshTheWidget() {
        listOf("app.tandemforactual.android.WIDGET_MONTH_CHANGED", Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED).forEach {
            assertTrue(it, BudgetWidget.refreshesOnAction(it))
        }
        assertFalse(BudgetWidget.refreshesOnAction(null))
        assertFalse(BudgetWidget.refreshesOnAction(Intent.ACTION_DATE_CHANGED))
        assertFalse(BudgetWidget.refreshesOnAction("android.appwidget.action.APPWIDGET_UPDATE"))
    }
}
