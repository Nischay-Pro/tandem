package app.tandemforactual.ui.feature.rules

import app.tandemforactual.data.store.PayeeDuplicateGroup
import app.tandemforactual.data.store.PayeeUsage
import kotlin.test.Test
import kotlin.test.assertEquals

class PayeeCleanupStepsTest {
    private val kept = PayeeUsage("b", "POS TESCO #4521", 10)
    private val others = listOf(PayeeUsage("a", "POS TESCO 12/11", 3), PayeeUsage("c", "Tesco", 1))

    @Test
    fun everyOtherPayeeMergesIntoTheKeptOneWhichThenTakesTheTidyName() {
        val group = PayeeDuplicateGroup("Tesco", "b", listOf(kept) + others, emptyList(), safe = true)
        assertEquals(
            listOf(
                PayeeCleanupStep.Merge("a", "b"),
                PayeeCleanupStep.Merge("c", "b"),
                PayeeCleanupStep.Rename("b", "Tesco"),
            ),
            payeeCleanupSteps(group),
        )
    }

    @Test
    fun aKeptPayeeAlreadyNamedRightIsNotRenamed() {
        val tidy = PayeeUsage("c", "Tesco", 12)
        val group = PayeeDuplicateGroup("Tesco", "c", listOf(tidy, PayeeUsage("a", "POS TESCO", 2)), emptyList(), safe = true)
        assertEquals(listOf(PayeeCleanupStep.Merge("a", "c")), payeeCleanupSteps(group))
    }

    @Test
    fun theSummarySaysHowManyPayeesAreShown() {
        assertEquals("120 payees", payeeCountSummary(120, 120, searching = false))
        assertEquals("4 of 120 payees", payeeCountSummary(4, 120, searching = true))
    }

    @Test
    fun aGroupMarkedNotTheSameIsRememberedByItsMembersInAnyOrder() {
        val group = PayeeDuplicateGroup("Tesco", "b", listOf(kept) + others, emptyList(), safe = true)
        val key = payeeGroupKey(group)
        assertEquals("a,b,c", key)
        val stored = encodePayeeGroupKeys(setOf(key, "x,y"))
        assertEquals(setOf("a,b,c", "x,y"), decodePayeeGroupKeys(stored))
        assertEquals(emptySet(), decodePayeeGroupKeys(null))
        assertEquals(emptySet(), decodePayeeGroupKeys(""))
    }
}
