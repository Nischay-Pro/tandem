package app.tandemforactual.ui.feature.rules

import app.tandemforactual.data.store.PayeeDuplicateGroup
import app.tandemforactual.data.store.PayeeUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PayeeGroupEditTest {
    private val group =
        PayeeDuplicateGroup(
            "Tesco",
            "b",
            listOf(PayeeUsage("a", "POS TESCO 12/11", 3), PayeeUsage("b", "POS TESCO #4521", 10), PayeeUsage("c", "Tesco", 1)),
            emptyList(),
            safe = true,
        )

    @Test
    fun anUntouchedGroupMergesAsSuggested() {
        assertEquals(group, group.edited(PayeeGroupEdit()))
    }

    @Test
    fun leavingOutTheKeptPayeeKeepsTheNextMostUsed() {
        val edited = group.edited(PayeeGroupEdit(left = setOf("b")))
        assertEquals(listOf("a", "c"), edited?.members?.map { it.id })
        assertEquals("a", edited?.keepId)
    }

    @Test
    fun addedPayeesAChosenSurvivorAndANewNameAreUsed() {
        val edit = PayeeGroupEdit(added = listOf(PayeeUsage("d", "TESCO EXPRESS", 2)), keepId = "c", name = "  Tesco Express ")
        val edited = group.edited(edit)
        assertEquals(listOf("a", "b", "c", "d"), edited?.members?.map { it.id })
        assertEquals("c", edited?.keepId)
        assertEquals("Tesco Express", edited?.name)
        assertEquals(PayeeCleanupStep.Rename("c", "Tesco Express"), edited?.let(::payeeCleanupSteps)?.last())
    }

    @Test
    fun aBlankNameKeepsTheSuggestionAndOnePayeeLeftIsNothingToMerge() {
        assertEquals("Tesco", group.edited(PayeeGroupEdit(name = " "))?.name)
        assertNull(group.edited(PayeeGroupEdit(left = setOf("a", "b"))))
    }
}
