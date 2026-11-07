package app.tandemforactual.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTemplatesTest {
    @Test
    fun templatesRoundTripBudgetScopeAndIntegerAssignments() {
        val templates =
            listOf(
                BudgetTemplate(
                    budgetId = "cad-budget",
                    name = "Canada",
                    assignments = listOf("rent" to 125_000L, "groceries" to 0L),
                ),
                BudgetTemplate(
                    budgetId = "usd-budget",
                    name = "United States",
                    assignments = listOf("mortgage" to 240_000L),
                ),
            )

        assertEquals(templates, decodeBudgetTemplates(encodeBudgetTemplates(templates)))
    }

    @Test
    fun malformedAndNegativeAssignmentsAreDropped() {
        val decoded =
            decodeBudgetTemplates(
                """[{"budgetId":"budget","name":"Bad","assignments":[{"categoryId":"rent","amountMinor":-1}]}]""",
            )

        assertTrue(decoded.isEmpty())
        assertTrue(decodeBudgetTemplates("not json").isEmpty())
    }
}
