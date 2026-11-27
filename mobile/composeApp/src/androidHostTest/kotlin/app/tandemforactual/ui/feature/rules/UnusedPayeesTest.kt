package app.tandemforactual.ui.feature.rules

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.state.payeesInRules
import kotlin.test.Test
import kotlin.test.assertEquals

class UnusedPayeesTest {
    private fun rule(
        id: String,
        json: String,
        deleted: Boolean = false,
    ) = BudgetRecordEntity(
        budgetId = "budget-1",
        recordType = "rule",
        recordId = id,
        remoteJson = json,
        remoteRevision = null,
        localJson = null,
        localDeleted = deleted,
        remoteDeleted = false,
        syncGeneration = "1",
        recordDate = null,
        accountId = null,
        categoryId = null,
        payeeId = null,
        amountMinor = null,
        budgetMonth = null,
    )

    @Test
    fun payeesMatchedOrSetByLiveRulesAreKept() {
        val rules =
            listOf(
                rule("r1", """{"conditions":[{"field":"payee","op":"oneOf","value":["p1","p2"]}],"actions":[]}"""),
                rule("r2", """{"conditions":[],"actions":[{"field":"description","op":"set","value":"p3"}]}"""),
                rule("r3", """{"conditions":[{"field":"payee","op":"is","value":"p4"}],"actions":[]}""", deleted = true),
                rule("r4", """{"tombstone":true,"conditions":[{"field":"payee","op":"is","value":"p5"}]}"""),
                rule("r5", """{"conditions":[{"field":"notes","op":"is","value":"p6"}],"actions":[]}"""),
            )
        assertEquals(setOf("p1", "p2", "p3"), payeesInRules(rules))
    }

    @Test
    fun onlyPayeesWithNoTransactionsAndNoRulesAreUnused() {
        val payees = listOf("a" to "Bakery", "b" to "Old gym", "c" to "Landlord", "d" to "Florist")
        val unused = unusedPayees(payees, mapOf("a" to 4L, "d" to 0L), inRules = setOf("c"))
        assertEquals(listOf("b" to "Old gym", "d" to "Florist"), unused)
    }
}
