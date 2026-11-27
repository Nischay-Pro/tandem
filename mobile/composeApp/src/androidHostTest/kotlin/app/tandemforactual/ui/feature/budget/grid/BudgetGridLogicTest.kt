package app.tandemforactual.ui.feature.budget.grid

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetGridLogicTest {
    private val parse = { text: String -> text.toBigDecimalOrNull()?.movePointRight(2)?.toLong() }
    private val size = GridSize(rows = 3, columns = 3)
    private val budgets = mapOf(GridCell(0, 0) to 100L, GridCell(0, 1) to 200L, GridCell(1, 0) to 300L, GridCell(1, 1) to 0L)
    private val budgeted = { cell: GridCell -> budgets[cell] ?: if (cell.column >= 0) 0L else null }

    @Test
    fun amountsAddUpLikeACalculatorInTheBudgetsCurrency() {
        assertEquals(12_000L, evaluateAmount("120", parse))
        assertEquals(15_500L, evaluateAmount("120+35", parse))
        assertEquals(12_000L, evaluateAmount("3*40", parse))
        assertEquals(4_000L, evaluateAmount("(100-20)/2", parse))
        assertEquals(1_000L, evaluateAmount("=10", parse))
        assertEquals(500L, evaluateAmount("−5+10", parse))
        assertEquals(150L, evaluateAmount("2×3÷4", parse))
        assertEquals(1_250L, evaluateAmount("12.5", parse))
    }

    @Test
    fun amountsThatDoNotAddUpAreRefused() {
        assertNull(evaluateAmount("", parse))
        assertNull(evaluateAmount("12+", parse))
        assertNull(evaluateAmount("1/0", parse))
        assertNull(evaluateAmount("(1+2", parse))
        assertNull(evaluateAmount("ten+2", parse))
    }

    @Test
    fun movingStaysInsideTheGridAndTabWrapsToTheNextRow() {
        assertEquals(GridCell(2, 2), GridCell(1, 1).moved(5, 5, size))
        assertEquals(GridCell(0, 0), GridCell(0, 0).moved(-1, -1, size))
        assertEquals(GridCell(1, 0), GridCell(0, 2).stepped(forward = true, size))
        assertEquals(GridCell(0, 2), GridCell(1, 0).stepped(forward = false, size))
        assertEquals(GridCell(2, 2), GridCell(2, 2).stepped(forward = true, size))
        assertTrue(GridSize(0, 3).empty)
    }

    @Test
    fun aRangeCoversEveryCellBetweenItsCorners() {
        val range = GridRange(GridCell(1, 2), GridCell(0, 1))
        assertEquals(listOf(GridCell(0, 1), GridCell(0, 2), GridCell(1, 1), GridCell(1, 2)), range.cells())
        assertTrue(GridCell(1, 1) in range)
        assertFalse(GridCell(2, 1) in range)
        assertFalse(range.single)
    }

    @Test
    fun fillsCopyAmountsAndSkipChangesThatChangeNothing() {
        val down = GridRange(GridCell(0, 0), GridCell(1, 1)).fillDown(budgeted)
        assertEquals(listOf(GridChange(GridCell(1, 0), 300, 100), GridChange(GridCell(1, 1), 0, 200)), down)
        assertEquals(listOf(GridChange(GridCell(1, 1), 0, 300)), GridRange(GridCell(1, 1), GridCell(1, 1)).fillRight(budgeted))
        assertEquals(emptyList(), GridRange(GridCell(0, 0), GridCell(0, 0)).fillDown(budgeted))
        assertEquals(listOf(GridChange(GridCell(0, 1), 200, 100)), GridRange(GridCell(0, 1), GridCell(0, 1)).copyPrevious(budgeted))
    }

    @Test
    fun copiedCellsRoundTripAsTabSeparatedText() {
        val block = GridRange(GridCell(0, 0), GridCell(1, 1))
        val text = block.toTsv { cell -> BigDecimal.valueOf(budgeted(cell) ?: 0, 2).toPlainString() }
        assertEquals("1.00\t2.00\n3.00\t0.00", text)

        val pasted = pasteTsv("5\t6\n7\t8\n9\t10", GridRange(GridCell(1, 1), GridCell(1, 1)), size, parse, budgeted)
        assertEquals(listOf(GridCell(1, 1), GridCell(1, 2), GridCell(2, 1), GridCell(2, 2)), pasted.map { it.cell })
        assertEquals(listOf(500L, 600L, 700L, 800L), pasted.map { it.after })

        val filled = pasteTsv("4", block, size, parse, budgeted)
        assertEquals(4, filled.size)
        assertEquals(emptyList(), pasteTsv("4\tx", block, size, parse, budgeted))
    }

    @Test
    fun theSpendingBarSaysUnderNearlyGoneOrOver() {
        assertEquals(SpendingStatus.UNDER, spendingStatus(GridAmounts(budgeted = 10_000, spent = 5_000, balance = 5_000)))
        assertEquals(SpendingStatus.APPROACHING, spendingStatus(GridAmounts(budgeted = 10_000, spent = 9_500, balance = 500)))
        assertEquals(SpendingStatus.OVER, spendingStatus(GridAmounts(budgeted = 10_000, spent = 12_000, balance = -2_000)))
        assertEquals(0.5f, spendingFraction(GridAmounts(budgeted = 10_000, spent = 5_000, balance = 5_000)))
        assertEquals(1f, spendingFraction(GridAmounts(budgeted = 0, spent = 100, balance = -100)))
        assertEquals(0f, spendingFraction(GridAmounts(budgeted = 10_000, spent = 0, balance = 10_000)))
    }
}
