package app.tandemforactual.ui.feature.budget

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

    @Test
    fun replacedCountOnlyIncludesCategoriesWhoseExistingAmountWouldChange() {
        val template = listOf("rent" to 125_000L, "groceries" to 40_000L, "fun" to 10_000L, "new" to 5_000L)
        val current = listOf("rent" to 100_000L, "groceries" to 40_000L, "fun" to 0L, "other" to 7_000L)

        assertEquals(1, budgetTemplateReplacedCount(template, current))
        assertEquals(0, budgetTemplateReplacedCount(template, emptyList()))
    }
}
