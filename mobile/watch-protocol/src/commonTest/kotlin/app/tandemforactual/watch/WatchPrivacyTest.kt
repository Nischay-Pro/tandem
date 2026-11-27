package app.tandemforactual.watch

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchPrivacyTest {
    private fun summary(state: WatchState) =
        WatchSummary(
            version = WatchSummary.VERSION,
            state = state,
            budgetName = "Home",
            sentAtEpochMs = 0,
            leftToSpendMinor = 123_456,
            money = WatchMoneyFormat(symbol = "€", symbolAfter = true, space = true, group = ".", decimal = ","),
        )

    @Test
    fun visibleAmountsUseTheBudgetsFormat() {
        assertEquals("1.234,56 €", summary(WatchState.VISIBLE).money(123_456))
        assertEquals("−0,05 €", summary(WatchState.VISIBLE).money(-5))
        assertEquals("$1,000", WatchMoneyFormat(fractionDigits = 0).format(99_960))
        assertEquals("$0.00", WatchMoneyFormat().format(0))
    }

    @Test
    fun hiddenAndLockedBudgetsNeverShowAmounts() {
        assertEquals(HIDDEN_AMOUNT, summary(WatchState.AMOUNTS_HIDDEN).money(123_456))
        assertEquals(HIDDEN_AMOUNT, summary(WatchState.LOCKED).money(123_456))
        assertEquals(HIDDEN_AMOUNT, summary(WatchState.VISIBLE).money(null))
    }

    @Test
    fun progressIsFullOnceACategoryIsOver() {
        assertEquals(0.5f, WatchCategory("a", "A", budgetedMinor = 100, spentMinor = 50, leftMinor = 50).progress)
        assertEquals(1f, WatchCategory("a", "A", budgetedMinor = 100, spentMinor = 150, leftMinor = -50).progress)
        assertEquals(0f, WatchCategory("a", "A").progress)
    }
}
