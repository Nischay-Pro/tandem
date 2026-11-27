package app.tandemforactual.ui

import app.tandemforactual.ui.core.session.transactions.CategoryEntry
import app.tandemforactual.ui.core.session.transactions.balanceAfterSave
import app.tandemforactual.ui.core.session.transactions.categoryChange
import app.tandemforactual.ui.core.session.transactions.categoryMonthBalance
import app.tandemforactual.ui.core.session.transactions.categorySaveFeedback
import app.tandemforactual.ui.core.session.transactions.mainCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionSaveFeedbackTest {
    private fun month(balance: Long) =
        """{"month":"2026-11","categoryGroups":[{"id":"g","categories":[{"id":"food","balance":$balance},{"id":"fuel","balance":0}]}]}"""

    private fun format(minor: Long) = "₹${minor / 100}"

    @Test
    fun readsTheCategoryBalanceFromTheMonth() {
        assertEquals(124_000L, categoryMonthBalance(month(124_000), "food"))
        assertNull(categoryMonthBalance(month(1), "rent"))
        assertNull(categoryMonthBalance("not json", "food"))
        assertNull(categoryMonthBalance(null, "food"))
    }

    @Test
    fun usesTheSyncedMonthOnceItChanged() {
        assertEquals(90_000L to false, balanceAfterSave("food", month(100_000), month(90_000), -10_000))
    }

    @Test
    fun addsTheChangeWhileItWaitsToSync() {
        assertEquals(90_000L to true, balanceAfterSave("food", month(100_000), month(100_000), -10_000))
        assertEquals(90_000L to true, balanceAfterSave("food", month(100_000), null, -10_000))
        assertEquals(100_000L to false, balanceAfterSave("food", month(100_000), month(100_000), 0))
        assertNull(balanceAfterSave("rent", month(100_000), month(100_000), -10_000))
    }

    @Test
    fun changeIsTheNewAmountLessTheOldInThatMonth() {
        val before = listOf(CategoryEntry("food", "2026-11", -5_000))
        val after = listOf(CategoryEntry("food", "2026-11", -8_000), CategoryEntry("food", "2026-12", -1_000))
        assertEquals(-3_000L, categoryChange("food", "2026-11", before, after))
        assertEquals(5_000L, categoryChange("food", "2026-11", before, emptyList()))
        assertEquals(0L, categoryChange("fuel", "2026-11", before, after))
    }

    @Test
    fun aSplitReportsItsLargestCategoryAndCountsTheRest() {
        val split =
            listOf(
                CategoryEntry("food", "2026-11", -2_000),
                CategoryEntry("fuel", "2026-11", -6_000),
                CategoryEntry("food", "2026-11", -1_000),
                CategoryEntry("home", "2026-11", -500),
                CategoryEntry("", "2026-11", -100),
            )
        val (main, others) = mainCategory(split)!!
        assertEquals("fuel", main.categoryId)
        assertEquals(2, others)
        assertNull(mainCategory(emptyList()))
        assertNull(mainCategory(listOf(CategoryEntry("", "2026-11", -1))))
    }

    @Test
    fun saysWhatIsLeftOrHowFarOverInWords() {
        val left = categorySaveFeedback("Groceries", 0, 124_000, false, false, "this month", ::format)
        assertEquals("Saved", left.title)
        assertEquals("Groceries · ₹1240 left this month", left.text)
        assertEquals(false, left.over)
        assertNull(left.detail)
        val over = categorySaveFeedback("Groceries", 2, -30_000, true, false, "in October 2026", ::format)
        assertEquals("Saved across 3 categories", over.title)
        assertEquals("Groceries · over by ₹300 in October 2026", over.text)
        assertTrue(over.over)
        assertEquals("Includes changes waiting to sync", over.detail)
    }

    @Test
    fun hideAmountsLeavesTheAmountOut() {
        assertEquals(
            "Groceries is within budget this month",
            categorySaveFeedback("Groceries", 0, 124_000, false, true, "this month", ::format).text,
        )
        assertEquals(
            "Groceries is over budget this month",
            categorySaveFeedback("Groceries", 0, -1, false, true, "this month", ::format).text,
        )
    }
}
