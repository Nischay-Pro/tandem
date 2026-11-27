package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.SplitLine
import app.tandemforactual.ui.core.state.TransactionSplitDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionSplitDraftTest {
    private val parse = { text: String -> text.toBigDecimalOrNull()?.movePointRight(2)?.toLong() }

    @Test
    fun startsWithTheWholeAmountAndBalancesAsLinesChange() {
        var draft = TransactionSplitDraft.start("50.00", "groceries", "Groceries")
        assertEquals(listOf(SplitLine(1, "50.00", "groceries", "Groceries"), SplitLine(2)), draft.lines)
        assertEquals(0, draft.remaining(5_000, parse))
        assertFalse(draft.isReady(5_000, parse), "the second line has no amount or category")

        draft = draft.setAmount(1, "30").setAmount(2, "20").choosingCategoryFor(2)
        assertEquals(2, draft.pickingLineKey)
        draft = draft.pickCategory("home", "Home")
        assertNull(draft.pickingLineKey)
        assertEquals("Home", draft.lines[1].categoryName)
        assertTrue(draft.isReady(5_000, parse))
        assertEquals(listOf(-3_000L to "groceries", -2_000L to "home"), draft.signedLines(parse, income = false))
        assertEquals(listOf(3_000L to "groceries", 2_000L to "home"), draft.signedLines(parse, income = true))
    }

    @Test
    fun aNegativeLineRunsAgainstTheTransaction() {
        val draft =
            TransactionSplitDraft(
                listOf(SplitLine(1, "60", "groceries", "Groceries"), SplitLine(2, "-10", "home", "Home")),
            )
        assertEquals(0, draft.remaining(5_000, parse))
        assertTrue(draft.isReady(5_000, parse))
        assertEquals(listOf(-6_000L to "groceries", 1_000L to "home"), draft.signedLines(parse, income = false))
        assertEquals(listOf(6_000L to "groceries", -1_000L to "home"), draft.signedLines(parse, income = true))
        assertFalse(draft.isReady(7_000, parse))
    }

    @Test
    fun refusesUnevenEmptyOrSingleLineSplits() {
        val draft =
            TransactionSplitDraft
                .start(
                    "30",
                    "groceries",
                    "Groceries",
                ).setAmount(2, "10")
                .choosingCategoryFor(2)
                .pickCategory("home", "Home")
        assertEquals(1_000, draft.remaining(5_000, parse))
        assertFalse(draft.isReady(5_000, parse))
        assertTrue(draft.isReady(4_000, parse))

        val added = draft.addLine("10")
        assertEquals(3, added.lines.last().key)
        assertFalse(added.isReady(5_000, parse), "the new line has no category")
        assertFalse(added.removeLine(3).removeLine(2).isReady(3_000, parse), "one line is not a split")
        assertFalse(draft.setAmount(2, "nope").isReady(4_000, parse))
        assertFalse(draft.setAmount(1, "40").setAmount(2, "0").isReady(4_000, parse))
        assertEquals(listOf(-3_000L to "groceries", 0L to "home"), draft.setAmount(2, "").signedLines(parse, income = false))
        assertEquals(
            1,
            TransactionSplitDraft(emptyList())
                .addLine("")
                .lines
                .single()
                .key,
        )
    }
}
